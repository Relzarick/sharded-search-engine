package redis;

import it.unimi.dsi.fastutil.bytes.ByteArrayList;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

/**
 * Interface for redis client.
 */
public interface Index extends AutoCloseable {
    /**
     *
     * @param postings Stores 2 values, its UUID and its matching term frequency
     */
    void set(String key, ByteArrayList postings);

    void flush();

    /**
     *
     * @param key Takes in a token and returns
     * @return The internal _id & term frequency
     */
    List<Posting> retrieve(String key) throws ExecutionException, InterruptedException;

    void close();

    /**
     *
     * @param docId    Internal _id of each document
     * @param termFreq Occurence of the token
     */
    record Posting(UUID docId, int termFreq) {
    }
}
