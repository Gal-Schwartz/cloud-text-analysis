package com.cloudapp.worker;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.process.PTBTokenizer;
import edu.stanford.nlp.process.CoreLabelTokenFactory;
import edu.stanford.nlp.trees.*;

import java.io.StringReader;
import java.util.List;

public class StanfordParser {

    private final LexicalizedParser lp;
    private final GrammaticalStructureFactory gsf;

    public StanfordParser() {
        lp = LexicalizedParser.loadModel(
                "edu/stanford/nlp/models/lexparser/englishPCFG.ser.gz"
        );

        TreebankLanguagePack tlp = new PennTreebankLanguagePack();
        gsf = tlp.grammaticalStructureFactory();
    }

    private List<CoreLabel> tokenize(String text) {
        return PTBTokenizer.factory(
                new CoreLabelTokenFactory(), ""
        )
        .getTokenizer(new StringReader(text))
        .tokenize();
    }

    public Object parse(String sentence, int mode) {

        List<CoreLabel> tokens = tokenize(sentence);
        Tree tree = lp.apply(tokens);

        switch (mode) {

            case 1:
                return tree.taggedYield();

            case 2:
                return tree;

            case 3:
                GrammaticalStructure gs = gsf.newGrammaticalStructure(tree);
                return gs.typedDependencies();

            default:
                throw new IllegalArgumentException("Unknown parse mode: " + mode);
        }
    }
}
