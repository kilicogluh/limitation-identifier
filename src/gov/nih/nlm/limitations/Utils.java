package gov.nih.nlm.limitations;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import gov.nih.nlm.ling.core.Document;
import gov.nih.nlm.ling.core.Section;
import gov.nih.nlm.ling.core.Sentence;
import gov.nih.nlm.ling.core.Span;
import gov.nih.nlm.ling.io.XMLReader;
import gov.nih.nlm.ling.sem.SemanticItem;

public class Utils {
	private static Logger log = Logger.getLogger(Utils.class.getName());	
	
	public static Pattern TEXT_PATTERN = Pattern.compile("\\w");
	public static Pattern LIMITATION_ANY_PATTERN = Pattern.compile(" (limitations|weaknesses)");
	public static Pattern LIMITATION_BEGIN_PATTERN = Pattern.compile(" (limitations|weaknesses)\\p{Punct}$");
	//private static Pattern LIMITATION_ANY_PATTERN = Pattern.compile("(limitations|limitation)",Pattern.CASE_INSENSITIVE);
	//private static Pattern LIMITATION_BEGIN_PATTERN = Pattern.compile("(limitations|limitation)\\p{Punct}$", Pattern.CASE_INSENSITIVE);
	
	public static XMLReader getXMLReader() {
		XMLReader reader = new XMLReader();
		return reader;
	}
	
	public static Map<Class<? extends SemanticItem>,List<String>> getAnnotationTypes() {
		Map<Class<? extends SemanticItem>,List<String>> annTypes = new HashMap<Class<? extends SemanticItem>,List<String>>();
		return annTypes;
	}
	
	public static boolean inLimitationParagraph(Sentence sent, boolean strict) {
		int pb = getParagraphBegin(sent);
		if (pb == -1) return false;
		Sentence piSent = sent.getDocument().getSubsumingSentence(new Span(pb,pb+1));
		if (piSent == null) {
			log.severe("Empty sentence");
			return false;
		}
		int ind = sent.getDocument().getSentences().indexOf(piSent);
		int endInd = sent.getDocument().getSentences().indexOf(sent);
		for (int i = ind; i <= endInd; i++ ) {
			Sentence s = sent.getDocument().getSentences().get(i);
			if (strict) {
				Matcher m = LIMITATION_BEGIN_PATTERN.matcher(s.getText().toLowerCase());
				if (m.find()) return true;
			} else {
				if (limitationIntroductorySentence(s)) return true;
			}
		}
		return false;
	}			
	
	public static int getParagraphBegin(Sentence sent) {
		Span sents = sent.getSpan();
		String t = sent.getDocument().getText();
			int next = t.indexOf("\n",sents.getBegin());
			int prev = t.substring(0,next).lastIndexOf("\n")+ 1 ;
			Matcher m = TEXT_PATTERN.matcher(t.substring(prev));
			if (m.find())
				return prev + m.start();
		return -1;
	}
	
/*	public static boolean limitationIntroductorySentence(Sentence sent) {
		String text = sent.getText().toLowerCase();
		Matcher m = LIMITATION_BEGIN_PATTERN.matcher(text);
		if  (m.find()) return true;
			m = LIMITATION_ANY_PATTERN.matcher(text);
			if  (m.find()) return true;
		return false;
	}*/
	
	public static boolean limitationIntroductorySentence(Sentence sent) {
		String text = sent.getText().toLowerCase();
		Matcher m = LIMITATION_BEGIN_PATTERN.matcher(text);
		if  (m.find()) return true;
		if (sent.getWords().size() <= 10) {
			m = LIMITATION_ANY_PATTERN.matcher(text);
			if  (m.find()) return true;
		}
		return false;
	}
	
	public static int getParagraphEnd(Sentence sent) {
		Span sents = sent.getSpan();
		String t = sent.getDocument().getText();
		int next = t.indexOf("\n",sents.getEnd());
		return next;
	}
	
	public static Section getTopSection(Sentence sent) {
		Document doc = sent.getDocument();
		if (doc.getSections() == null) return null;
		for (Section s: doc.getSections()) {
			Span ss = s.getTextSpan();
			if (Span.subsume(ss, sent.getSpan()))  {
				return s;
			}
			Span tss = s.getTitleSpan();
			if (tss == null) continue;
			if (Span.subsume(tss, sent.getSpan()))  {
				return s;
			}
		}
		return null;
	}
	
	public static boolean isCitationSentence(Sentence sent) {
		Pattern pat1 = Pattern.compile("\\[([0-9,\\-]+)\\]");
		String text = sent.getText();
		Matcher m = pat1.matcher(text);
		if (m.find()) {
			log.fine("Citation match:" + m.group());
			return true;
		}
		Pattern pat2 = Pattern.compile("\\.([0-9,\\-]+)$");
		m = pat2.matcher(text);
		if (m.find()) {
			log.fine("Citation match:" + m.group());
			return true;
		}
		if (text.contains(" et al")) return true;
		return false;
	}
}
