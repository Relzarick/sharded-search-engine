package redis;

import search.PositionalPosting;

import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * Interface for redis client.
 */
public interface Index extends AutoCloseable {
    /**
     *
     * @param postings Stores 2 values, its UUID and its matching term frequency
     */
    void set(String key, List<PositionalPosting> postings);

    void flush();

    /**
     *
     * @return The internal _id & position of the token in the doc
     */
    List<PositionalPosting> retrieve(String key) throws ExecutionException, InterruptedException;

    void close();

}
