package edu.arizona.cs;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import edu.stanford.nlp.simple.*;

import java.util.HashMap;
import java.util.List;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Scanner;

public class QuestionAnswering {
	// variables for building index
	Directory index;
	Analyzer analyzer;
	IndexWriterConfig config;
	IndexWriter w;
	
	// index settings
    boolean indexExists = true;
    int maxLines = -1; // number of lines to index at max
    boolean stem; // whether or not to use stemming
    boolean lem; // whether or not to use lemma

    // directory and score analyzer
    String directory;
    boolean calculate = false; // whether or not to calculate mrr
    int correct = 0;
    float mrr = 0; 

    /**
     * Constructor for QuestionAnswering.
     * @param directory - directory to work in
     * @param build - whether or not to rebuild the index
     * @param lem - whether or not to use lemmatization
     * @param stem - whether or not to use stemming
     */
    public QuestionAnswering(String directory, boolean build, boolean lem, boolean stem) {
    	// initialize directory, analyzer, and settings
        this.directory = directory;
        indexExists = !build;
        this.stem = stem;
        this.lem = lem;
        if (stem)
        	analyzer = new EnglishAnalyzer();
        else
        	analyzer = new StandardAnalyzer();
        
        // creates index directory
        try {
			index = FSDirectory.open(new File("index").toPath());
	        config = new IndexWriterConfig(analyzer);
	        config.setOpenMode(OpenMode.CREATE);
	        w = new IndexWriter(index, config);
		} catch (IOException e) {
			e.printStackTrace();
		}
        
        // builds index
        if (!indexExists) buildIndex(); 
    }

    /**
     * Builds the index from the input file.
     */
    private void buildIndex() {
        // get file from resources folder
    	File dir = new File(directory);
    	File[] files = dir.listFiles();
    	
    	// index each file
    	try {
	        for (File file: files) {
	        	if (file.getName().contains("enwiki")) {
	        		indexFile(file);
		        }
	        }
	        w.close();
        } catch (Exception e) {
        	System.out.println("Could not find files in directory "+directory);
            e.printStackTrace();
        }
        indexExists = true;
    }
    
