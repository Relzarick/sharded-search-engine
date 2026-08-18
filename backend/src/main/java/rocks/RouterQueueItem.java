package rocks;

import search.PositionalPosting;

import java.util.List;
import java.util.TreeMap;

/**
 * Interface for router to index
 */
public sealed interface RouterQueueItem {
    /**
     * This holds a complete batch to be ingested by index
     *
     * @param batch Is a List of Token : List of UUID + Positions
     */
    record IndexBatch(TreeMap<String, List<PositionalPosting>> batch) implements RouterQueueItem {
    }

    record PoisonPill() implements RouterQueueItem {
    }

}