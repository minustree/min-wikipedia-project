package edu.arizona.cs;

import org.junit.jupiter.api.Test;

public class QATest {
	
	String directory = "src/main/resources";
	String questions = "questions.txt";
	
	// SETTINGS
	boolean build = true; // whether or not to rebuild index
	boolean lem = false; // whether or not to use lemmatization
	boolean stem = true; // whether or not to use stemming
	boolean tfidf = false; // whether or not to use td-idf similarity
	
	@Test
    public void runTest() {
    	QuestionAnswering qa = new QuestionAnswering(directory, build, lem, stem);
    	qa.answerAll(qa.getQFile(questions), tfidf);
    }
}