package gov.nih.nlm.limitations;

import java.util.List;

public class LimitationSummary {
	private String docId;
	private int sentCount;
	private List<String> sents;
	
	public LimitationSummary(String id, int sentCount, List<String> sentences) {
		this.docId = id;
		this.sentCount = sentCount;
		this.sents = sentences;
	}
	public String getDocId() {
		return docId;
	}
	public void setDocId(String id) {
		this.docId = id;
	}
	public int getSentCount() {
		return sentCount;
	}
	public void setSentCount(int limitCount) {
		this.sentCount = limitCount;
	}
	public List<String> getSents() {
		return sents;
	}
	public void setSents(List<String> limitSentences) {
		this.sents = limitSentences;
	}
	
	

}
