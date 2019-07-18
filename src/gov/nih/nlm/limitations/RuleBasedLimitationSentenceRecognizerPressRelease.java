package gov.nih.nlm.limitations;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import gov.nih.nlm.ling.core.Document;
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
public class RuleBasedLimitationSentenceRecognizerPressRelease {
	private static Logger log = Logger.getLogger(RuleBasedLimitationSentenceRecognizerPressRelease.class.getName());	

	private static Map<String,String> predictLabels = new HashMap<>();
	private static XMLReader xmlReader;
	private static Map<Class<? extends SemanticItem>,List<String>> annTypes;

	private static void labelSentence(Sentence sent) {
		Document doc = sent.getDocument();
		String key = doc.getId() + "_" + sent.getId();
		predictLabels.put(key,label(sent));		
	}

	public static String label(Sentence sent) {
		Document doc = sent.getDocument();
		if (Utils.inLimitationParagraph(sent,false)) {
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
			String filenameNoExt = filename.replace(".xml", "");
			filenameNoExt = filenameNoExt.substring(filenameNoExt.lastIndexOf(File.separator)+1);
			log.info("Processing " + filenameNoExt + ":" + ++fileNum);
			processSingleArticle(filename,out);
		}	
		PrintWriter pw = new PrintWriter(out);
		List<String> ids = new ArrayList<String>(predictLabels.keySet());
		Collections.sort(ids, new Comparator<String>() {
			public int compare(String a, String b) {
				String[] as = a.split("_");
				String[] bs = b.split("_");
				if (as[0].equals(bs[0])) {
					int ai = Integer.parseInt(as[1].substring(1));
					int bi= Integer.parseInt(bs[1].substring(1));
					return ai-bi;
				} else {
					int ai = Integer.parseInt(as[0]);
					int bi= Integer.parseInt(bs[0]);
					return ai - bi;
				}	  
			}
		});
		List<String> seenPos = new ArrayList<>();
		for (String s: ids) {
			String[] ss = s.split("_");
			if (seenPos.contains(ss[0])) continue;
			if (predictLabels.get(s).equals("POS"))
				seenPos.add(ss[0]);
		}
		for (String filename: files) {
			String filenameNoExt = filename.replace(".xml", "");
			filenameNoExt = filenameNoExt.substring(filenameNoExt.lastIndexOf(File.separator)+1);
			if (seenPos.contains(filenameNoExt)) 
				pw.write(filenameNoExt + "\tPOS\n");
			else 
				pw.write(filenameNoExt + "\tNEG\n");
		}
		pw.flush();
		pw.close();
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
