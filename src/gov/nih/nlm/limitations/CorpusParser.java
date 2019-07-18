package gov.nih.nlm.limitations;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.uwm.pmcarticleparser.structuralelements.PMCArticleAbstract;
import edu.uwm.pmcarticleparser.structuralelements.PMCArticleFullText;
import gov.nih.nlm.ling.core.Document;
import gov.nih.nlm.ling.core.Sentence;
import gov.nih.nlm.ling.process.ComponentLoader;
import gov.nih.nlm.ling.process.SentenceSegmenter;
import gov.nih.nlm.ling.util.FileUtils;
import gov.nih.nlm.ling.wrappers.CoreNLPWrapper;
import gov.nih.nlm.pmc.MyPMCArticle;
import gov.nih.nlm.pmc.PMCSectionSegmenter;
import nu.xom.Element;
import nu.xom.Serializer;

/**
 * 
 * @author Halil Kilicoglu
 *
 */
public class CorpusParser {
	private static Logger log = Logger.getLogger(CorpusParser.class.getName());	

	private static SentenceSegmenter segmenter = null;


	private static Document parseArticle(String id, String filename) throws Exception {
		MyPMCArticle article = new MyPMCArticle(filename);
		String title = article.getTitle();
		String abstText = article.getAbstractText();
		String fullText = article.getFullTextText();
		String allText = title + abstText + fullText;
		Document doc = new Document(id, allText);

		int abstractInd = title.length();
		PMCArticleAbstract abst = article.getAbstract();
		PMCArticleFullText full = article.getFullText();
		log.info("Title:" + title);
		log.info("Abstract: " + abstText);
		log.info("Full-text: " + fullText);

		PMCSectionSegmenter sectSegmenter = new PMCSectionSegmenter(article);
		sectSegmenter.segment(doc);
		List<Sentence> sentences = new ArrayList<>();

		segmenter.segment(doc.getText(), sentences);
		for (Sentence sentence: sentences) {
			CoreNLPWrapper.coreNLP(sentence);
			doc.addSentence(sentence);
			sentence.setDocument(doc);
		}
		return doc;
	}

	public static Element processSingleFile(String id, String articleFile) throws IOException {
		Document articleDoc = null;
		Element articleXml = null;
		try {
			articleDoc = parseArticle(id,articleFile);
			articleXml = articleDoc.toXml();
		} catch (Exception e) {
			log.severe("Cannot parse " + id);
			e.printStackTrace();
		}
		return articleXml;
	}

	public static void processDirectory(String article, String out) throws IOException {
		File articleDir = new File(article);
		if (articleDir.isDirectory() == false) return;
		File outDir = new File(out);
		if (outDir.isDirectory() == false) return;
		int fileNum = 0;
		List<String> files = FileUtils.listFiles(article,false, "xml");

		for (String filename: files) {
			String id = filename.substring(filename.lastIndexOf(File.separator)+1).replace(".xml", "");
			log.log(Level.INFO,"Processing {0}: {1}.", new Object[]{id,++fileNum});
			String outFilename = outDir.getAbsolutePath() + File.separator + id + ".xml";
			PrintWriter pw = new PrintWriter(outFilename);
			try {
				Element docEl = processSingleFile(id, filename);
				nu.xom.Document xmlDoc = new nu.xom.Document(docEl);
				Serializer serializer = new Serializer(new FileOutputStream(outFilename));
				serializer.setIndent(4);
				serializer.write(xmlDoc); 
			} catch (Exception e) {
				System.err.println("ERROR PROCESSING FILE. SKIPPING.. " + id);
			}
			pw.flush();
			pw.close();
		}
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
			throws Exception {
		if (args.length < 2) {
			System.err.print("Usage: articleDirectory outputDirectory");
		}
		String articleIn = args[0];
		String out = args[1];
		File articleDir = new File(articleIn);
		if (articleDir.isDirectory() == false) {
			System.err.println("First argument is required to be an input directory:" + articleIn);
			System.exit(1);
		}
		File outDir = new File(out);
		if (outDir.isDirectory() == false) {
			System.err.println("The directory " + outDir + " doesn't exist. Creating a new directory..");
			outDir.mkdir();
		}
		// add processing properties
		Properties props = new Properties();
		props.put("sentenceSegmenter","gov.nih.nlm.pmc.PMCSentenceSegmenter");
		props.put("annotators","tokenize,ssplit,pos,lemma,parse");	
		props.put("tokenize.options","invertible=true");
		props.put("ssplit.isOneSentence","true");
		init(props);
		processDirectory(articleIn,out);
	}
}