    /**
     * Retrieves a queston file from the directory.
     * @param qName - name of question file
     * @return Question file.
     */
    public File getQFile(String qName) {
        // get file from resources folder
    	File dir = new File(directory);
    	File[] files = dir.listFiles();
    	
    	// index each file
    	try {
	        for (File file: files) {
	        	if (file.getName().contains(qName)) {
	        		return file;
		        }
	        }
	        w.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    	System.out.println("Could not find question file in directory "+directory);
    	return null;
    }
    
    /**
     * Indexes a given file.
     * @param file - file to index
     */
    private void indexFile(File file) {
    	System.out.println("indexing "+file.getName());
    	BufferedReader br = null;
    	
    	try {
	        // add file data to index
    		br = new BufferedReader(new FileReader(file));
	    	Document doc = null;
	    	String text = ""; // text string
	    	String title = ""; // title string
	    	String line = "";
        	int lineCount = 0; // initialize line count
        	
        	// scans each line
	        while ((line = br.readLine()) != null) {
	        	
	        	// extract title
	        	if (line.length() >= 4) {
	            	if ((line.substring(0,2).equals("[["))
	            	&& (line.substring(line.length()-2).equals("]]"))) {
	            		line = line.substring(2,line.length()-2);
	            		lineCount = 0;
	            		// add doc before
	            		if (doc != null) {
	            			text = lemmaText(text);
	            			doc.add(new TextField("text", text, Field.Store.YES));
	            			w.addDocument(doc);
	            		}
	          
	            		// start new doc
	            		title = line;
	            		text = "";
	            		doc = new Document();
	            		doc.add(new StringField("title", title, Field.Store.YES));
	            	}		
	            }
	        	
	        	// process and add text
	        	if ((lineCount < maxLines) || (maxLines == -1)) {
		        	if (line.length() >= 4) {
			        	if ((line.substring(0,2).equals("=="))
				        && (line.substring(line.length()-2).equals("=="))) {
			        		line = line.substring(2,line.length()-2);
			        	}
		        	}
		        	text = text+" "+line;
	        	}
	        	lineCount++;	
	        }
	        // add final doc
	        if (doc != null) {
		        text = lemmaText(text);
		        doc.add(new TextField("text", text, Field.Store.YES));
				w.addDocument(doc);
	        } else System.out.println("ERROR INDEXING "+file.getName());

			// close
	        br.close(); 
	        
    	} catch (Exception e) {
    		e.printStackTrace();
    	}
    }
    
    /**
     * Given a query (and whether or not to use tfidf similarity), 
     * searches the index and returns the result.
     * @param query - query to search with
     * @param tfidf - whether or not to use tf-idf similarity
     * @return Top result of search. Returns "NULL" if there are no results.
    */
    private String search(String query, boolean tfidf, String[] ans) {
    	try {
    		// parses query and reads index
			Query q = new QueryParser("text", analyzer).parse(query);
	        IndexReader reader = DirectoryReader.open(index);
	        
	        // initializes searcher with similarity formula
	        IndexSearcher searcher = new IndexSearcher(reader);
	        if (tfidf) searcher.setSimilarity(new ClassicSimilarity());
	        
	        int amount = 10;
	        if (calculate) amount = 300000;
	        TopDocs docs = searcher.search(q, amount);
	        ScoreDoc[] hits = docs.scoreDocs;
	        
	        // prints rank of correct answer
	        if (calculate) {
		        if (hits.length <= 0) return "NULL";
		        for (int i = 0; i < hits.length; i++) {
		            Document d = searcher.doc(hits[i].doc);
		            
		            for (String s: ans) {
			            if (d.get("title").equals(s)) {
			            	mrr = mrr + 1/((float)(i+1));
			            	System.out.println("RANK: "+(i+1));
			            	break;
			            }
		            }
		        }
	        }
	        
	        // returns top result
	        String retString = null;
	        if (hits.length > 0) retString = searcher.doc(hits[0].doc).get("title");
	        if (retString == null) return "NULL";
	        return retString;
	        
		} catch (Exception e) {
			e.printStackTrace();
			return "NULL";
		}
    }
    
    /**
     * Given a file of questions, answers them and returns the top answer
     * for each question. Prints results.
     * @param qFile - question file
     * @param tfidf - whether or not to use tf-idf similarity
     */
    public void answerAll(File questions, boolean tfidf) {
    	int l = 0; // line pointer
    	
    	Scanner inputScanner;
		try {
			inputScanner = new Scanner(questions);
			String result = ""; // retrieved answer
			String answer = ""; // actual answer
			String lookup = ""; // query to lookup with
			String[] ans = null; // list of all possible answers
	    	while (inputScanner.hasNextLine()) {
	    		String line = inputScanner.nextLine();
	    		
	    		switch (l) {
	    		case 0: // category
	    			lookup = escape(line);
	    			break;
	    		case 1: // clue
	    			lookup += lemmaText(escape(line));
	    			break;
	    		case 2: // answer
	    			answer = line;
	    			ans = line.split("\\|");
	    			result = search(lookup,tfidf,ans);
	    			break;
	    		case 3: // newline
	    			System.out.println("EXPECTED: "+answer);
	    			System.out.println("ANSWER: "+result);
	    			for (String s: ans) {
		    			if (result.equals(s)) {
		    				correct++;
		    			}
	    			}
	    			System.out.println("");
	    			break;
	    		}
	        	l = (l+1)%4;
	    	}
		} catch (Exception e) {
			System.out.println("Question file not found.");
		}
		System.out.println("CORRECT: "+correct);
		if (calculate) System.out.println("MRR: "+mrr/100);
    }
    
    /**
     * Removes all special characters from a query.
     * @param query - query to remove special characters from
     * @return Query with special characters removed
     */
    private String escape(String query) {
    	return query.replaceAll("[^a-zA-Z0-9,']", " ");
    }

    /**
     * Given a text string, performs lemmatization on it.
     * @param text - string to lemmatize
     * @return Lemmatized string.
     */
    public String lemmaText(String text) {
    	if (!lem) return text;
    	Sentence sent = new Sentence(text);
    	List<String> lemmas = sent.lemmas();
    	String retString = "";
    	for (String s: lemmas) {
    		retString += s+" ";
    	}
    	return retString;
    }
    
    /**
     * Main function.
     */
    public static void main(String[] args ) {
        try {
        	String dir = "src/main/resources"; 
        	String questions = "questions.txt";
        	if (args.length > 0) dir = args[0];
        	if (args.length > 1) questions = args[1];
        	QuestionAnswering qa = new QuestionAnswering(dir, true, false, true);
            qa.answerAll(qa.getQFile(questions), false);
        }
        catch (Exception e) {
        	e.printStackTrace();
            System.out.println(e.getMessage());
        }
    }
    
}
