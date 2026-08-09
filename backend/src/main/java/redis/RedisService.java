package redis;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import it.unimi.dsi.fastutil.bytes.ByteArrayList;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class RedisService implements Index {
    private final RedisClient client;
    private static final RedisCodec<Object, byte[]> CODEC = new IndexCodec();
    private final StatefulRedisConnection<Object, byte[]> connection;
    private final RedisAsyncCommands<Object, byte[]> async;

    public RedisService(String host) {
        client = RedisClient.create(RedisURI.Builder.redis(host, 6379).build());
        connection = client.connect(CODEC);
        connection.setAutoFlushCommands(false);
        async = connection.async();
    }

    @Override
    public void set(String key, ByteArrayList postings) {
        int count = postings.size() / 20;
        byte[][] members = new byte[count][];

        for (int i = 0; i < count; i++) {
            members[i] = new byte[20];
            postings.getElements(i * 20, members[i], 0, 20);
        }

        async.sadd(key, members);
    }

    @Override
    public void flush() {
        RedisFuture<String> barrier = async.ping();
        connection.flushCommands();

        try {
            barrier.get(10, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            throw new RuntimeException("Redis pipeline execution failed: " + e.getCause().getMessage(), e);
        } catch (TimeoutException e) {
            throw new RuntimeException("Redis pipeline timed out. Redis is overloaded.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Redis pipeline interrupted.", e);
        }
    }

    @Override
    public List<Posting> retrieve(String key) throws ExecutionException, InterruptedException {
        RedisFuture<Set<byte[]>> future = async.smembers(key);
        connection.flushCommands();

        Set<byte[]> raw = future.get();
        List<Posting> postings = new ArrayList<>(raw.size());

        for (byte[] b : raw) {
            ByteBuffer buf = ByteBuffer.wrap(b);
            long msb = buf.getLong();
            long lsb = buf.getLong();
            int tf = buf.getInt();
            postings.add(new Posting(new UUID(msb, lsb), tf));
        }

        return postings;
    }

    @Override
    public void close() {
        connection.close();
        client.close();
    }

    /**
     * Codec handles mixed key types (String tokens, UUID fallback) and
     * packs Posting values into a fixed 20-byte layout: 16-byte UUID + 4-byte int TF.
     */
    private static class IndexCodec implements RedisCodec<Object, byte[]> {
        private static final int VALUE_SIZE = 20;
        private final StringCodec stringCodec = StringCodec.UTF8;

        @Override
        public ByteBuffer encodeKey(Object key) {
            if (key instanceof UUID uuid) {
                ByteBuffer buffer = ByteBuffer.allocate(16);
                buffer.putLong(uuid.getMostSignificantBits());
                buffer.putLong(uuid.getLeastSignificantBits());
                buffer.flip();
                return buffer;
            }
            return stringCodec.encodeKey((String) key);
        }

        @Override
        public Object decodeKey(ByteBuffer bytes) {
            if (bytes != null && bytes.remaining() == 16) {
                long msb = bytes.getLong(bytes.position());
                long lsb = bytes.getLong(bytes.position() + 8);

                int version = (int) ((msb >>> 12) & 0x0F);
                int variant = (int) ((lsb >>> 62) & 0x03);

                if (version == 7 && variant == 2) {
                    bytes.position(bytes.position() + 16);
                    return new UUID(msb, lsb);
                }
            }
            return stringCodec.decodeKey(bytes);
        }

        @Override
        public ByteBuffer encodeValue(byte[] value) {
            return ByteBuffer.wrap(value);
        }

        @Override
        public byte[] decodeValue(ByteBuffer bytes) {
            byte[] value = new byte[VALUE_SIZE];
            bytes.get(value);
            return value;
        }

    }

}