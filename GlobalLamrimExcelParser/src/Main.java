import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Point;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;

import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.filechooser.FileFilter;

import com.csvreader.CsvReader;
import com.csvreader.CsvWriter;

/**
 * 
 */

/**
 * @author eyesblue
 * Change log: 1.1 輸出前加入邏輯檢查-logicalCheck()，確認音檔起始時間沒有結束小於開始的狀況，當excel中有邏輯錯誤時會造成此錯誤。
 *             1.2 當發生邏輯錯誤時，加入強制輸出選項，令維護者可輸入後手動修改; 加入JFrame顯示ChangeLog與Debug Log。
 *             1.3 修正在OSX中選擇輸出的路徑字串中會重複最後一個目錄名稱問題，JFileChooser.showSaveDialog ==> JFileChooser.showOpenDialog。
 *                 修正在OSX中讀取CSV來源時，"…"字元會變成"�"字元的問題。
 */
public class Main extends JFrame{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	static String version="1.4";
	static ArrayList<String[]> allList=new ArrayList<String[]>();
	static ArrayList<GlRecord> recordList=new ArrayList<GlRecord>();
	static Hashtable<String,GlRecord> glSchedule=new Hashtable<String,GlRecord>();
	static JFileChooser chooser = null;
	static int year=0;
	static int reservYear = 2;
	static String globalLamrimScheFileName="GlobalLamrimSchedule.CSV";
	static String globalLamrimScheOldFileName="GlobalLamrimScheduleOld.CSV";
	
	static String changeLog="修改紀錄:\n"
			+ "1.1 輸出前加入邏輯檢查-logicalCheck()，確認音檔起始時間沒有結束小於開始的狀況，當excel中有邏輯錯誤時會造成此錯誤。\n"
			+ "1.2 當發生邏輯錯誤時，加入強制輸出選項，令維護者可輸入後手動修改。\n"
			+ "1.3 修正在OSX中選擇輸出的路徑字串中會重複最後一個目錄名稱問題，JFileChooser.showSaveDialog ==> JFileChooser.showOpenDialog。\n"
			+ "     修正在OSX中讀取CSV來源時，\"…\"字元會變成\"�\"字元的問題。\n"
			+ "1.4 全廣進度下載位置改為lamrimreader.gebis.global。";
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		String sysInfo=getSysInfo();
		
		// Setting information to JFrame object
		Main main=new Main();
		main.setTitle("全廣格式轉換器 V"+version);
		initUI(main);
		
		JOptionPane.showMessageDialog(main, sysInfo, "全廣格式轉換器 V"+version, JOptionPane.INFORMATION_MESSAGE);
		System.out.println("Default Charset of system: "+Charset.defaultCharset().displayName());
		JOptionPane.showMessageDialog(main, "步驟1: 請選取來源CSV檔案", "全廣格式轉換器 V"+version, JOptionPane.INFORMATION_MESSAGE);
		JFileChooser chooser = new JFileChooser();
		chooser.setFileFilter(new FileFilter(){
			@Override
			public boolean accept(File file) {
				return file.isDirectory() || file.getName().toLowerCase().endsWith(".csv");
			}
			@Override
			public String getDescription() {
				return null;
			}});
		
		chooser.setCurrentDirectory(new File(System.getProperty("user.dir")));
		System.out.println("Show Open File Dialog ...");
		int s=chooser.showOpenDialog(main);
		if(s==JFileChooser.CANCEL_OPTION){
			System.out.println("User cancel the option.");
			return;
		}
		
		File rfile=chooser.getSelectedFile();
		chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		Object[] inputFormat ={"Big5", "UTF-8"};  
		String inputOptStr = (String) JOptionPane.showInputDialog(main,"請選擇解讀編碼方式(兩種編碼都可嘗試，選對會正確顯示中文，選錯則變成亂碼)", "選擇編碼", JOptionPane.PLAIN_MESSAGE, null, inputFormat, "輸入格式");
		
		boolean isReadSuccess = false;
		isReadSuccess=openFile(rfile, Charset.forName(inputOptStr));
		if(!isReadSuccess){
			JOptionPane.showMessageDialog(main, "無法成功解讀檔案！", "檔案錯誤", JOptionPane.ERROR_MESSAGE);
			return;
		}
		
