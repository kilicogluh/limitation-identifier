package gov.nih.nlm.limitations;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import gov.nih.nlm.ling.core.Document;
import gov.nih.nlm.ling.core.Section;
import gov.nih.nlm.ling.core.Sentence;
import gov.nih.nlm.ling.core.Span;
import gov.nih.nlm.ling.process.ComponentLoader;
import gov.nih.nlm.ling.process.SentenceSegmenter;
import gov.nih.nlm.ling.util.FileUtils;
import gov.nih.nlm.pmc.MyPMCArticle;
import gov.nih.nlm.pmc.PMCSectionSegmenter;

/**
 * 
 * @author Halil Kilicoglu
 *
 */
public class GenerateDataset {
	private static Logger log = Logger.getLogger(GenerateDataset.class.getName());	

	private static SentenceSegmenter segmenter = null;

	private static final Comparator<String> SENTENCE_ID_ORDER = 
			new Comparator<String>() {
		public int compare(String e1,String e2) {
			String[] els1 = e1.split("[\\t]+");
			String[] els2 = e2.split("[\\t]+");
			String id1 = els1[3];
			String id2 = els2[3];
			int idn1 = Integer.parseInt(id1.substring(1));
			int idn2 = Integer.parseInt(id2.substring(1));
			return idn1 - idn2;
		}	
	};

	private static PrintWriter pw = null;
	private static List<Sentence> DOC_POS_SENTENCES = new ArrayList<>();
	private static List<Sentence> DOC_NEG_SENTENCES = new ArrayList<>();
	private static Map<String,List<Sentence>> POS_MAP = new HashMap<>();
	private static Map<String,List<Sentence>> NEG_MAP = new HashMap<>();
	private static int  posNegSplit = 1;
	private static int MAX_INSTANCES_IF_NO_POS = 4;


	private static String getSectionTitle(Sentence sent) {
		Document doc = sent.getDocument();
		if (doc.getSections() == null) return "";
		for (Section s: doc.getSections()) {
			Span ss = s.getTextSpan();
			Span tss = s.getTitleSpan();
			if (tss == null) return "";
			if (Span.subsume(ss, sent.getSpan()) || Span.subsume(tss, sent.getSpan()))  {
				return doc.getStringInSpan(s.getTitleSpan());
			}
		}
		return "";
	}

	private static void parseSingleArticle(String id, String type) throws Exception {
		DOC_POS_SENTENCES = new ArrayList<>();
		DOC_NEG_SENTENCES = new ArrayList<>();
		MyPMCArticle article = new MyPMCArticle(id);
		String title = article.getTitle();
		String abstText = article.getAbstractText();
		String fullText = article.getFullTextText();

		String allText = title + abstText + fullText;
		Document doc = new Document(id, allText);

		PMCSectionSegmenter sectSegmenter = new PMCSectionSegmenter(article);
		sectSegmenter.segment(doc);
		List<Sentence> sentences = new ArrayList<>();
		segmenter.segment(doc.getText(), sentences);
		doc.setSentences(sentences);
		for (Sentence sentence: sentences) {
			sentence.setDocument(doc);
			log.fine("Sentence: " + sentence.getText());
		}
		for (Section sec : doc.getSections()) {
			String secTitle = "";
			if (sec.getTitleSpan() != null) 
				secTitle = doc.getStringInSpan(sec.getTitleSpan()).toLowerCase();
			labelSentences(sec, secTitle);
		}
		int pos = DOC_POS_SENTENCES.size();
		int neg = DOC_NEG_SENTENCES.size();
		int negCount = MAX_INSTANCES_IF_NO_POS;
		if (doc.getSentences().size() < 50) negCount = (int)doc.getSentences().size()/10;
		log.info("POS count:" + pos);
		log.info("NEG count:" + neg);
		if (posNegSplit > 0) {
			if (pos > 0) {
				if (neg <= negCount) {
					negCount = Math.min(neg,pos * posNegSplit);
				} else {
					negCount = pos * posNegSplit;
				}
			}  else {
				negCount = Math.min(neg, negCount);
			}
		} else {
			if (pos > 0) {
				negCount = Math.min(neg,pos * posNegSplit);
			}  else {
				negCount = Math.min(neg, negCount);
			}
		}
		POS_MAP.put(id, new ArrayList<>(DOC_POS_SENTENCES));
		Collections.shuffle(DOC_NEG_SENTENCES);
		NEG_MAP.put(id, new ArrayList<>(DOC_NEG_SENTENCES.subList(0, negCount)));

		List<String> outLines = new ArrayList<>();
		List<Sentence> posSubset = POS_MAP.get(id);
		List<Sentence> negSubset = NEG_MAP.get(id);
		for (Sentence s: posSubset) {
			outLines.add(type + "\t" + "POS\t" + id.substring(id.lastIndexOf("\\")+1) + "\t" + s.getId() + "\t" + getSectionTitle(s) + "\tPOS\t" + s.getText());
			log.fine(type + "\t" + "POS\t" + id.substring(id.lastIndexOf("\\")+1) + "\t" + s.getId() + "\t" + getSectionTitle(s) + "\tPOS\t" + s.getText());
		}
		for (Sentence s: negSubset) {
			outLines.add(type + "\t" + "NEG\t" + id.substring(id.lastIndexOf("\\")+1) + "\t" + s.getId() + "\t" + getSectionTitle(s) + "\tNEG\t" + s.getText());
			log.fine(type + "\t" + "NEG\t" + id.substring(id.lastIndexOf("\\")+1) + "\t" + s.getId() + "\t" + getSectionTitle(s) + "\tNEG\t" + s.getText());
		}
		Collections.sort(outLines,SENTENCE_ID_ORDER);
		for (String out: outLines) {
			pw.print(out);
			pw.print("\n");
		}
	}


