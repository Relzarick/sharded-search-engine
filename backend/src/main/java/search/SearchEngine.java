package search;

import indexer.InvertedIndexer;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import mongo.DocumentResults;
import mongo.Repository;
import org.bson.BsonDocument;
import org.bson.UuidRepresentation;
import org.rocksdb.RocksDBException;

import java.io.IOException;
import java.util.*;

public class SearchEngine {
    private final Repository mongo;
    private final InvertedIndexer indexer;
    private final IndexReader query = new IndexReader();

    private static final double PROXIMITY_WEIGHT = 5.0;
    private static final double PHRASE_WEIGHT = 2.0;
    private static final int NO_MATCH = Integer.MAX_VALUE;

    public SearchEngine(Repository db, InvertedIndexer idx) throws RocksDBException, IOException {
        mongo = db;
        indexer = idx;
    }

    public String search(String input, int offset, int size) {
        List<String> cleanedInput = indexer.tokenizeKeyWords(input);

        QueryResult queryResult = query.fetchFromIndex(cleanedInput);
        long totalDoc = mongo.getCollection().estimatedDocumentCount();

        if (queryResult.docTermPos().isEmpty())
            return new DocumentResults(List.of()).jsonify(0);

        List<UUID> rankedResults = rankResults(queryResult, cleanedInput, totalDoc, offset, size);
        List<BsonDocument> unordered = mongo.fetchMany(rankedResults).documents();
        List<BsonDocument> ordered = reorder(unordered, rankedResults);

        return new DocumentResults(ordered).jsonify(queryResult.docTermPos().size());
    }

    private List<BsonDocument> reorder(List<BsonDocument> documents, List<UUID> rankedOrder) {
        Map<UUID, BsonDocument> byId = new HashMap<>((int) (documents.size() / 0.75) + 1);

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
        Object2DoubleOpenHashMap<UUID> tokenScore = new Object2DoubleOpenHashMap<>();

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

        List<Object2DoubleMap.Entry<UUID>> entries = new ArrayList<>(tokenScore.object2DoubleEntrySet());
        entries.sort((a, b) -> Double.compare(b.getDoubleValue(), a.getDoubleValue()));

        if (offset >= entries.size())
            return List.of();

        int end = Math.min(offset + size, entries.size());

        List<UUID> keys = new ArrayList<>(end - offset);

        for (Object2DoubleMap.Entry<UUID> entry : entries.subList(offset, end))
            keys.add(entry.getKey());

        return keys;
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

        // 2. No exact phrase — fall back to general min-gap (order-agnostic proximity).
        // Smallest range containing at least one position from every query token's list.
        int totalPositions = 0;
        for (String token : queryTokens) {
            IntArrayList positions = tokenPosition.get(token);
            if (positions == null)
                return NO_MATCH; // token missing entirely for this doc — shouldn't happen post-intersection, but defensive
            totalPositions += positions.size();
        }

        long[] tagged = new long[totalPositions];
        int idx = 0;

        for (int i = 0; i < queryTokens.size(); i++) {
            IntArrayList positions = tokenPosition.get(queryTokens.get(i));
            for (int p : positions)
                tagged[idx++] = ((long) p << 32) | (i & 0xFFFFFFFFL);
        }

        Arrays.sort(tagged); // sorts by position first since it occupies the high bits

        int[] countPerToken = new int[queryTokens.size()];
        int distinctCovered = 0;
        int left = 0;
        int minGap = NO_MATCH;

        for (long entry : tagged) {
            int position = (int) (entry >>> 32);
            int tokenIdx = (int) entry;

            if (countPerToken[tokenIdx]++ == 0)
                distinctCovered++;

            while (distinctCovered == queryTokens.size()) {
                int leftPosition = (int) (tagged[left] >>> 32);
                int gap = position - leftPosition;
                minGap = Math.min(minGap, gap);

                int leftTokenIdx = (int) tagged[left];
                if (--countPerToken[leftTokenIdx] == 0)
                    distinctCovered--;
                left++;
            }
        }

        return minGap;
    }

}