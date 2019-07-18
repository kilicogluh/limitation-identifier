package gov.nih.nlm.limitations;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import gov.nih.nlm.ling.core.Document;
import gov.nih.nlm.ling.core.Section;
import gov.nih.nlm.ling.core.Sentence;
import gov.nih.nlm.ling.core.Span;
import gov.nih.nlm.ling.core.Word;
import gov.nih.nlm.ling.io.XMLReader;
import gov.nih.nlm.ling.sem.SemanticItem;
import gov.nih.nlm.ling.sem.SemanticItemFactory;
import gov.nih.nlm.ling.util.FileUtils;

/**
 * 
 * @author Halil Kilicoglu
 *
 */
public class RuleBasedLimitationSentenceRecognizer {
	private static Logger log = Logger.getLogger(RuleBasedLimitationSentenceRecognizer.class.getName());	

	private static Map<String,String> goldLabels = new HashMap<>();
	private static Map<String,String> predictLabels = new HashMap<>();
	private static Map<String,String> goldSentences = new HashMap<>();
	private static Set<String> goldDocs = new HashSet<>();
	private static XMLReader xmlReader;
	private static Map<Class<? extends SemanticItem>,List<String>> annTypes;

	private static List<String> LIST_EXCL_TERMS = Arrays.asList("first","firstly","second","secondly","third","thirdly","fourth","fifth","lastly","finally");
	private static List<String> LIST_TERMS = Arrays.asList("first","firstly","second","secondly","third","thirdly","fourth","fifth", "lastly", "finally");	
	private static List<String> CONTRASTIVE_TERMS = Arrays.asList("however", "nonetheless","nevertheless");	


	public static boolean contrastWithPrevious(Sentence sent) {
		Word first = sent.getWords().get(0);
		return (first.containsAnyLemma(CONTRASTIVE_TERMS));
	}

	public static boolean inListParagraph(Sentence sent){
		List<Sentence> sents = getParagraphSentences(sent);
		for (Sentence ss : sents) {
			if (ss.equals(sent)) continue;
			if (listSentence(ss,LIST_TERMS)) return true;
		}
		return false;
	}

	public static List<Sentence> getParagraphSentences(Sentence sent) {
		int begin = Utils.getParagraphBegin(sent);
		int end  = Utils.getParagraphEnd(sent);
		if (begin == -1 || end == -1) return new ArrayList<>();
		Document doc = sent.getDocument();
		List<Sentence> sents = doc.getAllSubsumingSentences(new Span(begin,end));
		return sents;
	}

	public static boolean listSentence(Sentence sent, List<String> terms){
		Word first = sent.getWords().get(0);
		return (first.containsAnyLemma(terms));
	}

	public static boolean nonListInListParagraph(Sentence sent) {
		return (inListParagraph(sent) && !listSentence(sent,LIST_EXCL_TERMS) && contrastWithPrevious(sent));
	}

	public static boolean furtherStudies(Sentence sent) {
		List<String> cues = Arrays.asList("further", "research", "studies", "needed", "required", "future");
		int cnt = 0;
		for (Word w: sent.getWords()) {
			if (cues.contains(w.getText())) cnt++;
		}
		return (cnt >= 2);
	}


	private static void labelSentence(Sentence sent) {
		Document doc = sent.getDocument();
		String key = doc.getId() + "_" + sent.getId();
		predictLabels.put(key,label(sent));
	}

