package gov.nih.nlm.limitations;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import gov.nih.nlm.ling.core.Document;
import gov.nih.nlm.ling.core.Section;
import gov.nih.nlm.ling.core.Sentence;
import gov.nih.nlm.ling.core.Span;
import gov.nih.nlm.ling.io.XMLReader;
import gov.nih.nlm.ling.sem.SemanticItem;
import gov.nih.nlm.ling.sem.SemanticItemFactory;
import gov.nih.nlm.ling.util.FileUtils;

/**
 * 
 * @author Halil Kilicoglu
 *
 */
public class DocumentLevelLimitations {
	private static Logger log = Logger.getLogger(DocumentLevelLimitations.class.getName());	

	private static List<String> posDocs = new ArrayList<>();
	private static Map<String,String> outLabels = new HashMap<>();
	private static Map<String,String> goldLabels = new HashMap<>();
	private static Map<String,String> goldSentences = new HashMap<>();
	private static Set<String> goldDocs = new HashSet<>();
	private static XMLReader xmlReader;
	private static Map<Class<? extends SemanticItem>,List<String>> annTypes;

	private static Pattern LIMITATION_RELAXED_PATTERN = Pattern.compile("(limitation|weakness|shortcoming|drawback)");


	private static boolean inLimitationParagraph(Sentence sent) {
		int pb = Utils.getParagraphBegin(sent);
		if (pb == -1) return false;
		Sentence piSent = sent.getDocument().getSubsumingSentence(new Span(pb,pb+1));
		if (piSent == null) {
			log.severe("Empy sentence");
			return false;
		}
		int ind = sent.getDocument().getSentences().indexOf(piSent);
		int endInd = sent.getDocument().getSentences().indexOf(sent);
		for (int i = ind; i <= endInd; i++ ) {
			Sentence s = sent.getDocument().getSentences().get(i);
			Matcher m = LIMITATION_RELAXED_PATTERN.matcher(s.getText().toLowerCase());
			if (m.find() && !Utils.isCitationSentence(s)) return true;
		}
		return false;
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
		if 	(topTitle.contains("discussion") == false && topTitle.contains("conclusion") == false && topTitle.contains("limitation") == false && topTitle.contains("weakness") == false) {
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
				return "POS";
			}
		}
		if (status.equals("NONE"))  {
			if (inLimitationParagraph(sent)) {
				log.fine("In limitation paragraph:" + doc.getId() + "|" + sent.getText());
				return "POS";
			}
			else {
				return "NEG";
			}
		} 
		return "NEG";

	}

	private static void processSingleArticle(String inFile,String outFile) throws Exception {
		Document doc = null;
		doc = xmlReader.load(inFile, true, SemanticItemFactory.class,annTypes, null);			
		for (Sentence sent: doc.getSentences()) {
			String label = label(sent);
			if (label.equals("POS")) {
				outLabels.put(doc.getId(), "POS");
				return;
			}
		}
		outLabels.put(doc.getId(), "NEG");
	}

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
		List<String> docids = new ArrayList<>(outLabels.keySet());
		Collections.sort(docids);
		for (String k: docids) {
			String gold = (posDocs.contains(k) ? "POS" : "NEG");
			String predict =outLabels.get(k);
			if (gold.equals("POS") && predict.equals("POS")) {
				TP++;
				log.fine("TP|" + k);
				pw.write("TP|" + k );
				pw.write("\n");
			}
			else if (gold.equals("NEG") && predict.equals("POS")) {
				FP++;
				log.fine("FP|" + k );
				pw.write("FP|" + k );
				pw.write("\n");
			}
			else if (gold.equals("POS") && predict.equals("NEG")) {
				FN++;
				log.fine("FN|" + k );
				pw.write("FN|" + k );
				pw.write("\n");
			}
			else if (gold.equals("NEG") && predict.equals("NEG")) {
				TN++;
				log.fine("TN|" + k );
				pw.write("TN|" + k );
				pw.write("\n");
			}
		}
		double precision = (double) TP / (double)(FP+TP);
		double recall = (double) TP / (double)(FN+TP);
		double fscore = (double)(2*precision*recall)/(double)(precision+recall);
		double accuracy = (double) (TP+TN)/ (double)(FN+FP+TP+TN);
		double specificity = (double) TN/ (double)(FP+TN);
		log.info("TP|FP|FN|TN:" + TP +"|" + FP + "|" + FN + "|" +TN);
		log.info("PRECISION " + precision);
		log.info("RECALL    " + recall);
		log.info("F-SCORE   " + fscore);
		log.info("ACCURACY  " + accuracy);
		log.info("SPECIFICITY " + specificity );
		pw.flush();
		pw.close();
	}

	private  static void loadGoldLabels(String filename) throws Exception {
		List<String> lines = FileUtils.linesFromFile(filename, "UTF-8");
		if (goldLabels == null) {
			goldLabels = new HashMap<String,String>();
		}
		for (String line: lines) {
			String[] els = line.split("[\t]");
			if (els[0].equals("DATASET") || els[0].equals("SEMI") || els[0].equals("SEED")) continue;
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
			log.info(key + " " + goldLabels.get(key));
		}
	} 

	private static void loadPosDocs(String filename) throws Exception {
		List<String> lines = FileUtils.linesFromFile(filename, "UTF-8");
		for (String line: lines) {
			posDocs.add(line.trim());
			log.info("POS:" + line.trim());
		}
		log.info("POS Count: " + posDocs.size());
	} 

	public static void main(String[] args) 
			throws Exception {
		if (args.length < 3) {
			System.err.print("Usage: inputDirectory goldFile outFile");
		}

		String in = args[0];
		String goldFile = args[1];
		String posFile = args[2];
		String out = args[3];
		File inDir = new File(in);
		if (inDir.isDirectory()== false) {
			System.err.println("Parsed XML directory does not exist:" + in);
			System.exit(1);
		}

		loadGoldLabels(goldFile);
		loadPosDocs(posFile);
		annTypes = Utils.getAnnotationTypes();
		xmlReader = Utils.getXMLReader();
		processDir(in,out);
	}

}
