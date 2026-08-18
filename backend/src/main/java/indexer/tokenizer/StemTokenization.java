package indexer.tokenizer;

import opennlp.tools.stemmer.PorterStemmer;

import java.util.ArrayList;
import java.util.List;

public class StemTokenization extends BaseTokenization implements TokenStrategy {
    private static final ThreadLocal<PorterStemmer> STEMMER = ThreadLocal.withInitial(PorterStemmer::new);
    private static final ThreadLocal<CharSeq> CHECKER = ThreadLocal.withInitial(CharSeq::new);
    private static final ThreadLocal<List<String>> BUFFER = ThreadLocal.withInitial(() -> new ArrayList<>(64));

    @Override
    public void toTokens(String input, List<String> list) {
        if (input == null || input.isBlank())
            return;

        PorterStemmer stemmer = STEMMER.get();
        int length = input.length();
        int i = 0;

        while (i < length) {
            while (i < length && Character.isWhitespace(input.charAt(i)))
                i++;

            if (i >= length)
                break;

            int start = i;
            boolean isDigit = false;

            while (i < length && !Character.isWhitespace(input.charAt(i))) {
                char c = input.charAt(i);

                if (c >= '0' && c <= '9')
                    isDigit = true;

                i++;
            }

            if (isDigit)
                continue;

            int end = i;

            while (start < end && isNotValid(input.charAt(start)))
                start++;

            while (end > start && isNotValid(input.charAt(end - 1)))
                end--;

            if (start < end) {
                int tokenLength = end - start;
                stemmer.reset();

                for (int j = 0; j < tokenLength; j++) {
                    char c = input.charAt(start + j);
                    stemmer.add((char) (c | 0x20));
                }

                CharSeq checker = CHECKER.get();
                checker.set(stemmer.getResultBuffer(), stemmer.getResultLength());

                if (!STOP_WORDS.contains(checker)) {
                    stemmer.stem();
                    list.add(new String(stemmer.getResultBuffer(), 0, stemmer.getResultLength()));
                }
            }
        }
    }

    @Override
    public List<String> toTokens(String input) {
        if (input == null || input.isBlank())
            return List.of();

        // Using List to catch repeated tokens
        List<String> buf = BUFFER.get();
        buf.clear();
        
        toTokens(input, buf);
        return buf;
    }

    private boolean isNotValid(char c) {
        return (c < 'a' || c > 'z') && (c < 'A' || c > 'Z');
    }

}