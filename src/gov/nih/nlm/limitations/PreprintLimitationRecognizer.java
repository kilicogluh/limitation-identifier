package gov.nih.nlm.limitations;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

import gov.nih.nlm.ling.core.Document;
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
public class PreprintLimitationRecognizer {
	private static Logger log = Logger.getLogger(PreprintLimitationRecognizer.class.getName());	

	private static Map<String,List<String>> posSentences = new HashMap<>();
	private static XMLReader xmlReader;
	private static Map<Class<? extends SemanticItem>,List<String>> annTypes;

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

	private static void processSingleArticle(String inFile,String outFile) throws Exception {
		Document doc = null;
		doc = xmlReader.load(inFile, true, SemanticItemFactory.class,annTypes, null);			
		for (Sentence sent: doc.getSentences()) {
			labelSentence(sent);
		}
	}

	private static void processDir(String dir, String out) throws Exception {
		List<String> files = FileUtils.listFiles(dir, false, "xml");
		int fileNum = 0;
		for (String filename: files) {
			if (new File(filename).length() == 0) continue;
			String filenameNoExt = filename.replace(".xml", "");
			filenameNoExt = filenameNoExt.substring(filenameNoExt.lastIndexOf(File.separator)+1);
			log.info("Processing " + filenameNoExt + ":" + ++fileNum);
			processSingleArticle(filename,out);
		}	
		List<LimitationSummary> sums = new ArrayList<>();
		int posCount = 0;
		for (String filename: files) {
			String filenameNoExt = filename.replace(".xml", "");
			String notei = filenameNoExt.substring(filenameNoExt.lastIndexOf("/")+1);
			if (posSentences.containsKey(notei)) {
				List<String> sents = posSentences.get(notei);
				LimitationSummary sum = new LimitationSummary(notei.replace(".tei", "").replace("_","/"),sents.size(),sents);
				sums.add(sum);
				posCount++;
			} else {
				sums.add(new LimitationSummary(notei.replace(".tei", "").replace("_","/"),0,new ArrayList<>()));
			}
		}
		
		ObjectMapper mapper = new ObjectMapper();
		mapper.writerWithDefaultPrettyPrinter().writeValue(Paths.get(out).toFile(), sums);
		System.out.println("ARTICLES WITH LIMITATIONS: " + posCount);
	}




	public static void main(String[] args) 
			throws IOException, InstantiationException, 
			IllegalAccessException, ClassNotFoundException, Exception {
		if (args.length < 2) {
			System.err.print("Usage: inputDirectory outFile");
		}

		String in = args[0];
		String out = args[1];
		File inDir = new File(in);
		if (inDir.isDirectory()== false) {
			System.err.println("Parsed XML directory does not exist:" + in);
			System.exit(1);
		}

		annTypes = Utils.getAnnotationTypes();
		xmlReader = Utils.getXMLReader();
		processDir(in,out);
	}

}
