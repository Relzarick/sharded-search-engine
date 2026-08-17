package search;

import etl.TokenHasher;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import redis.Index;
import redis.RedisService;

import java.util.*;
import java.util.concurrent.ExecutionException;

public class RedisQueryService implements AutoCloseable {
    private final Index[] shards;

    @SuppressWarnings("resource")
    public RedisQueryService() {
        shards = new Index[]{
                new RedisService("r1"),
                new RedisService("r2"),
                new RedisService("r3"),
                new RedisService("r4")
        };
    }

    /**
     * Calculate Term Frequency from the size of position
     *
     * @return Count of intersected UUID and its position array
     */
    public QueryResult fetchFromRedis(List<String> tokens) {
        // Each doc might have multiple matching tokens hence the nested hashmap
        Map<UUID, Object2ObjectOpenHashMap<String, IntArrayList>> docTermPos = new HashMap<>(); // Position of token
        Object2IntOpenHashMap<String> docFreqPerToken = new Object2IntOpenHashMap<>(); // How many doc contains the token
        Set<UUID> result = null;

        for (String token : tokens) {
            int instance = TokenHasher.hash(token, shards.length);

            Set<UUID> tokenUuids = new HashSet<>();
            List<PositionalPosting> postings;

            try {
                postings = shards[instance].retrieve(token);
            } catch (ExecutionException | InterruptedException e) {
                throw new RuntimeException("Failed to retrieve token: " + token, e);
            }

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
        for (Index redis : shards)
            redis.close();
    }

}