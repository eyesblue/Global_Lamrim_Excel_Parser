
public class GlRecord {
	String dateStart;
	String dateEnd;
	String speechPositionStart;
	String speechPositionEnd;
	String totalTime;
	String theoryLineStart;
	String theoryLineEnd;
	String subtitleLineStart;
	String subtitleLineEnd;
	String desc;
	
	public String toString(){
		return dateStart+", "+dateEnd+", "+speechPositionStart+", "+speechPositionEnd+", "+totalTime+", "+theoryLineStart+", "+theoryLineEnd+", "+subtitleLineStart+", "+subtitleLineEnd+", "+desc;
	}
	
	public String[] toStringArray(){
		String[] sa={dateStart,dateEnd,speechPositionStart,speechPositionEnd,totalTime,theoryLineStart,theoryLineEnd,subtitleLineStart,subtitleLineEnd,desc};
		return sa;
	}
}
