package search;

import etl.TokenHasher;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.rocksdb.RocksDBException;
import rocks.RocksService;

import java.io.IOException;
import java.util.*;

public class QueryService implements AutoCloseable {
    private final RocksService[] shards;

    public QueryService() throws RocksDBException, IOException {
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

        for (String token : tokens) {
            int instance = TokenHasher.hash(token, shards.length);

            Set<UUID> tokenUuids = new HashSet<>();
            List<PositionalPosting> postings;

            postings = shards[instance].retrieve(token);

            docFreqPerToken.put(token, postings.size());

            for (PositionalPosting doc : postings) {
                tokenUuids.add(doc.uuid());
                docTermPos
                        .computeIfAbsent(doc.uuid(), k -> new Object2ObjectOpenHashMap<>())
                        .put(token, doc.positions());
            }

            if (result == null) // Stores the first result
                result = tokenUuids;
            else result.retainAll(tokenUuids);

            if (result.isEmpty())
                break;
        }

        Set<UUID> finalDocs = (result == null) ? Set.of() : result;
        docTermPos.keySet().retainAll(finalDocs);

        return new QueryResult(docTermPos, docFreqPerToken);
    }

    @Override
    public void close() {
        for (RocksService index : shards)
            index.close();
    }
}