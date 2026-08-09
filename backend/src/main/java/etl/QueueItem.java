package etl;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.bson.BsonDocument;

import java.util.List;
import java.util.UUID;

public sealed interface QueueItem {
    record DocumentBatch(List<BsonDocument> documents) implements QueueItem {
    }

    record IndexerBatch(Object2ObjectOpenHashMap<String, LongArrayList> dict, UUID[] docIds) implements QueueItem {
    }

    record PoisonPill() implements QueueItem {
    }

}