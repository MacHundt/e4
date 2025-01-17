 
package socialocean.parts;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.swing.JApplet;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SingleSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;

import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.eclipse.e4.ui.di.Focus;
import org.eclipse.e4.ui.di.Persist;
import org.eclipse.swt.SWT;
import org.eclipse.swt.awt.SWT_AWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;

import impl.GraphCreatorThread;
import impl.MyLuceneAnalyser;
import impl.TimeLineCreatorThread;
import socialocean.model.Result;
import utils.Lucene;
import utils.Lucene.TimeBin;
import utils.Swing_SWT;
import utils.TermStats;

public class TopSelectionPart {
	
	private static TopSelectionPart INSTANCE;
	public static boolean isInitialized = false;
	
	private Composite composite;
	private JTable results;
	private DefaultTableModel resultDataModel;
	public int detailsColumns = 2;
	private JTable detail;
	
	private Result result = null;
	// content, tags, mentions, type, category, isRetweet //crimetype, //date, //id, sentiment, hasURL, has@ )
//	public String[] detailsToShow = {"category", "content", "has@", "hasURL", 
//			"isRetweet", "mention", "sentiment", "tags", "type"};
//	public String[] detailsToShow = {"category", "content", "has@", "type", "neg", "pos",
//			"isRetweet", "mention", "sentiment", "tags", "user_id", "user_name"};
	
	// The details should be present in the Lucene Index!
	public String[] detailsToShow = {""
			+ "category", 
			"content", 
			"hasURL", 
			"has@", 
//			"type", 					// for now, there are only tweets!
			"neg", 
			"device", 				// bb_tweets
			"pos", 
			"mention", 
			"sentiment", 
//			"source", 
			"tags", 
			"name", 
			"u_country",				// test .. takes long
			"t_country",				// test .. takes long
			"user_language", 
			"gender",
			"credibility",
			"urls",					// nodeXL
			"domains",				// nodeXL
			"day"
			};
	private DefaultTableModel detailsDataModel;
	private int resultColumns = 3;
	
	private String currentSelectedField = "";
	
	
	@Inject
	public TopSelectionPart() {
		
	}
	
