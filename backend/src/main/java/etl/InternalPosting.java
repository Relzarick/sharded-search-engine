package etl;

import it.unimi.dsi.fastutil.ints.IntArrayList;

/**
 *
 * @param docIndex  Maps to the UUID[]
 * @param positions IntArrayList matching positions in the document
 */
public record InternalPosting(int docIndex, IntArrayList positions) {
}