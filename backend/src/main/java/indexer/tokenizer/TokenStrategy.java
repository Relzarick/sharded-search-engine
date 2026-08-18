package indexer.tokenizer;

import java.util.List;

/**
 * All tokenizers should implement this interface
 */
public interface TokenStrategy {
    void toTokens(String input, List<String> list);

    /**
     * Returns all valid tokens from the string.
     *
     * @param input Takes in raw unfilterd inputs
     */
    List<String> toTokens(String input);
}