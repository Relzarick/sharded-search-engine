package indexer;

import etl.InternalPosting;
import etl.QueueItem;
import indexer.tokenizer.TokenStrategy;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.BsonValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;

public final class InvertedIndexer {
    private final TokenStrategy tk;

    public InvertedIndexer(TokenStrategy strategy) {
        tk = strategy;
    }

    public void tokenizeToQueue(QueueItem.DocumentBatch from, BlockingQueue<QueueItem> to) throws InterruptedException {
        Object2ObjectOpenHashMap<String, List<InternalPosting>> tokenToPostings = new Object2ObjectOpenHashMap<>(262144);

        List<BsonDocument> docs = from.documents();
        int batchSize = docs.size();

        UUID[] uuidList = new UUID[batchSize];

        // Index corresponds to uuidList
        for (int docIndex = 0; docIndex < batchSize; docIndex++) {
            BsonDocument doc = docs.get(docIndex);
            uuidList[docIndex] = doc.getBinary("_id").asUuid(); // Mapping the UUIDs to an array

            int posInDoc = 0;

            // Collect all tokens for the entire document
            for (Map.Entry<String, BsonValue> field : doc.entrySet()) {
                if (field.getKey().equals("_id"))
                    continue;

                if (field.getValue() instanceof BsonString str) {
                    for (String token : tk.toTokens(str.getValue())) {
                        List<InternalPosting> postings = tokenToPostings.computeIfAbsent(token, k -> new ArrayList<>());
                        InternalPosting last = postings.isEmpty() ? null : postings.getLast();

                        // Check if there is already a posting for the CURRENT document
                        if (last != null && last.docIndex() == docIndex) {
                            last.positions().add(posInDoc);
                        } else { // For FIRST occurence, create a new Posting record
                            IntArrayList positions = new IntArrayList(2);
                            positions.add(posInDoc);
                            postings.add(new InternalPosting(docIndex, positions));
                        }

                        posInDoc++;
                    }
                }
            }
        }

        // Mapping multiple ints to UUID[] is cheaper than String : UUID
        to.put(new QueueItem.IndexerBatch(tokenToPostings, uuidList));
    }

    /**
     * This is used for search queries
     *
     * @return The List of valid Tokens
     */
    public List<String> tokenizeKeyWords(String input) {
        return tk.toTokens(input);
    }

}