	public static String label(Sentence sent) {
		Document doc = sent.getDocument();
		Section topSect = Utils.getTopSection(sent);
		if (topSect == null) {
			return "NEG";
		}
		String topTitle = topSect.getTitle();
		if (topTitle == null) {
			return "NEG";
		}
		topTitle = topTitle.toLowerCase();
		if 	(topTitle.contains("discussion") == false  && topTitle.contains("conclusion") == false && topTitle.contains("limitation") == false && topTitle.contains("weakness") == false) {
			return "NEG";
		}
		Section sect = doc.getSection(sent);
		String status = "NONE";
		if (sect == null) sect = topSect;
		Span lSecSp =sect.getTitleSpan();
		String lSecTitle = "";
		if (lSecSp != null) {
			lSecTitle =sect.getTitle().toLowerCase();
			if (lSecTitle.contains("limitation") || lSecTitle.contains("weakness")) {
				if (lSecTitle.contains("strength")) status = "BOTH";
				else status = "LIMIT_ONLY";
			}
		}
		if (status.equals("LIMIT_ONLY")) { 
			if (Utils.limitationIntroductorySentence(sent) ||  
					nonListInListParagraph(sent) || Utils.isCitationSentence(sent)/* || furtherStudies(sent)*/ || contrastWithPrevious(sent)) return "NEG";
			else return "POS";
		}
		else if (status.equals("NONE"))  {
			if (Utils.inLimitationParagraph(sent,false)) {
				if (Utils.limitationIntroductorySentence(sent)  || 
						nonListInListParagraph(sent) || Utils.isCitationSentence(sent)/*  || furtherStudies(sent)*/ || contrastWithPrevious(sent)) return "NEG";
				else {
					log.fine("In limitation paragraph:" + doc.getId() + "|" + sent.getText());
					return "POS";
				}
			}
			else {
				return "NEG";
			}
		} else if (status.equalsIgnoreCase("BOTH")){
			int ind = sect.getSentences().indexOf(sent);
			Span titleSp = sect.getTitleSpan();
			int firstid = -1;
			int lastid = -1;
			for (Sentence s: sect.getSentences()) {
				if (titleSp != null && Span.overlap(titleSp, s.getSpan())) continue;
				String text = s.getText().toLowerCase();
				int sind = sect.getSentences().indexOf(s);
				if (firstid == -1 && ( text.contains("limitation") || text.contains("weakness"))) { firstid = sind; lastid = -1;}
				else if (firstid >= 0 && lastid == -1 && (text.contains("strength"))) lastid = sind;
				if (firstid >= 0 && lastid >= 0) break;
			}
			if (firstid >=0 && lastid == -1) lastid = sect.getSentences().size()-1;
			if (firstid >=0 && lastid >= 0 ) {
				if ((ind < firstid) || (ind > lastid)) 
					return "NEG";
				else {
					if (Utils.limitationIntroductorySentence(sent) || nonListInListParagraph(sent) || Utils.isCitationSentence(sent)/* || furtherStudies(sent)*/ || contrastWithPrevious(sent)) return "NEG";
					else return "POS";
				}
			} else if (firstid == -1 && lastid == -1) {
				int strengthInd = lSecTitle.indexOf("strength");
				int limitInd = lSecTitle.indexOf("limitation");
				if (limitInd == -1) limitInd = lSecTitle.indexOf("weakness");
				int sentCount = sect.getSentences().size();
				int firstcount = (int)sentCount/2;
				if (strengthInd < limitInd) {
					// test for equality?
					if (ind < firstcount) return "NEG";
					else {
						if (Utils.limitationIntroductorySentence(sent) || nonListInListParagraph(sent) || Utils.isCitationSentence(sent)/* || furtherStudies(sent)*/ || contrastWithPrevious(sent)) return "NEG";
						else return "POS";
					}
				} else {
					// test for equality?
					if (ind < firstcount) {
						if (Utils.limitationIntroductorySentence(sent) || nonListInListParagraph(sent) || Utils.isCitationSentence(sent)/* || furtherStudies(sent)*/ || contrastWithPrevious(sent)) return "NEG";
						else return "POS";
					}
					else return "NEG";
				}
			}
		}
		return "NEG";

	}

	private static void processSingleArticle(String inFile,String outFile) throws Exception {
		Document doc = null;
		doc = xmlReader.load(inFile, true, SemanticItemFactory.class,annTypes, null);			
		for (Sentence sent: doc.getSentences()) {
			String key = doc.getId() + "_" + sent.getId();
			if (goldLabels.containsKey(key) == false) continue;
			String text = sent.getText();
			if (goldSentences.get(key).equals(text)) {
				labelSentence(sent);
			} else {
				log.warning("ERROR: " + key + " " + text);
				continue;
			}
		}
	}

	/**
	 * 
	 * @param in	the input directory
	 * @param out  	the output directory
	 * @throws IOException if there is a problem with file reading/writing
	 */
	private static void processDir(String dir, String out) throws Exception {
		List<String> files = FileUtils.listFiles(dir, false, "xml");
		int fileNum = 0;
		for (String filename: files) {
			String filenameNoExt = filename.replace(".xml", "");
			filenameNoExt = filenameNoExt.substring(filenameNoExt.lastIndexOf(File.separator)+1);
			if (goldDocs.contains(filenameNoExt) == false) continue;
			log.info("Processing " + filenameNoExt + ":" + ++fileNum);
			processSingleArticle(filename,out);
		}	
		calculatePerformance(out);
	}

