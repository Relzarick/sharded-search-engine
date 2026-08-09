package search;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import redis.Index;
import redis.RedisService;
import redis.TokenHasher;

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

    public QueryResult fetchFromRedis(List<String> tokens) {
        Map<UUID, Object2IntOpenHashMap<String>> docTermFreqs = new HashMap<>(); // How many time token appear in the doc
        Object2IntOpenHashMap<String> docFreqPerToken = new Object2IntOpenHashMap<>(); // How many doc contains the token
        Set<UUID> result = null;

        for (String token : tokens) {
            int instance = TokenHasher.hash(token, shards.length);

            Set<UUID> tokenUuids = new HashSet<>();
            List<Index.Posting> postings;

            try {
                postings = shards[instance].retrieve(token);
            } catch (ExecutionException | InterruptedException e) {
                throw new RuntimeException("Failed to retrieve token: " + token, e);
            }

            docFreqPerToken.put(token, postings.size());

            for (Index.Posting doc : postings) {
                tokenUuids.add(doc.docId());
                docTermFreqs.computeIfAbsent(doc.docId(), k -> new Object2IntOpenHashMap<>()).put(token, doc.termFreq());
            }

            if (result == null) // Stores the first result
                result = tokenUuids;
            else result.retainAll(tokenUuids);

            if (result.isEmpty())
                break;
        }

        Set<UUID> finalDocs = (result == null) ? Set.of() : result;
        docTermFreqs.keySet().retainAll(finalDocs);

        return new QueryResult(docTermFreqs, docFreqPerToken);
    }

    @Override
    public void close() {
        for (Index redis : shards)
            redis.close();
    }

}