	private static void labelSentences(Section section, String topTitle) {
		List<Section> subsects = section.getSubSections();
		if (subsects.size() == 0) {
			Document doc = section.getDocument();
			Span titleSp = section.getTitleSpan();
			String secTitle = "";
			if (titleSp != null) secTitle = doc.getStringInSpan(section.getTitleSpan());
			String lSecTitle = secTitle.toLowerCase();
			if 	(topTitle.contains("discussion") == false && topTitle.contains("conclusion") == false && topTitle.contains("limitation") == false && topTitle.contains("weakness") == false) {
				for (Sentence s: section.getSentences()) {
					if (titleSp != null && Span.overlap(titleSp, s.getSpan())) continue;
					String text = s.getText().toLowerCase();
					if (text.length() < 30) continue;
					else DOC_NEG_SENTENCES.add(s);
				}
				return;
			}
			String status = "NONE";
			if (lSecTitle.contains("limitation") || lSecTitle.contains("weakness")) {
				if (lSecTitle.contains("strength")) status = "BOTH";
				else status = "LIMIT_ONLY";
			}
			for (Sentence s: section.getSentences()) {
				if (titleSp != null && Span.overlap(titleSp, s.getSpan())) continue;
				String text = s.getText().toLowerCase();
				if (text.length() < 30) continue;
				if (status.equals("LIMIT_ONLY")) { 
					DOC_POS_SENTENCES.add(s);
				}
				else if (status.equals("NONE"))  {
					if (Utils.inLimitationParagraph(s, true)) {
						DOC_POS_SENTENCES.add(s);
						System.err.println("IN LIMITATION PARAGRAPH:" + doc.getId() + "|" + s.getText());
					}
					else DOC_NEG_SENTENCES.add(s);
				}
			}
			if (status.equalsIgnoreCase("BOTH")) {
				int firstid = -1;
				int lastid = -1;
				for (Sentence s: section.getSentences()) {
					if (titleSp != null && Span.overlap(titleSp, s.getSpan())) continue;
					String text = s.getText().toLowerCase();
					int ind = section.getSentences().indexOf(s);
					if (firstid == -1 && ( text.contains("limit") || text.contains("weak"))) { firstid = ind; lastid = -1;}
					else if (firstid >= 0 && lastid == -1 && (text.contains("strength") || text.contains("strong"))) lastid = ind;
					if (firstid >= 0 && lastid >= 0) continue;
				}
				if (firstid >=0 && lastid == -1) lastid = section.getSentences().size()-1;
				if (firstid >=0 && lastid >= 0) {
					for (Sentence s: section.getSentences()) {
						if (titleSp != null && Span.overlap(titleSp, s.getSpan())) continue;
						String text = s.getText().toLowerCase();
						if (text.length() < 30) continue;
						int ind = section.getSentences().indexOf(s);
						if ((ind < firstid) || (ind > lastid))  { 
							DOC_NEG_SENTENCES.add(s); 
							continue;
						}
						DOC_POS_SENTENCES.add(s);
					}
				} else if (firstid == -1 && lastid == -1) {
					int strengthInd = lSecTitle.indexOf("strength");
					int limitInd = lSecTitle.indexOf("limitation");
					if (limitInd == -1) limitInd = lSecTitle.indexOf("weakness");
					int sentCount = section.getSentences().size();
					int firstcount = (int)sentCount/2;
					if (strengthInd < limitInd) {
						for (int i=0; i < firstcount; i++) {
							Sentence sent = section.getSentences().get(i);
							if (titleSp != null && Span.overlap(titleSp, sent.getSpan())) continue;
							String text = sent.getText().toLowerCase();
							if (text.length() < 30) continue;
							DOC_NEG_SENTENCES.add(sent);
						}
						for (int i=firstcount+1; i < sentCount; i++) {
							Sentence sent = section.getSentences().get(i);
							if (titleSp != null && Span.overlap(titleSp, sent.getSpan())) continue;
							String text = sent.getText().toLowerCase();
							if (text.length() < 30) continue;
							DOC_POS_SENTENCES.add(sent);
						}
					} else {
						for (int i=0; i < firstcount; i++) {
							Sentence sent = section.getSentences().get(i);
							if (titleSp != null && Span.overlap(titleSp, sent.getSpan())) continue;
							String text = sent.getText().toLowerCase();
							if (text.length() < 30) continue;
							DOC_POS_SENTENCES.add(sent);
						}
						for (int i=firstcount+1; i < sentCount; i++) {
							Sentence sent = section.getSentences().get(i);
							if (titleSp != null && Span.overlap(titleSp, sent.getSpan())) continue;
							String text = sent.getText().toLowerCase();
							if (text.length() < 30) continue;
							DOC_NEG_SENTENCES.add(sent);
						}
					}
				}
			}

		} else {
			for (Section subsect: subsects) {
				labelSentences(subsect,topTitle);
			}
		}
	}

