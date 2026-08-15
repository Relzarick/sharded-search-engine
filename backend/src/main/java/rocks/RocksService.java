package rocks;

import etl.CommandQueue;
import org.rocksdb.*;
import search.PositionalPosting;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

public class RocksService implements AutoCloseable {
    Options options;
    RocksDB db;

    WriteOptions writeOptions;
    static byte SEP = 0x00;

    public RocksService() throws RocksDBException {
        options = new Options().setCreateIfMissing(true);
//                .setIncreaseParallelism(4);

        db = RocksDB.open(options, "/app/data/index");
        writeOptions = new WriteOptions().setDisableWAL(true);
    }

    public void set(CommandQueue.Commands data) throws RocksDBException {
        try (WriteBatch batch = new WriteBatch()) {
            for (Map.Entry<String, List<PositionalPosting>> entry : data.batch().entrySet()) {
                byte[] token = entry.getKey().getBytes();

                for (PositionalPosting posting : entry.getValue()) {
                    UUID uuid = posting.uuid();

                    // Token + SEP + UUID
                    ByteBuffer buf = ByteBuffer.allocate(token.length + 1 + 16);

                    buf.put(token);
                    buf.put(SEP);
                    buf.putLong(uuid.getMostSignificantBits());
                    buf.putLong(uuid.getLeastSignificantBits());

                    byte[] key = buf.array();

                    int[] positions = posting.positions().toIntArray();

                    // Count + Posistions
                    ByteBuffer pBuf = ByteBuffer.allocate(4 + positions.length * 4);

                    pBuf.putInt(positions.length);
                    pBuf.asIntBuffer().put(positions);

                    byte[] value = pBuf.array();

                    batch.put(key, value);
                }
            }

            db.write(writeOptions, batch);
        }
    }

    public List<PositionalPosting> retrieve(String key) throws ExecutionException, InterruptedException {
        return List.of();
    }

    @Override
    public void close() {
        db.close();
    }

}