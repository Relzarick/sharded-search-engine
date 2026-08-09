package search;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import java.util.Map;
import java.util.UUID;

/**
 *
 * @param termFreq List containing - UUID, token & term frequency
 * @param docFreq  How many documents contained the token
 */
public record QueryResult(Map<UUID, Object2IntOpenHashMap<String>> termFreq, Object2IntOpenHashMap<String> docFreq) {
}
