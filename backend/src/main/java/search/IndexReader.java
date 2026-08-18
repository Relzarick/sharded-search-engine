package search;

import etl.TokenHasher;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.rocksdb.RocksDBException;
import rocks.PositionalPosting;
import rocks.RocksService;

import java.io.IOException;
import java.util.*;

public class IndexReader implements AutoCloseable {
    private final RocksService[] shards;

    public IndexReader() throws RocksDBException, IOException {
        shards = new RocksService[]{
                new RocksService("shard-0"),
                new RocksService("shard-1")
        };
    }

    /**
     * Calculate Term Frequency from the size of position
     *
     * @return Count of intersected UUID and its position array
     */
    public QueryResult fetchFromIndex(List<String> tokens) {
        // Each doc might have multiple matching tokens hence the nested hashmap
        Map<UUID, Object2ObjectOpenHashMap<String, IntArrayList>> docTermPos = new HashMap<>(); // Position of token
        Object2IntOpenHashMap<String> docFreqPerToken = new Object2IntOpenHashMap<>(); // How many doc contains the token
        Set<UUID> result = null;

        // This is later used to populate docTermPos
        Map<String, List<PositionalPosting>> storedPostings = new HashMap<>();

        // Loop each token, filtering for intersected UUID
        for (String token : tokens) {
            int instance = TokenHasher.hash(token, shards.length);
            List<PositionalPosting> postings = shards[instance].retrieve(token);

            docFreqPerToken.put(token, postings.size());
            storedPostings.put(token, postings);

            Set<UUID> tokenUuids = new HashSet<>();
            for (PositionalPosting doc : postings)
                tokenUuids.add(doc.uuid());

            // Stores the UUIDs from the first token, filtering it against UUIDs from subsequent batches
            if (result == null)
                result = tokenUuids;
            else result.retainAll(tokenUuids);

            if (result.isEmpty())
                break;
        }

        Set<UUID> filteredUUIDs = (result == null) ? Set.of() : result;

        if (!filteredUUIDs.isEmpty()) {
            for (Map.Entry<String, List<PositionalPosting>> entry : storedPostings.entrySet()) {
                String token = entry.getKey();
                List<PositionalPosting> postings = entry.getValue();

                for (PositionalPosting posting : postings) {
                    if (filteredUUIDs.contains(posting.uuid()))
                        docTermPos
                                .computeIfAbsent(posting.uuid(), k -> new Object2ObjectOpenHashMap<>())
                                .put(token, posting.positions());

                }
            }
        }

        return new QueryResult(docTermPos, docFreqPerToken);
    }

    @Override
    public void close() {
        for (RocksService index : shards)
            index.close();
    }
}