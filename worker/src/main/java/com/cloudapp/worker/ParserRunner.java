package com.cloudapp.worker;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ParserRunner {

    private final StanfordParser parser;
    private static final Logger logger = LoggerFactory.getLogger(ParserRunner.class);


    public ParserRunner() {
        this.parser = new StanfordParser();
    }

    public File process(String url, String modeStr) throws Exception {
        int mode = parseMode(modeStr);

        System.out.println("Processing: mode=" + modeStr + " | url=" + url);
        String text = download(url);
        List<String> sentences = splitIntoSentences(text);
        String baseName = url.substring(url.lastIndexOf('/') + 1);
        if (baseName.isEmpty()) {
            baseName = "input.txt";
        }

        String outputFileName = "output_" + modeStr + "_" + baseName;
        File outFile = new File(outputFileName);

        try (PrintWriter out = new PrintWriter(outFile, StandardCharsets.UTF_8)) {
            int count = 1;
            for (String sentence : sentences) {
                logger.info("RAW SENTENCE = [" + sentence + "]");
                if (sentence.trim().isEmpty()) continue;

                Object result = parser.parse(sentence, mode);
                logger.info("parsed sentence " + count);

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

        logger.info("Saved output to: " + outFile.getAbsolutePath());
        return outFile;
    }

    private int parseMode(String modeStr) {
        switch (modeStr) {
            case "POS":
                return 1;
            case "CONSTITUENCY":
                return 2;
            case "DEPENDENCY":
                return 3;
            default:
                throw new IllegalArgumentException("Unknown mode: " + modeStr);
        }
    }

    private String download(String urlString) throws Exception {
        URL url = new URL(urlString);
        try (InputStream in = url.openStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private List<String> splitIntoSentences(String text) {
        text = text.replace("\r", "");
        String[] raw = text.split("\\n+");
        List<String> out = new ArrayList<>();
        for (String s : raw) {
            s = s.trim();
            if (!s.isEmpty()) out.add(s);
        }
        return out;
    }

}
