package search;

import indexer.InvertedIndexer;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import mongo.Repository;
import org.bson.BsonDocument;
import org.bson.UuidRepresentation;

import java.util.*;

public class SearchService {
    private final Repository mongo;
    private final RedisQueryService query = new RedisQueryService();
    private final InvertedIndexer indexer;

    private static final double PROXIMITY_WEIGHT = 5.0;
    private static final double PHRASE_WEIGHT = 2.0;
    private static final int NO_MATCH = Integer.MAX_VALUE;

    public SearchService(Repository db, InvertedIndexer idx) {
        mongo = db;
        indexer = idx;
    }

    public String find(String input, int offset, int size) {
        List<String> cleanedInput = indexer.tokenizeKeyWords(input);

        QueryResult queryResult = query.fetchFromRedis(cleanedInput);
        long totalDoc = mongo.getCollection().estimatedDocumentCount();

        if (queryResult.docTermPos().isEmpty())
            return "{count: 0, rows: []}";

        List<UUID> rankedResults = rankResults(queryResult, cleanedInput, totalDoc, offset, size);
        List<BsonDocument> unordered = mongo.fetchMany(rankedResults).documents();
        List<BsonDocument> ordered = reorder(unordered, rankedResults);

        return new DocumentResults(ordered).jsonify(queryResult.docTermPos().size());
    }

    private List<BsonDocument> reorder(List<BsonDocument> documents, List<UUID> rankedOrder) {
        Map<UUID, BsonDocument> byId = new HashMap<>(documents.size());

        for (BsonDocument doc : documents)
            byId.put(doc.getBinary("_id").asUuid(UuidRepresentation.STANDARD), doc);

        List<BsonDocument> ordered = new ArrayList<>(rankedOrder.size());

        for (UUID docId : rankedOrder) {
            BsonDocument doc = byId.get(docId);
            if (doc != null)
                ordered.add(doc);
        }

        return ordered;
    }

    private List<UUID> rankResults(QueryResult result, List<String> queryTokens, long totalDocCount, int offset, int size) {
        Map<UUID, Double> tokenScore = new HashMap<>();

        for (Map.Entry<UUID, Object2ObjectOpenHashMap<String, IntArrayList>> doc : result.docTermPos().entrySet()) {
            Object2ObjectOpenHashMap<String, IntArrayList> tokenPosition = doc.getValue();
            UUID uuid = doc.getKey();

            double tfidfScore = 0.0;

            for (Object2ObjectMap.Entry<String, IntArrayList> position : tokenPosition.object2ObjectEntrySet()) {
                int termFreq = position.getValue().size();
                String token = position.getKey();

                double idf = Math.log((double) totalDocCount / result.docFreq().getInt(token));
                tfidfScore += termFreq * idf;
            }

            double score = tfidfScore;

            // Ranking is only for multi-token queries
            if (queryTokens.size() > 1) {
                int minGap = minGapAcrossTokens(tokenPosition, queryTokens);

                if (minGap == queryTokens.size() - 1)
                    score += PHRASE_WEIGHT * tfidfScore;
                else if (minGap < Integer.MAX_VALUE)
                    score += PROXIMITY_WEIGHT * (1.0 / minGap);

            }

            tokenScore.put(uuid, score);
        }

        List<UUID> keys = new ArrayList<>(tokenScore.keySet());
        keys.sort((a, b) -> Double.compare(tokenScore.get(b), tokenScore.get(a)));

        if (offset >= keys.size())
            return List.of();

        int end = Math.min(offset + size, keys.size());

        return keys.subList(offset, end);
    }

    // AI magic algo
    private int minGapAcrossTokens(Object2ObjectOpenHashMap<String, IntArrayList> tokenPosition, List<String> queryTokens) {
        // 1. Exact phrase check first: does query[0], query[1], ... appear at consecutive positions in order?
        IntArrayList firstTokenPositions = tokenPosition.get(queryTokens.getFirst());
        if (firstTokenPositions != null) {
            for (int start : firstTokenPositions) {
                boolean isPhrase = true;

                for (int i = 1; i < queryTokens.size(); i++) {
                    IntArrayList nextPositions = tokenPosition.get(queryTokens.get(i));

                    if (nextPositions == null ||
                            IntArrays.binarySearch(nextPositions.elements(), 0, nextPositions.size(), start + i) < 0) {
                        isPhrase = false;
                        break;
                    }
                }

                if (isPhrase)
                    return queryTokens.size() - 1; // exact phrase: gap == token count - 1
            }
        }

        // 2. No exact phrase — fall back to general min-gap (order-agnostic proximity)
        // Smallest range containing at least one position from every query token's list.
        List<int[]> tagged = new ArrayList<>(); // [position, tokenIndex]

        for (int i = 0; i < queryTokens.size(); i++) {
            IntArrayList positions = tokenPosition.get(queryTokens.get(i));
            if (positions == null)
                return NO_MATCH; // token missing entirely for this doc — shouldn't happen post-intersection, but defensive

            for (int p : positions)
                tagged.add(new int[]{p, i});
        }

        tagged.sort((a, b) -> Integer.compare(a[0], b[0]));

        int[] countPerToken = new int[queryTokens.size()];
        int distinctCovered = 0;
        int left = 0;
        int minGap = NO_MATCH;

        for (int[] ints : tagged) {
            int tokenIdx = ints[1];
            if (countPerToken[tokenIdx]++ == 0)
                distinctCovered++;

            while (distinctCovered == queryTokens.size()) {
                int gap = ints[0] - tagged.get(left)[0];
                minGap = Math.min(minGap, gap);

                int leftTokenIdx = tagged.get(left)[1];
                if (--countPerToken[leftTokenIdx] == 0)
                    distinctCovered--;
                left++;
            }
        }

        return minGap;
    }

}