	private static void calculatePerformance(String outfile) throws Exception {
		PrintWriter pw = new PrintWriter(outfile);
		int TP=0;
		int FP=0;
		int FN=0;
		int TN=0;
		List<String> sentids = new ArrayList<>(predictLabels.keySet());
		Collections.sort(sentids);
		for (String k: sentids) {
			String gold = goldLabels.get(k);
			String predict =predictLabels.get(k);
			if (gold.equals("POS") && predict.equals("POS")) {
				TP++;
				log.fine("TP|" + k + "|" + predict + "|" + gold + "|" +  goldSentences.get(k));
				pw.write("TP|" + k + "|" + predict + "|" + gold + "|" +  goldSentences.get(k));
				pw.write("\n");
			}
			else if (gold.equals("NEG") && predict.equals("POS")) {
				FP++;
				log.fine("FP|" + k + "|" + predict + "|" + gold + "|" +  goldSentences.get(k));
				pw.write("FP|" + k + "|" + predict + "|" + gold + "|" +  goldSentences.get(k));
				pw.write("\n");
			}
			else if (gold.equals("POS") && predict.equals("NEG")) {
				FN++;
				log.fine("FN|" + k + "|" + predict + "|" + gold + "|" +  goldSentences.get(k));
				pw.write("FN|" + k + "|" + predict + "|" + gold + "|" +  goldSentences.get(k));
				pw.write("\n");
			}
			else if (gold.equals("NEG") && predict.equals("NEG")) {
				TN++;
				log.fine("TN|" + k + "|" + predict + "|" + gold + "|" +  goldSentences.get(k));
				pw.write("TN|" + k + "|" + predict + "|" + gold + "|" +  goldSentences.get(k));
				pw.write("\n");
			}
		}
		double precision = (double) TP / (double)(FP+TP);
		double recall = (double) TP / (double)(FN+TP);
		double fscore = (double)(2*precision*recall)/(double)(precision+recall);
		double accuracy = (double) (TP+TN)/ (double)(FN+FP+TP+TN);
		double specificity = (double) TN/ (double)(FP+TN);
		log.info("TP|FP|FN|TN:" + TP +"|" + FP + "|" + FN + "|" +TN);
		pw.write("TP|FP|FN|TN:" + TP +"|" + FP + "|" + FN + "|" +TN); pw.write("\n");
		log.info("PRECISION " + precision);
		pw.write("PRECISION " + precision); pw.write("\n");
		log.info("RECALL    " + recall);
		pw.write("RECALL    " + recall); pw.write("\n");
		log.info("F-SCORE   " + fscore);
		pw.write("F-SCORE   " + fscore); pw.write("\n");
		log.info("ACCURACY  " + accuracy);
		pw.write("ACCURACY  " + accuracy); pw.write("\n");
		log.info("SPECIFICITY " + specificity );
		pw.flush();
		pw.close();
	}

	public static void loadGoldLabels(String filename) throws Exception {
		List<String> lines = FileUtils.linesFromFile(filename, "UTF-8");
		if (goldLabels == null) {
			goldLabels = new HashMap<String,String>();
		}
		for (String line: lines) {
			String[] els = line.split("[\t]");
			String type = els[0];
			if (type.equals("DATASET") || type.equals("SEMI") || type.equals("SEED")) continue;
			//			  goldLabels.put(els[0], els[1]);
			String docid = els[2].replace(".xml", "");
			String sentid = els[3];
			String sent = els[6];
			String label = els[5];
			String key = docid+"_" + sentid;
			goldLabels.put(key,label);
			goldSentences.put(key, sent);
			goldDocs.add(docid);
		}
		for (String key: goldLabels.keySet()) {
			log.fine(key + " " + goldLabels.get(key));
		}
	} 

	public static void main(String[] args) 
			throws Exception {
		if (args.length < 3) {
			System.err.print("Usage: inputDirectory goldFile outFile");
		}

		String in = args[0];
		String goldFile = args[1];
		String out = args[2];
		File inDir = new File(in);
		if (inDir.isDirectory()== false) {
			System.err.println("Parsed XML directory does not exist:" + in);
			System.exit(1);
		}

		loadGoldLabels(goldFile);
		annTypes = Utils.getAnnotationTypes();
		xmlReader = Utils.getXMLReader();
		processDir(in,out);
	}

}
