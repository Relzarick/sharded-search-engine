package etl;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.bson.BsonDocument;
import redis.InternalPosting;

import java.util.List;
import java.util.UUID;

public sealed interface QueueItem {
    record DocumentBatch(List<BsonDocument> documents) implements QueueItem {
    }

    /**
     *
     * @param term Contains the token, its UUID index and positions
     */
    record IndexerBatch(Object2ObjectOpenHashMap<String, List<InternalPosting>> term,
                        UUID[] uuids) implements QueueItem {
    }

    record PoisonPill() implements QueueItem {
    }

}