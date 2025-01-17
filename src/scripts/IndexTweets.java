package scripts;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.TimeZone;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.DoubleDocValuesField;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.spatial.geopoint.document.GeoPointField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import impl.MyLuceneAnalyser;
import utils.DBManager;

public class IndexTweets {
	
	private static int Fetchsize = 10000;
	private static String countryTable = "countries_all";
	
//	private static String indexPath = "/Users/michaelhundt/Documents/Meine/Studium/MASTER/MasterProject/data/LUCENE_Index/lucene_index_nodex2/";
	private static String indexPath = "/Users/michaelhundt/Documents/Meine/Studium/MASTER/MasterProject/data/LUCENE_Index/lucene_index_1_nogeo/";

	private static int topX = 1;
	private static boolean onlyTopX = true;
	
	private static boolean LOCAL = false;
	private static long mindate = Long.MAX_VALUE;
	
	private static long maxdate = Long.MIN_VALUE;
	
	// index all tweets from DB
	public static void main(String[] args) {
		
		Connection c = DBManager.getConnection(LOCAL, false);
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSSSS");

		boolean create = true;	// create new Index
		Date start = new Date();
		System.out.println("Indexing to directory '" + indexPath + "'...");

		try {
			Directory dir = FSDirectory.open(Paths.get(indexPath));
			
			FileReader reader = new FileReader(new File("./stopwords/stopwords.txt"));
			Analyzer analyzer = new MyLuceneAnalyser(reader);
//			Analyzer my_analyzer = new MyLuceneAnalyser(reader);		// without lowercase
			
			IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
//			IndexWriterConfig my_iwc = new IndexWriterConfig(my_analyzer);
			
			if (create) {
				// Create a new index in the directory, removing any
				// previously indexed documents:
				iwc.setOpenMode(OpenMode.CREATE);
			} else {
				// Add new documents to an existing index:
				iwc.setOpenMode(OpenMode.CREATE_OR_APPEND);
			}
			
			IndexWriter writer = new IndexWriter(dir, iwc);
			
			c.setAutoCommit(false);
			Statement stmt = c.createStatement();
			stmt.setFetchSize(Fetchsize);
			
			String query = " Select "
					+ "t.tweet_id, "
					+ "t.tweet_creationdate, "
					+ "t.tweet_content, "
					+ "t.relationship, "
					+ "t.latitude, "
					+ "t.longitude, "
					+ "t.hasurl, "
					+ "t.user_screenname, "
					+ "t.tweet_source, "			// bb_tweets
					+ "t.user_language, "		// bb_tweets
					+ "t.positive, "
					+ "t.negative, "
					+ "t.category, "
					+ "t.sentiment, "
//					+ "t.urls, "				// my2k
//					+ "t.domains, "			// my2k
					+ "u.gender, "
					+ "u.user_statusescount, "			// Credibility attributes of users  -- add further (also scores from the tweet )
					+ "u.user_followerscount, "
					+ "u.user_friendscount, "
					+ "u.user_listedcount, "
					+ "u.desc_score, "
					+ "u.latitude as u_lat, "
					+ "u.longitude as u_lon "
					+ "from "+DBManager.getTweetdataTable()+" as t, "
					+ DBManager.getUserTable()+" as u "
					+ "where t.user_screenname = u.user_screenname;";
			
			  
			ResultSet rs = stmt.executeQuery(query);
			
			ArrayList<Tweet> tweets = new ArrayList<>(Fetchsize);
			
			int doc_counter = 0;
			int counter = 0;
			int stat = 1;
			
			while (rs.next()) {
				counter++;
				
				Tweet t = new Tweet(rs.getString("tweet_id"));
				t.setTweet_creationdate(rs.getString("tweet_creationdate"));
				t.setTweet_content(rs.getString("tweet_content"));
				t.setRelationship(rs.getString("relationship"));
				t.setLatitude(rs.getDouble("latitude"));
				t.setLongitude(rs.getDouble("longitude"));
				t.setHasurl(rs.getBoolean("hasurl"));
				if (rs.getString("user_screenname").contains(","))
					System.out.println(rs.getString("user_screenname"));
				t.setUserScreenName(rs.getString("user_screenname")); 			
//				t.setUser_id(rs.getLong("user_id"));								// bb_tweets
				t.setTweet_source(rs.getString("tweet_source"));					// bb_tweets
				t.setLanguage(rs.getString("user_language"));					// bb_tweets  // text is english, but the user can select his profile language
				t.setPositive(rs.getInt("positive"));
				t.setNegative(rs.getInt("negative"));
				t.setCategory((rs.getString("category") != null) ? rs.getString("category") : "other");
				t.setSentiment((rs.getString("sentiment") != null) ? rs.getString("sentiment") : "neu");
				String gender = rs.getString("gender");
				if (gender == null)
					gender = "unknown";
				else if (gender.equals("andy"))
					gender = "unknown";
				else if (gender.equals("mostly_male"))
					gender = "male";
				else if (gender.equals("mostly_female"))
					gender = "female";
				t.setGender(gender);
				t.setU_lat(rs.getDouble("u_lat"));
				t.setU_lon(rs.getDouble("u_lon"));
				
				double credibility = rs.getDouble("desc_score");
				t.setCredibility(credibility);
				
				
//				t.setUrls(rs.getString("urls"));									// my2k
//				t.setDomains(rs.getString("domains"));							// my2k
				
				tweets.add(t);
				if (counter == Fetchsize) {
					indexTweets(writer, tweets, doc_counter);
					counter = 0;
					tweets.clear();
					doc_counter++;
					if (doc_counter % 100 == 0) {
						System.out.println(". >>"+ Fetchsize*100*stat+" tweets processed");
						stat++;
						if (topX-- == 0 && onlyTopX)
							break;
					}				
					else {
						System.out.print(".");
					}
				}
			}
			c.close();
			
			writer.close();
			
			// create properties file
			File propfile = new File(indexPath + "/settings.properties");
			FileWriter wr;
			Date date = new Date(mindate * 1000L);
			SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
			format.setTimeZone(TimeZone.getTimeZone("UTC"));
			String mind = format.format(date);
			date = new Date(maxdate * 1000L);
			String maxd = format.format(date);
			
			String mincdate = getMinUserCreation();
			String maxcdate = getMaxUserCreation();

			wr = new FileWriter(propfile);
			wr.write("# DB \ntweetdata=" + DBManager.getTweetdataTable() + "\nusers=" + DBManager.getUserTable()
					+ "\n# TIME \n" + "min=" + mind + "\nmax=" + maxd + "\nusermin=" + mincdate + "\nusermax="
					+ maxcdate + "\n");
			wr.flush();

			Date end = new Date();
			System.out.println();
			System.out.println(end.getTime() - start.getTime() + " total milliseconds");
			System.out.println("END");
			
			
		} catch (IOException e) {
			e.printStackTrace();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		
	}
	
	
	private static String getMaxUserCreation() {
		Connection c = DBManager.getConnection(LOCAL, false);
		String maxCreationDate = "";
		try {
			Statement stmt = c.createStatement();
			String query = "select user_creationdate from "+DBManager.getUserTable() +" order by user_creationdate DESC Limit 1";
			ResultSet rs = stmt.executeQuery(query);
			while (rs.next()) {
				maxCreationDate = rs.getString(1);
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		
		return maxCreationDate;
	}


	private static String getMinUserCreation() {
		Connection c = DBManager.getConnection(LOCAL, false);
		String minCreationDate = "";
		try {
			Statement stmt = c.createStatement();
			String query = "select user_creationdate from "+DBManager.getUserTable() +" order by user_creationdate ASC Limit 1";
			ResultSet rs = stmt.executeQuery(query);
			while (rs.next()) {
				minCreationDate = rs.getString(1);
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		
		return minCreationDate;
	}


	private static void indexTweets(IndexWriter writer, ArrayList<Tweet> tweets, int doc_counter) throws SQLException {

		Document doc = new Document();
		long counterLines = 0;
		int no_geo = 0;
		String content = "";
		String tags = "";
		String mentions = "";
		double longi = 0;
		double lati = 0;
		String category = "";
		
		Connection countryCon = DBManager.getConnection(LOCAL, false);
		Statement stmt = countryCon.createStatement();
		
		for (Tweet t : tweets) {
			doc.clear();
			// type
			doc.add(new StringField("type", "twitter", Field.Store.YES));
			doc.add(new StringField("id", t.getTweet_id(), Field.Store.YES));
			
//			doc.add(new StringField("isRetweet", (t.getTweet_replytostatus() > 0) ? "true" : "false", Field.Store.NO));
			doc.add(new StringField("relationship", t.getRelationship().toLowerCase(), Field.Store.YES));
			
			
//			Gender
			doc.add(new StringField("gender", t.getGender().toLowerCase(), Field.Store.YES));
			
			
			//	TweetSource
			String source = t.getTweet_source().toLowerCase();
			// last char is '/'  --> remove
			if (source.endsWith("/")) {
				source = source.substring(0, source.length()-1);
			}
			int indexOfLastSlash = source.lastIndexOf("/");
			if (indexOfLastSlash != -1) {
				source = source.substring(indexOfLastSlash+1);
			}
			doc.add(new StringField("device", source, Field.Store.YES));
			
			
			// User_Language
			doc.add(new StringField("user_language", t.getLanguage().toLowerCase(), Field.Store.YES));
			
			
			// User_ScreenName
			doc.add(new StringField("name", t.getUserScreenName(), Field.Store.YES));
			
			
			// # tags
			content = t.getTweet_content();
			
			if (content != null) {
			
				tags = getTagsFromTweets(content).toLowerCase();
				TextField tag_field = new TextField("tags", tags, Field.Store.YES);
				doc.add(tag_field);
				
				
//				// urls
//				String urls = t.getUrls();
//				if (urls != null)
//					doc.add(new StringField("urls", urls, Field.Store.YES));
//					//doc.add(new Field("urls", urls, Field.Store.YES, Field.Index.NOT_ANALYZED));
//				
//				
//				// domains
//				String domains = t.getDomains();
//				if (domains != null)
//					doc.add(new StringField("domains", domains, Field.Store.YES));
				
				
				// @ mentions
				mentions = getMentionsFromTweets(content);
				StringField hasMention = new StringField("has@", (mentions.isEmpty()) ? "false" : "true", Field.Store.YES);
				doc.add(hasMention);
				TextField mention_field = new TextField("mention", mentions, Field.Store.YES);
				doc.add(mention_field);
//				StringField mention_string = new StringField("mentionStg", mentions, Field.Store.YES);
//				doc.add(mention_string);

//				doc.add(mention_field);
//				doc.add(new StringField("has@", (!mentions.isEmpty()) ? "true" : "false", Field.Store.YES));

				
				// fulltext
				content = content.replaceAll("\"", "");
				TextField content_field = new TextField("content", content.toLowerCase(), Field.Store.NO);
				doc.add(content_field);
				
				category = t.getCategory();
				category = category.replace(" & ", "_").toLowerCase();
				doc.add(new StringField("category", category, Field.Store.YES));
			
				doc.add(new StringField("hasURL", (t.isHasurl())? "true" : "false" , Field.Store.YES));
				
				// User_id
				doc.add(new StringField("uid", t.getUser_id()+"", Field.Store.YES));
				
				
				
// *********  Credibility
				doc.add(new StringField("credibility", t.getCredibility()+"", Field.Store.YES));
//				doc.add(new DoubleDocValuesField("credibility", t.getCredibility()));			// doesn't work
				
				
				// Sentiment
				doc.add(new StringField("sentiment", t.getSentiment(), Field.Store.YES));
				doc.add(new StringField("neg", t.getNegative()+"", Field.Store.YES));
				doc.add(new StringField("pos", t.getPositive()+"", Field.Store.YES));

				
				// time
				String[] datetime = t.getTweet_creationdate().split(" ");
				String date_Str = datetime[0];
				String time_Str = datetime[1];
				String[] date_arr = date_Str.trim().split("-");
				String[] time_arr = time_Str.trim().split(":");
				if (date_arr.length == 3 && time_arr.length == 3) {
					// DATE
					int year = Integer.parseInt(date_arr[0]);
					int month = Integer.parseInt(date_arr[1]);
					int day = Integer.parseInt(date_arr[2]);
					// TIME
					int hour = Integer.parseInt(time_arr[0]);
					int min = Integer.parseInt(time_arr[1]);
					int sec = Integer.parseInt(time_arr[2]);
					
					LocalDate date = LocalDate.of(year, month, day);
					LocalTime time = LocalTime.of(hour, min, sec);
					LocalDateTime dt = LocalDateTime.of(date, time);
					long utc_time = dt.toEpochSecond(ZoneOffset.UTC);
					doc.add(new StringField("date", ""+utc_time , Field.Store.YES ));
					
					mindate = Math.min(mindate, utc_time);
					maxdate = Math.max(maxdate, utc_time);
					
					String dayString = dt.getDayOfWeek().toString().toLowerCase();
					doc.add(new StringField("day", dayString, Field.Store.YES));
					
					// geo
					lati = t.getLatitude();
					longi = t.getLongitude();
					if (lati == 0 || longi == 0) {
						no_geo++;
//						continue;
					}

					// geo tweet location --> Get Country (ID oder name) of admin0 .. and admin1
					if (lati != 0 || longi != 0) {
						GeoPointField geo = new GeoPointField("geo", lati, longi, GeoPointField.Store.YES);
						doc.add(geo);
						
////						// add Country
//						String country = getCountry(lati, longi, stmt).replaceAll(" ", "_").toLowerCase();
//						if (country.isEmpty())
//							country = "other";
//						doc.add(new StringField("t_country", country, Field.Store.YES));
//						
//						// user GEO
//						double ulat = t.getU_lat();
//						double ulong = t.getU_lon();
//						if (ulat != 0 || ulong != 0 ) {
//							// user origin country
//							String ucountry = getCountry(ulat, ulong, stmt).replaceAll(" ", "_").toLowerCase();
//							if (ucountry.isEmpty())
//								ucountry = "other";
//							doc.add(new StringField("u_country", ucountry, Field.Store.YES));
//						}
					}
					
				} 
//				else {
//					continue;
//				}
//
//			}
//			else {
//				continue;
			}
			
			
			if (writer.getConfig().getOpenMode() == OpenMode.CREATE) {
				// New index, so we just add the document (no old document
				// can be there):
				counterLines++;
				try {
					writer.addDocument(doc);
				} catch (IOException e) {
					e.printStackTrace();
				}
			} else {
				// Existing index (an old copy of this document may have
				// been indexed) so
				// we use updateDocument instead to replace the old one
				// matching the exact
				// path, if present:
				// System.out.println("updating " + file);
				// writer.updateDocument(new Term("path", file.toString()),
				// doc);
			}
		}
		stmt.close();
		countryCon.close();

	}
	
	
	/**
	 * This method retrieves the gender from the user table.
	 * If user_screenname is not present, we return "andy".
	 * @param userScreenName
	 * @param genderC
	 * @return gender of the user, default is "andy".
	 */
	private static String getGender(String userScreenName, Connection genderC) {
		String gender = "unknown";
		try {
			Statement stmt = genderC.createStatement();
			String query = "select gender from "+DBManager.getUserTable() +" where user_screenname = '"+userScreenName+"';";
			ResultSet rs = stmt.executeQuery(query);
			while (rs.next()) {
				gender = rs.getString(1);
				if (gender.equals("andy"))
					gender = "unknown";
			}
		} catch (SQLException e) {
			return gender;
		}
		return gender;
	}


	private static String getCountry(double lati, double longi, Statement stmt) throws SQLException {
		String country = "";
		String query = "Select name_0 from "+countryTable+" " + 
				"where St_Contains(geom, St_setSrid(St_Point("+longi+","+lati+"),4326))";
		ResultSet rs = stmt.executeQuery(query);
		while (rs.next()) {
			country = rs.getString(1);
		}
	
		return country;
	}


	private static String getTagsFromTweets(String text_content) {
		String output = "";
		for (String token : text_content.split(" ")) {
			if (token.startsWith("#")) {
				output += token.substring(1) + " ";
			}
		}
		return output.trim();
	}
	
	
	private static String getMentionsFromTweets(String text_content) {
		String output = "";
		for (String token : text_content.split(" ")) {
			// Make a retweet to a simple mention:
			// RT @peter:  -> @peter 
			if (token.startsWith("@")) {
				token = token.replaceAll("[,:]", "");
				output += token.substring(1) + " ";
			}
		}
//		output = output.replace(":", "");
		return output.trim();
	}

}
