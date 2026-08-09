package indexer;

import etl.QueueItem;
import indexer.tokenizer.TokenStrategy;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.BsonValue;

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
        Object2ObjectOpenHashMap<String, LongArrayList> uniqueTokens = new Object2ObjectOpenHashMap<>(262144);
        Object2IntOpenHashMap<String> tokenCountPerDoc = new Object2IntOpenHashMap<>(256); // Stores term frequency

        List<BsonDocument> docs = from.documents();
        int batchSize = docs.size();

        UUID[] docIds = new UUID[batchSize];
        int docIndex = 0; // Array index corresponding to docIds

        for (BsonDocument doc : docs) {
            docIds[docIndex] = doc.getBinary("_id").asUuid(); // Mapping the UUIDs to an array

            tokenCountPerDoc.clear(); // clear for the next batch

            // Loops each field in the individual docuemnts
            for (Map.Entry<String, BsonValue> field : doc.entrySet()) {
                if (field.getKey().equals("_id"))
                    continue;

                if (field.getValue() instanceof BsonString str) {
                    for (String valid : tk.toTokens(str.getValue()))
                        tokenCountPerDoc.addTo(valid, 1);
                }
            }

            // Contains token : document index + TF
            for (Object2IntMap.Entry<String> entry : tokenCountPerDoc.object2IntEntrySet()) {
                String token = entry.getKey();
                int tf = entry.getIntValue();

                uniqueTokens.computeIfAbsent(token, k -> new LongArrayList()).add(pack(docIndex, tf));
            }

            docIndex++;
        }

        to.put(new QueueItem.IndexerBatch(uniqueTokens, docIds));
    }

    public List<String> tokenizeKeyWords(String input) {
        return tk.toTokens(input);
    }

    // Stores the document index into the first half and the term frequency into the second
    private static long pack(int docIndex, int tf) {
        return ((long) docIndex << 32) | (tf & 0xFFFFFFFFL);
    }

}