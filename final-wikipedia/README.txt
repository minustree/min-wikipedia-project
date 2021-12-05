### HOW TO USE ###

-JAR-
The jar has two optional arguments for the directory and the name of the question file.
By default, the directory is “src/main/resources”, and the question file is called “questions.txt”.
The wikipedia files MUST be in the given directory and contain “enwiki” in their filename.
The question file MUST be in the given directory.

Please make sure that the .jar file and the source directory given are in the same location.

To run, use the command:
java -jar QA-project.jar

To run with arguments, use the command:
java -jar QA-project.jar <directory_name> <questionfile_name>

- RUN MANUALLY -
Maven is required.
Go to the directory containing pom.xml.
Run "mvn test".

By default, the code will build the index using stemming and Lucene's default similarity. This may take a while.
The code will print out the questions and the resulting answers, as well as how many were correctly answered by the code.

To change these settings, edit QATest.java.
(There are four boolean values at the top of the file called build, lem, stem, and tfidf).
