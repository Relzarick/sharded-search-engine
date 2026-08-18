package rocks;

import etl.InternalPosting;
import etl.TokenHasher;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.rocksdb.RocksDBException;

import java.io.IOException;
import java.util.*;

public class RocksRouter {
    private final Submissioner[] shards;

    private static final Comparator<PositionalPosting> UUID_CMP = Comparator.comparing(PositionalPosting::uuid);

    public RocksRouter() throws RocksDBException, IOException {
        shards = new Submissioner[]{
                new Submissioner("shard-0"),
                new Submissioner("shard-1")
        };
    }

    /**
     * Routes to each shard using consistent hashing.
     * TreeMap over hashMap is used because RocksDB needs data to be sorted, will lose much time if data is random
     */
    public void routeTo(Object2ObjectOpenHashMap<String, List<InternalPosting>> tokensToPosting, UUID[] UUIDs) throws InterruptedException {
        List<TreeMap<String, List<PositionalPosting>>> shardedBatch = new ArrayList<>(shards.length);

        for (int i = 0; i < shards.length; i++)
            shardedBatch.add(new TreeMap<>());

        for (Map.Entry<String, List<InternalPosting>> entry : tokensToPosting.entrySet()) {
            List<PositionalPosting> postings = new ArrayList<>(entry.getValue().size());
            int shardIndex = TokenHasher.hash(entry.getKey(), shards.length);

            for (InternalPosting post : entry.getValue())
                postings.add(new PositionalPosting(UUIDs[post.docIndex()], post.positions()));

            postings.sort(UUID_CMP);
            shardedBatch.get(shardIndex).put(entry.getKey(), postings);
        }

        for (int i = 0; i < shards.length; i++)
            shards[i].queueBatch(new RouterQueueItem.IndexBatch(shardedBatch.get(i)));
    }

    /**
     * Initiates cleanup procedures for the index. This is ran in the background
     */
    public void shutdown() {
        for (Submissioner shard : shards) {
            try {
                shard.queueBatch(new RouterQueueItem.PoisonPill());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while queueing poison pill", e);
            }
        }

        for (Submissioner shard : shards)
            shard.closeThread();
    }

}