	@PostConstruct
	public void postConstruct(Composite parent) {
		
//		Display display = Display.getCurrent();
//		org.eclipse.swt.graphics.Color red = display.getSystemColor(SWT.COLOR_RED);
		
		Swing_SWT util = new Swing_SWT();
		parent.addControlListener(util.CleanResize);
		
		composite = new Composite(parent, SWT.EMBEDDED | SWT.NO_BACKGROUND );
		Frame frame = SWT_AWT.new_Frame(composite);
 		
		JApplet rootContainer = new JApplet();
		
		JPanel panel = new JPanel();
		panel.setLayout(new BorderLayout());
		
		JPanel northSelection = new JPanel();
		panel.add(northSelection, BorderLayout.NORTH);
		northSelection.setLayout(new FlowLayout(FlowLayout.LEFT));
		
		JLabel lblNewLabel = new JLabel("get Top X");
		Font standard = lblNewLabel.getFont();
		Font newFont = new Font(standard.getFontName(), standard.getStyle(), 11);
		
		lblNewLabel.setFont(newFont);
		northSelection.add(lblNewLabel);
		
		JSpinner spinner = new JSpinner(new SpinnerNumberModel(50, 1, 1000, 1));
		spinner.setFont(newFont);
		northSelection.add(spinner);
		
		JButton resultBtn = new JButton(" >>>");
		resultBtn.setFont(newFont);
		northSelection.add(resultBtn);
		
		JButton showBtn = new JButton("Show");
		showBtn.setFont(newFont);
//		showBtn.setBackground(new Color(255,0,0));
		showBtn.setForeground(new Color(255,0,0));
		
		northSelection.add(showBtn);
		
		JSplitPane split = new JSplitPane();
		panel.add(split, BorderLayout.CENTER);
		
//		String[] header = {"Name", "Term count", "%"};
		String[] header = {"Field", "Term count"};
		Object[][] data = new Object[1][detailsColumns];
		detailsDataModel = new DefaultTableModel(data, header);
		TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<DefaultTableModel>(detailsDataModel);
		detail = new JTable(detailsDataModel);
		detail.setRowSorter(sorter);
		detail.setAutoCreateColumnsFromModel(true);
		
		JScrollPane detailsPane = new JScrollPane(detail);
		split.setLeftComponent(detailsPane);
		
//		String[] result_header = {"Rank", "Freq", "Field", "Text"};
		String[] result_header = {"Rank", "Freq", "Text"};
		Object[][] result_data = new Object[9][resultColumns];
		resultDataModel = new DefaultTableModel(result_data, result_header);
		results = new JTable(resultDataModel);
		JScrollPane resultPane = new JScrollPane(results);
		results.setFillsViewportHeight(true);
		results.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		resultPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		resultPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
		split.setRightComponent(resultPane);
		
		
		resultBtn.addActionListener(new ActionListener() {
			
			@Override
			public void actionPerformed(ActionEvent e) {
				Lucene l = Lucene.INSTANCE;
				while (!l.isInitialized) {
					continue;
				}
				int topX = -1;
				try {
					topX = (int) spinner.getValue();
				} catch (Exception ex) {
					return;
				}
				int selected = detail.getSelectedRow();
				int colIndexName = 0;
				for (int i=0; i< detailsDataModel.getColumnCount(); i++) {
					if (detailsDataModel.getColumnName(i).equals("Name"))
							colIndexName = i;
				}
				if (selected == -1)
					return;
				currentSelectedField = (String) detailsDataModel.getValueAt(detail.convertRowIndexToModel(selected), colIndexName);
				if (currentSelectedField != null && !currentSelectedField.isEmpty()) {
					System.out.println("Get top "+ topX +" from field "+currentSelectedField);
					l.printlnToConsole("Get top "+ topX +" from field "+currentSelectedField);
					TermStats[] result = l.searchTopXOfField(currentSelectedField, topX);
					
					Object[][] resulTable = new Object[result.length][resultColumns];
					for (int i= 0; i< result.length; i++) {
						TermStats ts = result[i];
						resulTable[i][0] = i;						// Rank	
						resulTable[i][1] = ts.docFreq;				
//						resulTable[i][2] = ts.field;
						resulTable[i][2] = ts.termtext.utf8ToString();
					}
					
					setResultTable(resulTable);
					
				}
			}
		});
		
		showBtn.addActionListener(new ActionListener() {
			
			@Override
			public void actionPerformed(ActionEvent e) {
				Lucene l = Lucene.INSTANCE;
				while (!l.isInitialized) {
					continue;
				}
				// get Selected Items from result Table
				if (results.getSelectedRow() == -1 )
					return;
				
				String query = currentSelectedField+":"+results.getModel().getValueAt(results.getSelectedRow(), resultColumns-1 );
				System.out.println("Show "+results.getModel().getValueAt(results.getSelectedRow(), resultColumns-1));
				l.printlnToConsole("Show "+results.getModel().getValueAt(results.getSelectedRow(), resultColumns-1));
				
				if (query.isEmpty()) {
					return;
				}
				
				// Query
//				ScoreDoc[] result = null;
//				Query q = null;
				try {
					Query q = null;
					TermQuery tquery = null;
					boolean termQuery = false;
					if (query.contains("name:") || query.contains("mention:")) {
						QueryParser parser = new QueryParser("name", new MyLuceneAnalyser());
						q = parser.parse(query);
					}
					else if (query.contains("urls:")) {
						String url = query.substring(query.indexOf("urls:")+5);
						tquery = new TermQuery(new Term("urls", url));
						termQuery = true;
						result = l.query(tquery, l.getQeryType(), true, true);
					}
					else 
						 q = l.getParser().parse(query);
					
					if (!termQuery)
						result = l.query(q, l.getQeryType(), true, true);
					
					ScoreDoc[] data = result.getData();
					TimeLineCreatorThread lilt = new TimeLineCreatorThread(l) {
						@Override
						public void execute() {
							result.setTimeCounter(l.createTimeBins(TimeBin.HOURS, data));
							l.showInTimeLine(result.getTimeCounter());
						}
					};
					lilt.start();
					
					GraphCreatorThread graphThread = new GraphCreatorThread(l) {
						
						@Override
						public void execute() {
							l.createGraphView(data);
//							l.createSimpleGraphView(data);
						}
					};
					graphThread.start();
					
					// Show in MAP  --> Clear LIST = remove all Markers
//					l.initCountriesMap();
					l.createMapMarkers(data, true);
					l.changeHistogramm(result.getHistoCounter());
					
//					l.createGraphML_Mention(data, true);
//				l.createGraphML_Retweet(result, true);
					
				} catch (ParseException e1) {
					e1.printStackTrace();
				}
			}
		});
		
		split.setDividerLocation(0.5);
		
		rootContainer.add(panel);
 		
		frame.add(rootContainer);
		frame.validate();
		
		INSTANCE = this;
		isInitialized = true;
	}
	
	public static TopSelectionPart getInstance() {
        return INSTANCE;
	}
	
	public void setDetailTable(Object[][] tableData) {
		for (int i= 0; i< tableData.length; i++) {
			if (i >= detailsDataModel.getRowCount()) {
				detailsDataModel.addRow(new Object[detailsColumns]);
			}
			for (int j=0; j < tableData[0].length; j++) {
				detailsDataModel.setValueAt(tableData[i][j], i, j);
			}
		}
		detailsDataModel.fireTableStructureChanged();
		detail.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
	}
	
	
	public void setResultTable(Object[][] resultTableData) {
		while (resultDataModel.getRowCount() > resultTableData.length) {
			resultDataModel.removeRow(resultDataModel.getRowCount()-1);
		}
		
		for (int i= 0; i< resultTableData.length; i++) {
			if (i >= resultDataModel.getRowCount()) {
				resultDataModel.addRow(new Object[resultColumns]);
			}
			for (int j=0; j < resultTableData[0].length; j++) {
				resultDataModel.setValueAt(resultTableData[i][j], i, j);
			}
		}
		resultDataModel.fireTableStructureChanged();
//		results.setEnabled(false);
		results.doLayout();
	}
	
	
	
	@Focus
	public void onFocus() {
		composite.setFocus();
	}
	
	
	@Persist
	public void save() {
		
	}
	
}