		String errRes=null;
		if((errRes=logicalCheck())!=null){
			JOptionPane.showMessageDialog(main, errRes, "邏輯性錯誤", JOptionPane.ERROR_MESSAGE);
			return;
		}
		
		Object[] outputFormat ={ "廣論App用輸出檔", "福智資料庫用輸出檔"};  
		String outputOptStr = (String) JOptionPane.showInputDialog(main,"步驟1解讀完成！\n\n步驟2 輸出: 請選擇輸出格式", "輸出檔案", JOptionPane.PLAIN_MESSAGE, null, outputFormat, "輸出格式");
		
		JOptionPane.showMessageDialog(main, "請選擇檔案輸出目錄，將輸出"+globalLamrimScheFileName+"與"+globalLamrimScheOldFileName+"兩個檔案。", "全廣格式轉換器 V"+version, JOptionPane.INFORMATION_MESSAGE);
//		chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		chooser.setDialogTitle("請選擇檔案輸出目錄");
		//chooser.showSaveDialog(main);
		s=chooser.showOpenDialog(main);
		if(s==JFileChooser.CANCEL_OPTION){
			System.out.println("User cancel the option.");
			return;
		}
		File wfile=chooser.getSelectedFile();
		if(!wfile.exists()){
			JOptionPane.showMessageDialog(main, errRes, "您所選擇的: "+wfile.getAbsolutePath()+" 不存在！", JOptionPane.ERROR_MESSAGE);
			return;
		}
		if(!wfile.isDirectory()){
			JOptionPane.showMessageDialog(main, errRes, "您所選擇的路徑: "+wfile.getAbsolutePath()+" 不是資料夾！", JOptionPane.ERROR_MESSAGE);
			return;
		}
		
