package redis;

import etl.CommandQueue;
import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import java.nio.ByteBuffer;
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

    public void routeToRedis(Object2ObjectOpenHashMap<String, LongArrayList> UniqueTokens, UUID[] UUIDs) throws InterruptedException {
        int estimatedSize = (int) Math.ceil((UniqueTokens.size() / (double) shards.length) * 1.33);
        List<Map<String, ByteArrayList>> batches = new ArrayList<>(shards.length);

        // Sizes the hashMap to the size of dict divided by n containers plus a little extra
        for (int i = 0; i < shards.length; i++)
            batches.add(new HashMap<>(estimatedSize));

        // Loops each row
        for (Map.Entry<String, LongArrayList> entry : UniqueTokens.entrySet()) {
            int shardInstance = TokenHasher.hash(entry.getKey(), shards.length); // Hashing based on token

            LongArrayList unpacked = entry.getValue(); // Document index + term frequency
            ByteArrayList postings = new ByteArrayList(unpacked.size() * 20);

            byte[] scratch = new byte[20];
            ByteBuffer buf = ByteBuffer.wrap(scratch);

            // Matches docIndex to UUIDs array
            for (int i = 0; i < unpacked.size(); i++) {
                long packedVal = unpacked.getLong(i);
                int docIndex = unpackDocIndex(packedVal);
                UUID uuid = UUIDs[docIndex];
                int tf = unpackTf(packedVal);

                buf.clear();
                buf.putLong(uuid.getMostSignificantBits());
                buf.putLong(uuid.getLeastSignificantBits());
                buf.putInt(tf);

                postings.addElements(postings.size(), scratch);
            }

            batches.get(shardInstance).put(entry.getKey(), postings);
        }

        UniqueTokens.clear(); // Clearing because its already copied to batches

        // Routes the hashed token into their respective shards
        for (int i = 0; i < shards.length; i++) {
            Map<String, ByteArrayList> subBatch = batches.get(i);

            if (!subBatch.isEmpty())
                shards[i].queueBatch(subBatch);
        }
    }

    private static int unpackDocIndex(long packed) {
        return (int) (packed >>> 32);
    }

    private static int unpackTf(long packed) {
        return (int) packed;
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

                    for (Map.Entry<String, ByteArrayList> entry : cmd.batch().entrySet()) {
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

        public void queueBatch(Map<String, ByteArrayList> batch) throws InterruptedException {
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