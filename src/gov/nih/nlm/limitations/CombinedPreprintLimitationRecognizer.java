package gov.nih.nlm.limitations;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

import gov.nih.nlm.ling.core.Document;
import gov.nih.nlm.ling.core.Sentence;
import gov.nih.nlm.ling.process.ComponentLoader;
import gov.nih.nlm.ling.process.SentenceSegmenter;
import gov.nih.nlm.ling.util.FileUtils;
import gov.nih.nlm.ling.wrappers.CoreNLPWrapper;

/**
 * Combined pipeline for preprint processing. Performs preprocessing with Stanford CoreNLP, and 
 * identifies limitation sentences.
 * 
 * @author Halil Kilicoglu
 *
 */

public class CombinedPreprintLimitationRecognizer {
	private static Logger log = Logger.getLogger(CombinedPreprintLimitationRecognizer.class.getName());	

	private static SentenceSegmenter segmenter = null;
	private static Map<String,List<String>> posSentences = new HashMap<>();

	private static void labelSentence(Sentence sent) {
		Document doc = sent.getDocument();
		String lbl = label(sent);
		if (lbl.equals("POS")) {
			List<String> ex = new ArrayList<>();
			if (posSentences.containsKey(doc.getId()))
					ex = posSentences.get(doc.getId());
			ex.add(sent.getText());
			posSentences.put(doc.getId(),ex);
		}
	}

	public static String label(Sentence sent) {
		Document doc = sent.getDocument();
//		if (Utils.inLimitationParagraph(sent,false)) { // More strict version 
		if (Utils.inLimitationParagraph2(sent,false)) {			// Looser version
			log.fine("In limitation paragraph:" + doc.getId() + "|" + sent.getId() + "|" +  sent.getText());
			return "POS";
		}
		else {
			return "NEG";
		}
	}

	private static Document preprocessArticle(String id, String filename) throws Exception {
		String allText = stripNonValidXML(FileUtils.stringFromFile(filename, "UTF-8"));
		Document doc = new Document(id, allText);
		log.fine("Full-text: " + allText);

		List<Sentence> sentences = new ArrayList<>();
		segmenter.segment(doc.getText(), sentences);
		for (Sentence sentence: sentences) {
			CoreNLPWrapper.coreNLP(sentence);
			doc.addSentence(sentence);
			sentence.setDocument(doc);
		}
		return doc;
	}
	
	private static String stripNonValidXML(String in) {
	    StringBuffer out = new StringBuffer();
	    char current;

	    if (in == null || ("".equals(in))) return ""; 
	    for (int i = 0; i < in.length(); i++) {
	        current = in.charAt(i); 
	        if ((current == 0x9) ||
	            (current == 0xA) ||
	            (current == 0xD) ||
	            ((current >= 0x20) && (current <= 0xD7FF)) ||
	            ((current >= 0xE000) && (current <= 0xFFFD)) ||
	            ((current >= 0x10000) && (current <= 0x10FFFF)))
	            out.append(current);
	    }
	    return out.toString();
	} 
		
	public static void processArticle(String id, String filename) throws Exception {
		Document doc = preprocessArticle(id,filename);
		if (doc.getSentences() == null) return;
		for (Sentence sent: doc.getSentences()) {
			labelSentence(sent);
		}
	}
	
	public static void processDirectory(String dir, String out) throws Exception {
		File articleDir = new File(dir);
		if (articleDir.isDirectory() == false) return;
		int fileNum = 0;
		List<String> files = FileUtils.listFiles(dir,false, "txt");

		for (String filename: files) {
			String id = filename.substring(filename.lastIndexOf(File.separator)+1).replace(".txt", "");
			log.info("Processing " + id + ": " + ++fileNum);
			processArticle(id,filename);
		}
		List<LimitationSummary> sums = new ArrayList<>();
		int posCount = 0;
		for (String filename: files) {
			String id = filename.substring(filename.lastIndexOf(File.separator)+1).replace(".txt", "");
			if (posSentences.containsKey(id)) {
				List<String> sents = posSentences.get(id);
				LimitationSummary sum = new LimitationSummary(id.replace(".tei", "").replace("_","/"),sents.size(),sents);
				sums.add(sum);
				posCount++;
			} else {
				sums.add(new LimitationSummary(id.replace(".tei", "").replace("_","/"),0,new ArrayList<>()));
			}
		}
		
		ObjectMapper mapper = new ObjectMapper();
		mapper.writerWithDefaultPrettyPrinter().writeValue(Paths.get(out).toFile(), sums);
		System.out.println("Number of preprints with limitations: " + posCount);
		System.out.println("Number of total preprints: " + files.size());
	}
	

	/**
	 * Initializes CoreNLP and the sentence segmenter from properties.
	 * 
	 * @param props	the properties to use for initialization
	 * 
	 * @throws ClassNotFoundException	if the sentence segmenter class cannot be found
	 * @throws IllegalAccessException	if the sentence segmenter cannot be accessed
	 * @throws InstantiationException	if the sentence segmenter cannot be initializaed
	 */
	public static void init(Properties props) 
			throws InstantiationException, IllegalAccessException, ClassNotFoundException {
		CoreNLPWrapper.getInstance(props);
		segmenter = ComponentLoader.getSentenceSegmenter(props);
	}

	public static void main(String[] args) 
			throws IOException, InstantiationException, 
			IllegalAccessException, ClassNotFoundException, Exception {
		if (args.length < 2) {
			System.err.print("Usage: articleDirectory outFile");
		}

		String in = args[0];
		String out = args[1];
		File inDir = new File(in);
		if (inDir.isDirectory() == false) {
			System.err.println("First argument is required to be an input directory:" + in);
			System.exit(1);
		}
		
		Properties props = new Properties();
		props.put("sentenceSegmenter","gov.nih.nlm.pmc.PMCSentenceSegmenter");
		props.put("annotators","tokenize,ssplit,pos,lemma");	
		props.put("tokenize.options","invertible=true");
		props.put("ssplit.isOneSentence","true");
		init(props);
		processDirectory(in,out);
	}

}