		if(outputOptStr==outputFormat[0])saveToGLamrimAppFile(wfile);
		else
			try {
				saveToFzFile(wfile,rfile.getName());
			} catch (IOException e) {
				JOptionPane.showMessageDialog(main, "寫入檔案失敗: "+wfile.getAbsolutePath()+File.separator+File.separator+rfile.getName(), "寫檔錯誤", JOptionPane.ERROR_MESSAGE);
			}
	}
	
	private static void initUI(JFrame frame){
		frame.setDefaultCloseOperation (JFrame.EXIT_ON_CLOSE);
		JTextArea changeLogArea = new JTextArea (10,80);
		changeLogArea.setText(changeLog);
		JTextArea logArea = new JTextArea (25, 80);
		
        Container contentPane = frame.getContentPane ();
        contentPane.setLayout (new BorderLayout ());
        contentPane.add (
        		new JScrollPane (
        				changeLogArea, 
                        JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, 
                        JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED),
                    BorderLayout.NORTH);
        contentPane.add (
            new JScrollPane (
                logArea, 
                JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, 
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED),
            BorderLayout.CENTER);

        frame.pack ();
        frame.setSize(640, 480);
        frame.setLocationRelativeTo(null);
        frame.setVisible (true);
        

        JTextAreaOutputStream out = new JTextAreaOutputStream (logArea);
        System.setOut (new PrintStream (out));
        System.out.println("程式除錯訊息：");
	}
	
	private static String logicalCheck() {
		GlRecord lastGlr=null;
		
		System.out.println("Logical check start");
		for(GlRecord glr:recordList){
			
			int lastPos=0, thisPos=0;
			System.out.println("Start: "+glr.speechPositionStart+", End: "+glr.speechPositionEnd);
			
			lastPos=positionStrToWeightInt(glr.speechPositionStart);
			if(lastPos==-1)return "格式錯誤："+glr;
			thisPos=positionStrToWeightInt(glr.speechPositionEnd);
			if(thisPos==-1)return "格式錯誤："+glr;
			
			if(thisPos <= lastPos){
				String msg=glr.dateStart+" ~ "+glr.dateEnd+" 的音檔結束位置("+glr.speechPositionEnd+") 比開始位置("+lastGlr.speechPositionEnd+") 更小";
				int ret=JOptionPane.showConfirmDialog(null, msg+",\n您仍然要輸出後手動修改嗎？", "邏輯性錯誤", JOptionPane.YES_NO_OPTION, JOptionPane.ERROR_MESSAGE);
				if(!(ret==JOptionPane.YES_OPTION)){
					return "因邏輯性錯誤取消輸出";
				}
			}
			
			// First into the scope, lastGlr is null, no need check this start with last end.
			if(lastGlr==null){
				lastGlr=glr;
				continue;
			}
			
			
			System.out.println("Last End: "+lastGlr.speechPositionEnd+", This Start: "+glr.speechPositionStart);
			// Check is the start time of glr bigger then lastGlr.end time.
			lastPos=positionStrToWeightInt(lastGlr.speechPositionEnd);
			if(lastPos==-1)return "格式錯誤："+lastGlr;
			thisPos=positionStrToWeightInt(glr.speechPositionStart);
			if(thisPos==-1)return "格式錯誤："+glr;
			System.out.println("lastPos="+lastPos+", thisPos="+thisPos);
				if(lastPos > thisPos){
					String msg=glr.dateStart+" ~ "+glr.dateEnd+" 的音檔起始位置("+glr.speechPositionStart+") 比前一天 ("+lastGlr.dateStart+" ~ "+lastGlr.dateEnd+") 的音檔結束位置("+lastGlr.speechPositionEnd+") 更小";
					int ret=JOptionPane.showConfirmDialog(null, msg+",\n您仍然要輸出後手動修改嗎？", "邏輯性錯誤", JOptionPane.YES_NO_OPTION, JOptionPane.ERROR_MESSAGE);
					if(!(ret==JOptionPane.YES_OPTION)){
						return "因邏輯性錯誤取消輸出";
					}
				}
			
			lastGlr=glr;
		}
		
		
		return null; // Logic correct, there is no error message return.
	}

	private static String getSysInfo(){
		String res="作業系統資訊\n";
		res+="預設編碼: "+Charset.defaultCharset().displayName();
		return res;
	}
	
	private static void saveToFzFile(File file,String fileName) throws IOException {
		CsvWriter csvw=new CsvWriter(file.getAbsolutePath()+File.separator+fileName,',',Charset.forName("Big5"));
		csvw.setDelimiter(',');
		
		String[] headers={"ID","月份","天","廣論內文(前12個字&最後12個字)","廣論頁數行數","錄音帶卷數時間","手抄頁數行數","手抄文字"};
		for(String s:headers)
			csvw.write(s);
		csvw.endRecord();
		
		for(GlRecord glr:recordList){
			String startSa[]=glr.dateStart.split("/");
			String endSa[]=glr.dateEnd.split("/");
			
			int month=Integer.parseInt(startSa[1]);
			int day=Integer.parseInt(startSa[2]);
			csvw.write("");
			csvw.write(""+month);
			csvw.write(""+day);
			
			int[] theoryStart=getTheoryStrToInt(glr.theoryLineStart);
			int[] theoryEnd=getTheoryStrToInt(glr.theoryLineEnd);
			TheoryData.content[theoryStart[0]]=TheoryData.content[theoryStart[0]].replace("<", "");
			TheoryData.content[theoryStart[0]]=TheoryData.content[theoryStart[0]].replace(">", "");
			TheoryData.content[theoryStart[0]]=TheoryData.content[theoryStart[0]].replace("b", "");
			TheoryData.content[theoryStart[0]]=TheoryData.content[theoryStart[0]].replace("s", "");
			TheoryData.content[theoryStart[0]]=TheoryData.content[theoryStart[0]].replace("/", "");
			String theoryTextStart=TheoryData.content[theoryStart[0]];
			theoryTextStart=theoryTextStart.split("\n")[theoryStart[1]];
			if(theoryTextStart.length()>12)theoryTextStart=theoryTextStart.substring(0, 12);
			String theoryTextEnd=TheoryData.content[theoryEnd[0]];
			theoryTextEnd=theoryTextEnd.split("\n")[theoryEnd[1]];
			if(theoryTextEnd.length()>12)theoryTextEnd=theoryTextEnd.substring(theoryTextEnd.length()-12);
			theoryTextStart=theoryTextStart+" "+theoryTextEnd;
			theoryTextStart=theoryTextStart.replace('.', '。');
			theoryTextStart=theoryTextStart.replace("　", "");
			theoryTextStart=theoryTextStart.replace(',', '，');
			csvw.write(theoryTextStart);
			
			csvw.write(glr.theoryLineStart+" ~ "+glr.theoryLineEnd);
			
			String[] speechStart=glr.speechPositionStart.split(":");
			String[] speechEnd=glr.speechPositionEnd.split(":");
			if(speechStart[0].equals(speechEnd[0])){
				csvw.write(speechStart[0]+" "+speechStart[1]+":"+speechStart[2]+" ~ "+speechEnd[1]+":"+speechEnd[2]);
			}
			else{
				csvw.write(speechStart[0]+" "+speechStart[1]+":"+speechStart[2]+" ~ "+speechEnd[0]+" "+speechEnd[1]+":"+speechEnd[2]);
			}
			
			csvw.write(glr.subtitleLineStart+" ~ "+glr.subtitleLineEnd);
			csvw.write("『"+glr.desc+"』");
			csvw.endRecord();
		}
		
		csvw.flush();
		csvw.close();
	}

	
	public static int[] getTheoryStrToInt(String str){
		String[] split=str.split("-");
		int page=-1, line=-1;
		
		split[0]=split[0].replace("P", "").replace("p", "");
		page=Integer.parseInt(split[0])-1;
		
		split[1]=split[1].toUpperCase();
		if(split[1].startsWith("LL")){
			line=Integer.parseInt(split[1].replace("LL", ""));
			int contentLineCount=TheoryData.content[page].split("\n").length;
			System.out.println("Content lines of page "+page+" is "+contentLineCount);
			line=contentLineCount-line;
		}else{
			line=Integer.parseInt(split[1].replace("L", ""))-1;
		}
		
		int[] res={page,line};
		return res;
	}
	
	private static boolean saveToGLamrimAppFile(File file){
		System.out.println("Save files to: "+file.getAbsolutePath());
		Downloader downloader = new Downloader();
		try {
			if(!downloader.downloadSchedule()){
				JOptionPane.showMessageDialog(null, "無法從廣論App計畫頁中下載全廣進度，請檢查網路連線是否正常！", "下載錯誤", JOptionPane.ERROR_MESSAGE);
				return false;
			}
			if(!downloader.reloadSchedule()){
				JOptionPane.showMessageDialog(null, "載入已下載的全廣進度錯誤，請檢查網路連線是否正常！", "下載錯誤", JOptionPane.ERROR_MESSAGE);
				return false;
			}
		} catch (IOException e1) {
			e1.printStackTrace();
			return false;
		}
		
		for(GlRecord glr:recordList)
			downloader.addGlRecord(glr);
		
		glSchedule=downloader.getGlSchedule();
		recordList = new ArrayList<GlRecord>(glSchedule.values());

		System.out.println("There are "+recordList.size()+" in Global Lamrim record.");
		sortRecordList();
				
		System.out.println("There are "+recordList.size()+" in Global Lamrim record after sort.");
		// Delete the record that too long term.
		String lastTime[]=recordList.get(0).dateStart.split("/");
		Calendar lastCal=Calendar.getInstance();
		lastCal.set(Calendar.YEAR, Integer.parseInt(lastTime[0]) - reservYear);
		lastCal.set(Calendar.MONTH, Integer.parseInt(lastTime[1]));
		lastCal.set(Calendar.DAY_OF_MONTH, Integer.parseInt(lastTime[2]));
		System.out.println("Remove before :"+ lastCal.get(Calendar.YEAR)+"/"+ lastCal.get(Calendar.MONTH)+"/"+ lastCal.get(Calendar.DATE));
		ArrayList<GlRecord> removeList=new ArrayList<GlRecord>();
		for(int i=0;i<recordList.size();i++){
			GlRecord glr=recordList.get(i);
			Calendar cal=Calendar.getInstance();
			String[] startDate=glr.dateStart.split("/");
			cal.set(Calendar.YEAR, Integer.parseInt(startDate[0]));
			cal.set(Calendar.MONTH, Integer.parseInt(startDate[1]));
			cal.set(Calendar.DAY_OF_MONTH, Integer.parseInt(startDate[2]));
			System.out.println("Compare "+ lastCal.get(Calendar.YEAR)+"/"+ lastCal.get(Calendar.MONTH)+"/"+ lastCal.get(Calendar.DATE)+", time="+lastCal.getTimeInMillis()+" and "+ cal.get(Calendar.YEAR)+"/"+ cal.get(Calendar.MONTH)+"/"+ cal.get(Calendar.DATE)+", time="+cal.getTimeInMillis());
			if((long)lastCal.getTimeInMillis() > (long)cal.getTimeInMillis()){
				System.out.println("Remove :"+ glr);
				removeList.add(glr);
			}
		}
		
		for(GlRecord glr: removeList)
			recordList.remove(glr);
		CsvWriter csvw=new CsvWriter(file.getAbsolutePath()+File.separator+globalLamrimScheFileName,',',Charset.forName("UTF-8"));

		//String[] dateRange={recordList.get(recordList.size()-1).dateStart,recordList.get(0).dateStart};
		String[] dateRange={recordList.get(recordList.size()-1).dateStart,recordList.get(0).dateEnd};
		try {
			csvw.writeRecord(dateRange);
			for(GlRecord glr:recordList)
				csvw.writeRecord(glr.toStringArray());
			csvw.flush();
		} catch (IOException e) {
			e.printStackTrace();
			JOptionPane.showMessageDialog(null, "無法成功寫入輸出檔，請檢查寫入權限或檔案是否已被其他程式開啟！", "輸出錯誤", JOptionPane.ERROR_MESSAGE);
		}
		csvw.close();
		
		// Copy temp file to old schedule.
		File tmpFile=downloader.getDownloadTmpFile();
		byte[] buf=new byte[(int) tmpFile.length()];
		try {
			FileInputStream fis=new FileInputStream(tmpFile);
			System.out.println("Output old file to: "+file.getAbsolutePath()+File.separator+globalLamrimScheOldFileName);
			FileOutputStream fos=new FileOutputStream(file.getAbsolutePath()+File.separator+globalLamrimScheOldFileName);
			fis.read(buf);
			fis.close();
			fos.write(buf);
			fos.flush();
			fos.close();
		} catch (FileNotFoundException e) {
			JOptionPane.showMessageDialog(null, "無法成功寫入輸出檔，請檢查寫入權限！", "輸出錯誤", JOptionPane.ERROR_MESSAGE);
			e.printStackTrace();
			return false;
		} catch (IOException e) {
			JOptionPane.showMessageDialog(null, "無法成功寫入輸出檔，請檢查寫入權限！", "輸出錯誤", JOptionPane.ERROR_MESSAGE);
			e.printStackTrace();
			return false;
		}
		tmpFile.delete();
		return true;
	}
	
	private static boolean openFile(File file, Charset charset){
		
		CsvReader csvr=null;
		try {
			csvr=new CsvReader(file.getAbsolutePath(),',', charset);
		} catch (FileNotFoundException e) {
			JOptionPane.showMessageDialog(null, e.getMessage(), "File not found", JOptionPane.ERROR_MESSAGE);
			e.printStackTrace();
		}
		
		try {
			String row[];
			while(csvr.readRecord()){
				int count=csvr.getColumnCount();
				row=new String[count];
				boolean noData=true;
				for(int i=0;i<count;i++){
					row[i]=csvr.get(i).trim().replace(" ", "");
					if(row[i].length()!=0)noData=false;
				}
				if(noData)continue;
				allList.add(row);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		if(!parseData())return false;
		for(String[] sa:allList){
			for(String str:sa)
				System.out.print(str+", ");
			System.out.println();
		}
		
		return true;
	}

	private static boolean parseData() {
		getYear();
		if(year==0){
			return false;
		}
		System.out.println("Year: "+year);
		
		int dataStart=getFirstDataIndex();
		if(dataStart==-1){
			JOptionPane.showMessageDialog(null, "Can't find first record of the document", "format error", JOptionPane.ERROR_MESSAGE);
			return false;
		}
		for(int i=0;i<dataStart;i++){
			allList.remove(0);
		}
		System.out.println("First data set: "+dataStart);

		fillSpeechCol();
		
		int errLine=-1;
		for(String[] sa: allList){
			errLine=checkSpeechFormat(sa);
			if(errLine!=-1){
				JOptionPane.showMessageDialog(null, "欄位錯誤！欄位中可能有夾雜錯誤字元，請檢查欄位"+(errLine+1)+":\n"+sa[0]+","+sa[1]+","+sa[2]+","+sa[3]+","+sa[4]+","+sa[5]+","+sa[6]+","+sa[7]+","+sa[8], "欄位內容錯誤", JOptionPane.ERROR_MESSAGE);
				return false;
			}
		}
		fillDate();
		adjustTime();
		
		for(GlRecord gr:recordList){
			gr.desc.replace("�", "…");//已知在OSX中"…"字元會變成"�"字元，此處修正回來。
			System.out.println(gr);
		}
		
		return true;
	}
	
	private static void sortRecordList(){
		GlRecord[] grArrays=new GlRecord[recordList.size()];
		grArrays=recordList.toArray(grArrays);
		Arrays.sort(grArrays, new Comparator<GlRecord>(){
			@Override
			public int compare(GlRecord arg0, GlRecord arg1) {
				DateFormat df = DateFormat.getDateInstance();
				try {
					long diffTime= (df.parse(arg1.dateStart).getTime()-df.parse(arg0.dateStart).getTime());
					if(diffTime>0)return 1;
					if(diffTime<0)return -1;
					return 0;
				} catch (ParseException e) {e.printStackTrace();}
				return 0;
			}});
		recordList.clear();
		for(GlRecord glr:grArrays)
			recordList.add(glr);
	}
	
	private static void adjustTime() {
		String mediaTag = null;
		int startTime=-1, endTime=-1;
		
		for(int i=0;i<recordList.size()-1;i++){
			System.out.println("Analyze "+recordList.get(i));
			String[] time=recordList.get(i).speechPositionStart.split(":");
			mediaTag=time[0];
			startTime=toSecond(time[1]+":"+time[2]);
			time=recordList.get(i).speechPositionEnd.split(":");
			endTime=toSecond(time[1]+":"+time[2]);
		
			if(endTime<startTime){
				String nextMediaTag=recordList.get(i+1).speechPositionStart.split(":")[0];
				if(mediaTag.equals(nextMediaTag)){
					String sa[]=recordList.get(i).speechPositionStart.split(":");
					recordList.get(i).speechPositionStart=Util.mediaName[Util.getMediaIndex(mediaTag)-1]+":"+sa[1]+":"+sa[2];
					System.out.println("Adjust start time "+recordList.get(i));
				}
				else {
					String sa[]=recordList.get(i).speechPositionEnd.split(":");
					recordList.get(i).speechPositionEnd=nextMediaTag+":"+sa[1]+":"+sa[2];
					System.out.println("Adjust end time "+recordList.get(i));
				}
			}
		}
		
		String lastTime[]=recordList.get(recordList.size()-1).speechPositionStart.split(":");
		String lastMediaTag=lastTime[0];
		int lastStartTime=toSecond(lastTime[1]+":"+lastTime[2]);
		lastTime=recordList.get(recordList.size()-1).speechPositionEnd.split(":");
		int lastEndTime=toSecond(lastTime[1]+":"+lastTime[2]);
		
		if(lastEndTime<lastStartTime){
			if(lastMediaTag.equals(mediaTag)){
				String sa[]=recordList.get(recordList.size()-1).speechPositionEnd.split(":");
				recordList.get(recordList.size()-1).speechPositionEnd=Util.mediaName[Util.getMediaIndex(mediaTag)+1]+":"+sa[1]+":"+sa[2];
			}
			else{
				String sa[]=recordList.get(recordList.size()-1).speechPositionStart.split(":");
				recordList.get(recordList.size()-1).speechPositionStart=mediaTag+":"+sa[1]+":"+sa[2];
			}
		}
	}

	private static void fillDate() {
		/*
		 * Cross Year problem: the document will cross year in winter(month 12,1,2), the date just only month/day, and the year is stay on top of document,
		 * if we don't check cross year, it will be 2013/12/31 ~ 2013/1/31 ~2013/2/28, correct should be 2013/12/31 ~ 2014/1/31 ~ 2014/2/28 
		 * */
		boolean crossYear = false;
		for(int i=0;i<allList.size();i++){
			// Check date format
			String sa[]=allList.get(i);
			String dateStr=sa[1];
			if(dateStr.length()!=0 && !dateStr.matches("\\d{2} */ *\\d{2} *~ *\\d{2} */ *\\d{2}")){
				JOptionPane.showMessageDialog(null, "Date region format error(01/14~01/15), data:["+dateStr+"]", "Date ragion format error", JOptionPane.ERROR_MESSAGE);
			}
			String[] dates=dateStr.split("~");
			
			// Check cross year
			String checkCross[]=dates[0].split("/");
			if(checkCross[0].equals("12"))crossYear=true;

			SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd");
			DateFormat df = DateFormat.getDateInstance();
			try {
				Date date = df.parse(year+"/"+dates[0]);
				date = df.parse(year+"/"+dates[1]);
			} catch (ParseException e) {
				JOptionPane.showMessageDialog(null, "Date parse error(ex. 2014/01/15), data:["+dateStr+"]", "Date parse error", JOptionPane.ERROR_MESSAGE);
				e.printStackTrace();
				continue;
			}
			
			GlRecord gr=new GlRecord();
			boolean nextYear=false;
			int startMonth=Integer.parseInt(dates[0].split("/")[0]);
			int endMonth=Integer.parseInt(dates[1].split("/")[0]);
			if(crossYear && (startMonth==1 || startMonth==2 || endMonth==1 || endMonth==2))
				nextYear=true;
			
			gr.dateStart=((nextYear && (startMonth==1 || startMonth==2))?year+1:year)+"/"+dates[0];
			gr.dateEnd=((nextYear && (endMonth==1 || startMonth==2))?year+1:year)+"/"+dates[1];
			
			String[] times=sa[2].split("~");

			gr.speechPositionStart=sa[0]+":"+times[0];
			gr.speechPositionEnd=sa[0]+":"+times[1];
	
			gr.totalTime=sa[3];
			gr.theoryLineStart=sa[4];
			gr.theoryLineEnd=sa[5];
			gr.subtitleLineStart=sa[6];
			gr.subtitleLineEnd=sa[7];
			gr.desc=sa[8];
			recordList.add(gr);
		}
	}

	private static int toSecond(String timeStr){
		String[] sa=timeStr.split(":");
//		System.out.println("Sa[0]="+sa[0]);
		sa[1].replace("\"", "");
		int min=Integer.parseInt(sa[0]);
		int sec=Integer.parseInt(sa[1]);
		sec+=60*min;
		return sec;
	}

	private static void fillSpeechCol() {
		String speechTag="";
		for(int i=0;i<allList.size();i++){
			String sa[]=allList.get(i);
			if(sa[0]!=""){
				speechTag=sa[0];
				continue;
			}
			if(sa[0]=="")sa[0]=speechTag;
		}
	}

	private static int getFirstDataIndex() {
		for(int i=0;i<allList.size();i++){
			String[] sa=allList.get(i);
			if(sa[0].matches("\\d+A") || sa[0].matches("\\d+B") || sa[0].matches("\\d+a") || sa[0].matches("\\d+b"))
				return i;
		}
		return -1;
	}
	
	private static void getYear() {
		String yearWord="年";
		for(String[] sa:allList)
			for(String s:sa)
				if(s.matches("\\d{4}"+yearWord)){
					year=Integer.parseInt(s.replace(yearWord, ""));
					return;
				}
	}
	
	private static int positionStrToWeightInt(String str){
		int arr[]=positionStrToInt(str);
		if(arr==null)return -1;
		int res=arr[2];
		res+=arr[1]*100;
		res+=arr[0]*10000;
		return res;
	}
	
	private static int[] positionStrToInt(String str){
		String[] strs=str.split(":");
		int res[]=new int[3];
		boolean hasErr=false;
		
		try{
			res[0]=Util.getMediaIndex(strs[0]);
			res[1]=Integer.parseInt(strs[1]);
			res[2]=Integer.parseInt(strs[2]);
		}catch(NumberFormatException nfe){
			hasErr=true;
		}
		
		if(hasErr)return null;
		return res;
	}
	
	private static int checkSpeechFormat(String sa[]){
		if(!(sa[0].matches("\\d+A") || sa[0].matches("\\d+B") || sa[0].matches("\\d+a") || sa[0].matches("\\d+b")))return 0;
		if(!sa[1].matches("\\d{2}/\\d{2}~\\d{2}/\\d{2}"))return 1;
		if(!sa[2].matches("\\d{2}:\\d{2}~\\d{2}:\\d{2}"))return 2;
		if(!sa[3].matches("\\d{2}'\\d{2}\""))return 3;
		if(!sa[4].matches("P\\d+-L+\\d+"))return 4;
		if(!sa[5].matches("P\\d+-L+\\d+"))return 5;
		if(!sa[6].matches("P\\d+-L+\\d+"))return 6;
		if(!sa[7].matches("P\\d+-L+\\d+"))return 7;
		return -1;
	}
}
