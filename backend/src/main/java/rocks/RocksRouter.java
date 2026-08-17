package rocks;

import etl.TokenHasher;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.rocksdb.RocksDBException;
import redis.InternalPosting;
import search.PositionalPosting;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RocksRouter {
    private final Submissioner[] shards;
    private final ExecutorService compactionService;

    public RocksRouter() throws RocksDBException, IOException {
        shards = new Submissioner[]{
                new Submissioner(new RocksService("shard-0")),
                new Submissioner(new RocksService("shard-1"))
        };

        compactionService = Executors.newFixedThreadPool(shards.length);
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

            postings.sort(Comparator.comparing(PositionalPosting::uuid));
            shardedBatch.get(shardIndex).put(entry.getKey(), postings);
        }

        tokensToPosting.clear(); // Small memory save

        for (int i = 0; i < shards.length; i++)
            shards[i].queueBatch(new RouterQueueItem.IndexBatch(shardedBatch.get(i)));
    }

    public void asyncCompactThenClose() {
        for (int i = 0; i < shards.length; i++) {
            final int index = i;

            compactionService.submit(() -> {
                try {
                    shards[index].compactThenClose();
                } catch (RocksDBException e) {
                    throw new RuntimeException(e);
                }
            });
        }

        compactionService.shutdown();
    }

}