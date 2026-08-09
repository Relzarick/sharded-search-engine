package search;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import java.util.Map;
import java.util.UUID;

/**
 *
 * @param docTermPos List containing - UUID : TOKEN : positions array
 * @param docFreq    Intersected TOKEN : count
 */
public record QueryResult(Map<UUID, Object2ObjectOpenHashMap<String, IntArrayList>> docTermPos,
                          Object2IntOpenHashMap<String> docFreq) {
}
