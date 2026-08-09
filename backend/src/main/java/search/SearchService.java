package search;

import indexer.InvertedIndexer;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import mongo.Repository;

import java.util.*;

public class SearchService {
    private final Repository mongo;
    private final RedisQueryService query = new RedisQueryService();
    private final InvertedIndexer indexer;

    public SearchService(Repository db, InvertedIndexer idx) {
        mongo = db;
        indexer = idx;
    }

    public String find(String input, int offset, int size) {
        List<String> cleanedInput = indexer.tokenizeKeyWords(input);

        QueryResult queryResult = query.fetchFromRedis(cleanedInput);
        long totalDoc = mongo.getCollection().estimatedDocumentCount();

        if (queryResult.termFreq().isEmpty())
            return "{count: 0, rows: []}";

        List<UUID> rankedResults = rankResults(queryResult, totalDoc, offset, size);

        return mongo.fetchMany(rankedResults).jsonify(queryResult.termFreq().size());
    }

    private List<UUID> rankResults(QueryResult result, long totalDocCount, int offset, int size) {
        Map<UUID, Double> tokenScore = new HashMap<>();

        for (Map.Entry<UUID, Object2IntOpenHashMap<String>> doc : result.termFreq().entrySet()) {
            Object2IntOpenHashMap<String> tokenFreqs = doc.getValue();
            UUID docId = doc.getKey();

            double score = 0.0;

            // Gets the term frequency of each token
            for (Object2IntMap.Entry<String> tf : tokenFreqs.object2IntEntrySet()) {
                int termFreq = tf.getIntValue();
                String token = tf.getKey();

                double idf = Math.log((double) totalDocCount / result.docFreq().getInt(token));
                score += termFreq * idf;
            }

            tokenScore.put(docId, score);
        }

        List<UUID> keys = new ArrayList<>(tokenScore.keySet());
        keys.sort((a, b) -> Double.compare(tokenScore.get(b), tokenScore.get(a)));

        if (offset >= keys.size())
            return List.of();

        int end = Math.min(offset + size, keys.size());

        return keys.subList(offset, end);
    }

}