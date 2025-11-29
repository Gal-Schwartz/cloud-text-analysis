package com.cloudapp.worker;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.process.PTBTokenizer;
import edu.stanford.nlp.process.CoreLabelTokenFactory;
import edu.stanford.nlp.trees.*;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class ParserRunner {

    private final StanfordParser parser;

    public ParserRunner() {
        this.parser = new StanfordParser();
    }

    /**
     * Processes a URL line-by-line (streaming). Much lower memory usage.
     */
    public File process(String url, String modeStr) throws Exception {
        int mode = parseMode(modeStr);

        System.out.println("Processing: mode=" + modeStr + " | url=" + url);

        // Determine output file name
        String baseName = url.substring(url.lastIndexOf('/') + 1);
        if (baseName.isEmpty()) baseName = "input.txt";

        String outputFileName = "output_" + modeStr + "_" + baseName;
        File outFile = new File(outputFileName);

        URL inputUrl = new URL(url);

        try (BufferedReader reader = new BufferedReader(
                     new InputStreamReader(inputUrl.openStream(), StandardCharsets.UTF_8));
             PrintWriter out = new PrintWriter(
                     new OutputStreamWriter(new FileOutputStream(outFile), StandardCharsets.UTF_8))) {

            String line;
            int count = 1;

            while ((line = reader.readLine()) != null) {

                line = line.trim();
                if (line.isEmpty()) continue;

                System.out.println("RAW: [" + line + "]");

                Object result;
                try {
                    result = parser.parse(line, mode);
                } catch (Exception e) {
                    // Record the error for this particular sentence
                    out.println("===== Sentence " + count + " =====");
                    out.println("Text: " + line);
                    out.println("ERROR: " + e.getMessage());
                    out.println();
                    count++;
                    continue;
                }

                // Write formatted result
                out.println("===== Sentence " + count + " =====");
                out.println("Text: " + line);
                out.println(result.toString());
                out.println();

                count++;
            }
        }

        System.out.println("Saved output to: " + outFile.getAbsolutePath());
        return outFile;
    }

    private int parseMode(String modeStr) {
        switch (modeStr) {
            case "POS":           return 1;
            case "CONSTITUENCY":  return 2;
            case "DEPENDENCY":    return 3;
            default:
                throw new IllegalArgumentException("Unknown mode: " + modeStr);
        }
    }
}
