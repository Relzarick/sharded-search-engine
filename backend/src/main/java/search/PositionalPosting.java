package search;

import it.unimi.dsi.fastutil.ints.IntArrayList;

import java.util.UUID;

/**
 *
 * @param uuid      _id of each docuement
 * @param positions Tracks the position of the token in an array
 */
public record PositionalPosting(UUID uuid, IntArrayList positions) {
}
