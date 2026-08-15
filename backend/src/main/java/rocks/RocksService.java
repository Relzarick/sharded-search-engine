package rocks;

import etl.CommandQueue;
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
    Options options;
    RocksDB db;

    WriteOptions writeOptions;
    static byte SEP = 0x00;

    public RocksService() throws RocksDBException {
        options = new Options().setCreateIfMissing(true)
                .prepareForBulkLoad()
                .setIncreaseParallelism(2);

        db = RocksDB.open(options, "index");
        writeOptions = new WriteOptions().setDisableWAL(true);
    }

    public void set(List<CommandQueue.Commands> data) throws RocksDBException {
        try (WriteBatch batch = new WriteBatch()) {
            for (CommandQueue.Commands cmd : data) {
                for (Map.Entry<String, List<PositionalPosting>> entry : cmd.batch().entrySet()) {
                    byte[] token = entry.getKey().getBytes();

                    for (PositionalPosting posting : entry.getValue()) {
                        UUID uuid = posting.uuid();

                        // TOKEN + SEP + UUID
                        ByteBuffer buf = ByteBuffer.allocate(token.length + 1 + 16);

                        buf.put(token).put(SEP);
                        buf.putLong(uuid.getMostSignificantBits());
                        buf.putLong(uuid.getLeastSignificantBits());

                        byte[] key = buf.array();

                        int[] positions = posting.positions().toIntArray();

                        // POSITIONS
                        ByteBuffer posBuf = ByteBuffer.allocate(positions.length * 4);
                        posBuf.asIntBuffer().put(positions);

                        byte[] value = posBuf.array();

                        batch.put(key, value);
                    }
                }
            }

            db.write(writeOptions, batch);
        }
    }

    public void compact() throws RocksDBException {
        db.compactRange(null, null);
    }

    public void test() {
        try {
            long estimatedKeys = db.getLongProperty("rocksdb.estimate-num-keys");
            System.out.println("Estimated total keys: " + estimatedKeys);
        } catch (RocksDBException e) {
            e.printStackTrace();
        }
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