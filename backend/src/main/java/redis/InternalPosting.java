package redis;

import it.unimi.dsi.fastutil.ints.IntArrayList;

/**
 *
 * @param docIndex  Maps to the UUID[]
 * @param positions An array of in matching its position in the document
 */
public record InternalPosting(int docIndex, IntArrayList positions) {
}