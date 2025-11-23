package com.cloudapp.worker;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

import edu.stanford.nlp.trees.GrammaticalStructure;

public class ParserRunner {

    private final StanfordParser parser;

    public ParserRunner() {
        this.parser = new StanfordParser();
    }

    public void run(String instructionFilePath) throws Exception {

        try (BufferedReader br = new BufferedReader(new FileReader(instructionFilePath))) {
            String line;

            //while ((line = br.readLine()) != null) {
            line = br.readLine();

                //if (line.trim().isEmpty()) continue;

                // TAB-separated
                String[] parts = line.split("\t");

                if (parts.length != 2) {
                    System.err.println("Invalid input line: " + line);
                    //continue;
                }

                String modeStr = parts[0].trim();
                System.out.println(modeStr);
                String url = parts[1].trim();

                int mode = 0;
                switch(modeStr)
                {
                    case "POS" : mode = 1; break;
                    case "CONSTITUENCY" : mode = 2; break;
                    case "DEPENDENCY": mode = 3; break;
                }
                System.out.println("Processing: " + mode + " | " + url);

                String text = download(url);
                List<String> sentences = splitIntoSentences(text);
                String baseName = url.substring(url.lastIndexOf('/') + 1);
                String outputFile = "output_" + mode + "_" + baseName;

                try (PrintWriter out = new PrintWriter(outputFile, StandardCharsets.UTF_8)) {

                    int count = 1;
                    for (String sentence : sentences) {

                        if (sentence.trim().isEmpty()) continue;

                        Object result = parser.parse(sentence, mode);
                        System.out.println("parsed");

                        out.println("===== Sentence " + count + " =====");
                        out.println("Text: " + sentence);

                        if (mode == 2) {
                            out.println(result.toString()); // Tree
                        } else {
                            // POS or DEPENDENCY
                            out.println(result);
                        }

                        out.println();
                        count++;
                    }
                }

                System.out.println("Saved output to: " + outputFile + "\n");
            //}
        }
    }

    private String download(String urlString) throws Exception {
        URL url = new URL(urlString);
        try (InputStream in = url.openStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private List<String> splitIntoSentences(String text) {
        return Arrays.asList(text
                .replace("\n", " ")
                .split("(?<=[.!?])\\s+"));
    }
}