	/**
	 * 
	 * @param in	the input directory
	 * @param out  	the output directory
	 * @throws IOException if there is a problem with file reading/writing
	 */
	public static void processDirectory(String in, String out) throws IOException {
		File inDir = new File(in);
		if (inDir.isDirectory() == false) return;
		int fileNum = 0;
		List<String> files = FileUtils.listFiles(in, false, "xml");
		int numSeed =  100;
		int numTest = 300;
		pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(out),
				StandardCharsets.UTF_8), true);
		for (String filename: files) {
			String type = "SEED";
			if (fileNum < numSeed) type = "SEED";
			else if (fileNum >= numSeed && fileNum < numTest) type = "TEST";
			else type = "SEMI";
			String id = filename.substring(filename.lastIndexOf(File.separator)+1).replace(".xml", "");
			log.log(Level.INFO,"Processing {0}: {1}. {2}", new Object[]{id,++fileNum, type});
			try {
				parseSingleArticle(filename, type);
			} catch (Exception e) {
				e.printStackTrace();
				log.warning("ERROR PROCESSING FILE. SKIPPING.. " + id);
			}
		}
		pw.flush();
		pw.close();
	}

	public static void init(Properties props) 
			throws InstantiationException, IllegalAccessException, ClassNotFoundException {
		//		CoreNLPWrapper.getInstance(props);
		segmenter = ComponentLoader.getSentenceSegmenter(props);
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
			System.err.println("PMC XML directory does not exist:" + in);
			System.exit(1);
		}
		Properties props = new Properties();
		props.put("sentenceSegmenter","gov.nih.nlm.pmc.PMCSentenceSegmenter");
		/*		props.put("annotators","tokenize,ssplit,pos,lemma,parse");	
		props.put("tokenize.options","invertible=true");
		props.put("ssplit.isOneSentence","true");*/
		init(props);
		processDirectory(in,out);
	}
}
