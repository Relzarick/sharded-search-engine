package redis;

import etl.CommandQueue;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import search.PositionalPosting;

import java.util.*;
import java.util.concurrent.*;

public class RedisShardRouter implements AutoCloseable {
    private final Writer[] shards;

    public RedisShardRouter() {
        shards = new Writer[]{
                new Writer(new RedisService("r1")),
                new Writer(new RedisService("r2")),
                new Writer(new RedisService("r3")),
                new Writer(new RedisService("r4"))
        };
    }

    public void routeToRedis(Object2ObjectOpenHashMap<String, List<InternalPosting>> tokensToPosting, UUID[] UUIDs) throws InterruptedException {
        int estimatedSize = (int) Math.ceil((tokensToPosting.size() / (double) shards.length) * 1.33);
        List<Map<String, List<PositionalPosting>>> batches = new ArrayList<>(shards.length);

        // Sizes the hashMap to the size of term divided by n containers plus a little extra
        for (int i = 0; i < shards.length; i++)
            batches.add(new HashMap<>(estimatedSize));

        // Loops each row
        for (Map.Entry<String, List<InternalPosting>> entry : tokensToPosting.entrySet()) {
            int shardInstance = TokenHasher.hash(entry.getKey(), shards.length); // Hashing based on token

            List<PositionalPosting> postings = new ArrayList<>(entry.getValue().size());

            for (InternalPosting post : entry.getValue())
                postings.add(new PositionalPosting(UUIDs[post.docIndex()], post.positions()));

            batches.get(shardInstance).put(entry.getKey(), postings);
        }

        tokensToPosting.clear(); // Clearing because its already copied to batches

        // Routes the hashed token into their respective shards
        for (int i = 0; i < shards.length; i++) {
            Map<String, List<PositionalPosting>> subBatch = batches.get(i);

            if (!subBatch.isEmpty())
                shards[i].queueBatch(subBatch);
        }
    }

    @Override
    public void close() {
        for (Writer w : shards)
            w.closeThreads();
    }

    private static class Writer {
        private final Index redis;
        private static final int FLUSH_THRESHOLD = 1000;

        private final BlockingQueue<CommandQueue> queue = new ArrayBlockingQueue<>(10);
        private final ExecutorService executor = Executors.newSingleThreadExecutor();

        public Writer(Index instance) {
            redis = instance;
            executor.submit(this::loop);
        }

        private void loop() {
            int count = 0;

            try {
                while (!Thread.currentThread().isInterrupted()) {
                    CommandQueue item = queue.take();

                    if (item instanceof CommandQueue.PoisonPill) {
                        if (count > 0)
                            redis.flush();

                        break;
                    }

                    CommandQueue.Commands cmd = (CommandQueue.Commands) item;

                    for (Map.Entry<String, List<PositionalPosting>> entry : cmd.batch().entrySet()) {
                        redis.set(entry.getKey(), entry.getValue());
                        count++;

                        if (count >= FLUSH_THRESHOLD) {
                            redis.flush();
                            count = 0;
                        }
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                redis.close();
            }
        }

        public void queueBatch(Map<String, List<PositionalPosting>> batch) throws InterruptedException {
            queue.put(new CommandQueue.Commands(batch));
        }

        public void closeThreads() {
            try {
                queue.put(new CommandQueue.PoisonPill());
                executor.shutdown();

                if (!executor.awaitTermination(60, TimeUnit.SECONDS))
                    executor.shutdownNow();
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

    }

}