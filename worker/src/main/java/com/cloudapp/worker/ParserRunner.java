package com.cloudapp.worker;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class ParserRunner {

    private final StanfordParser parser;

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

                if (sentence.trim().isEmpty()) continue;

                Object result = parser.parse(sentence, mode);
                System.out.println("parsed sentence " + count);

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

        System.out.println("Saved output to: " + outFile.getAbsolutePath());
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
        return Arrays.asList(
                text.replace("\n", " ")
                    .split("(?<=[.!?])\\s+")
        );
    }
}
