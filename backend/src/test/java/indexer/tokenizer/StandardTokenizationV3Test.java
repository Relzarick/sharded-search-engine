package indexer.tokenizer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StandardTokenizationV3Test {
    private TokenStrategy tokenizer;

    @BeforeEach
    void setup() {
        tokenizer = new StandardTokenizationV3();
    }

    private List<String> getTokens(String input) {
        List<String> tokens = new ArrayList<>();
        tokenizer.toTokens(input, tokens);

        return tokens;
    }

    @Test
    void normalSentenceTest() {
        assertEquals(List.of("test"), getTokens("This, is a test!"));
    }

    @Test
    void weirdSentenceTest() {
        assertEquals(List.of("test"), getTokens("This+, is the 3rd test!"));
        assertEquals(List.of("test"), getTokens("...test !!!"));

        assertTrue(getTokens("don't").isEmpty());
    }

    @Test
    void edgeCaseTest() {
        assertEquals(List.of("test"), getTokens("test   "));
        assertEquals(List.of("test"), getTokens("{_test'"));
    }

    @Test
    void numberTest() {
        assertTrue(getTokens("9").isEmpty());
        assertTrue(getTokens("-9").isEmpty());
        assertTrue(getTokens("+9").isEmpty());
    }

    @Test
    void InputEmptyTest() {
        assertTrue(getTokens("").isEmpty());
    }

}