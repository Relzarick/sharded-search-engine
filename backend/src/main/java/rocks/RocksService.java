package rocks;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.rocksdb.*;
import search.PositionalPosting;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class RocksService {
    private final RocksDB db;
    private final WriteOptions writeOptions;

    private static final byte SEP = 0x00;
    private final ByteBuffer keyBuf = ByteBuffer.allocateDirect(1024);
    private final ByteBuffer valBuf = ByteBuffer.allocateDirect(1024 * 1024);

    public RocksService(String path) throws RocksDBException {
        Options options = new Options()
                .setCreateIfMissing(true)
                .prepareForBulkLoad();

        db = RocksDB.open(options, path);
        writeOptions = new WriteOptions().setDisableWAL(true);
    }

    public void set(RouterQueueItem queueItem) throws RocksDBException {
        try (WriteBatch wb = new WriteBatch()) {
            RouterQueueItem.IndexBatch batch = (RouterQueueItem.IndexBatch) queueItem;

            for (Map.Entry<String, List<PositionalPosting>> item : batch.batch().entrySet()) {
                byte[] token = item.getKey().getBytes();

                for (PositionalPosting posting : item.getValue()) {
                    UUID uuid = posting.uuid();
                    int[] positions = posting.positions().toIntArray();

                    keyBuf.clear();
                    valBuf.clear();

                    keyBuf.put(token).put(SEP);
                    keyBuf.putLong(uuid.getMostSignificantBits());
                    keyBuf.putLong(uuid.getLeastSignificantBits());
                    keyBuf.flip();

                    valBuf.asIntBuffer().put(positions);
                    valBuf.limit(positions.length * Integer.BYTES); // cap to exact bytes, no overflow

                    wb.put(keyBuf, valBuf);
                }
            }

            db.write(writeOptions, wb);
        }
    }

    public void compact() throws RocksDBException {
        db.compactRange(null, null);
    }

    public List<PositionalPosting> retrieve(String input) {
        byte[] token = input.getBytes();

        // TOKEN + SEP
        ByteBuffer buf = ByteBuffer.allocate(token.length + 1);
        buf.put(token).put(SEP);

        byte[] prefix = buf.array();
        List<PositionalPosting> results = new ArrayList<>();

        try (RocksIterator iter = db.newIterator()) {
            for (iter.seek(prefix); iter.isValid(); iter.next()) {
                byte[] key = iter.key();

                if (!checkPrefix(key, prefix))
                    break;

                ByteBuffer keyBuf = ByteBuffer.wrap(key, prefix.length, key.length - prefix.length);
                long msb = keyBuf.getLong();
                long lsb = keyBuf.getLong();
                UUID uuid = new UUID(msb, lsb);

                IntBuffer intBuf = ByteBuffer.wrap(iter.value()).asIntBuffer();
                int[] pos = new int[intBuf.remaining()];
                intBuf.get(pos);

                results.add(new PositionalPosting(uuid, new IntArrayList(pos)));
            }
        }

        return results;
    }

    /**
     * Ensures that key matches the prefix
     */
    private boolean checkPrefix(byte[] key, byte[] prefix) {
        if (key.length < prefix.length)
            return false;

        for (int i = 0; i < prefix.length; i++)
            if (key[i] != prefix[i])
                return false;

        return true;
    }

    public void close() {
        db.close();
    }

}