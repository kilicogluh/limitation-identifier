package gov.nih.nlm.limitations;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Logger;

import gov.nih.nlm.ling.core.Document;
import gov.nih.nlm.ling.core.Section;
import gov.nih.nlm.ling.core.Sentence;
import gov.nih.nlm.ling.io.XMLReader;
import gov.nih.nlm.ling.sem.SemanticItem;
import gov.nih.nlm.ling.sem.SemanticItemFactory;
import gov.nih.nlm.ling.util.FileUtils;

/**
 * 
 * @author Halil Kilicoglu
 *
 */
public class RuleBasedLimitationSentenceRecognizerBaseline {
	private static Logger log = Logger.getLogger(RuleBasedLimitationSentenceRecognizerBaseline.class.getName());	

	private static Map<String,String> goldLabels = new HashMap<>();
	private static Map<String,String> predictLabels = new HashMap<>();
	private static Map<String,String> goldSentences = new HashMap<>();
	private static Set<String> goldDocs = new HashSet<>();
	private static XMLReader xmlReader;
	private static Map<Class<? extends SemanticItem>,List<String>> annTypes;

	private static void labelSentence(Sentence sent) {
		Document doc = sent.getDocument();
		String key = doc.getId() + "_" + sent.getId();
		Section topSect = Utils.getTopSection(sent);
		if (topSect == null) {
			predictLabels.put(key, "NEG");
			log.severe("Empty section " + sent.toString());
			return;
		}
		String topTitle = topSect.getTitle();
		if (topTitle == null) {
			predictLabels.put(key, "NEG");
			return;
		}
		topTitle = topTitle.toLowerCase();
		if 	(topTitle.contains("discussion") == false && topTitle.contains("conclusion") == false && topTitle.contains("limitation") == false && topTitle.contains("weakness") == false) {
			predictLabels.put(key, "NEG");
			return;
		}
		Section sect = doc.getSection(sent);
		String status = "NONE";
		if (sect == null) sect= topSect;
		String lSecTitle =sect.getTitle().toLowerCase();
		if (lSecTitle.contains("limitation") || lSecTitle.contains("weakness")) {
			if (lSecTitle.contains("strength")) status = "BOTH";
			else status = "LIMIT_ONLY";
		}
		if (status.equals("LIMIT_ONLY")) { 
			predictLabels.put(key, "POS");
		}
		else if (status.equals("NONE"))  {
			if (Utils.inLimitationParagraph(sent,true)) {
				predictLabels.put(key, "POS");
				log.fine("In limitation paragraph: " + doc.getId() + "|" + sent.getText());
			}
			else {
				predictLabels.put(key, "NEG");
			}
		} else if (status.equalsIgnoreCase("BOTH")){
			if (Utils.inLimitationParagraph(sent,true)) {
				predictLabels.put(key, "POS");
			} else
				predictLabels.put(key, "NEG");
		}
	}

	private static void processSingleArticle(String inFile, Properties props, String outFile) throws Exception {
		Document doc = null;
		doc = xmlReader.load(inFile, true, SemanticItemFactory.class,annTypes, null);
		for (Sentence sent: doc.getSentences()) {
			String key = doc.getId() + "_" + sent.getId();
			if (goldLabels.containsKey(key) == false) continue;
			String text = sent.getText();
			if (goldSentences.get(key).equals(text)) {
				labelSentence(sent);
			} else {
				log.warning("ERROR " + key + " " + text);
				continue;
			}

		}
	}

	private static void processDir(String dir, Properties props, String out) throws Exception {
		List<String> files = FileUtils.listFiles(dir, false, "xml");
		int fileNum = 0;
		for (String filename: files) {
			String filenameNoExt = filename.replace(".xml", "");
			filenameNoExt = filenameNoExt.substring(filenameNoExt.lastIndexOf(File.separator)+1);
			if (goldDocs.contains(filenameNoExt) == false) continue;
			log.info("Processing " + filenameNoExt + ":" + ++fileNum);
			processSingleArticle(filename,props, out);
		}	
		calculatePerformance(out);
	}

	private static void calculatePerformance(String outfile) throws Exception {
		if (goldLabels.size() != predictLabels.size()) {
			log.severe("GOLD and PREDICT counts do not match.");
			return;
		}
		PrintWriter pw = new PrintWriter(outfile);
		int TP=0;
		int FP=0;
		int FN=0;
		int TN=0;
		List<String> sentids = new ArrayList<>(goldLabels.keySet());
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
		log.info("TP|FP|FN|TN:" + TP +"|" + FP + "|" + FN + "|" +TN);
		log.info("PRECISION " + precision);
		log.info("RECALL    " + recall);
		log.info("F-SCORE   " + fscore);
		log.info("ACCURACY  " + accuracy);
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
			log.fine(key + " " + goldLabels.get(key));
		}
	} 

	public static void main(String[] args) 
			throws IOException, InstantiationException, 
			IllegalAccessException, ClassNotFoundException, Exception {
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
		Properties props = new Properties();
		loadGoldLabels(goldFile);
		annTypes = Utils.getAnnotationTypes();
		xmlReader = Utils.getXMLReader();
		processDir(in,props,out);
	}

}
