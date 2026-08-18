package rocks;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.rocksdb.*;
import search.PositionalPosting;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class RocksService {
    private final RocksDB db;
    private final EnvOptions envOptions = new EnvOptions();
    private final Options sstOptions = new Options().setCompressionType(CompressionType.LZ4_COMPRESSION);
    private final IngestExternalFileOptions ingestOptions = new IngestExternalFileOptions().setMoveFiles(true);

    private final List<String> tmpFiles = new ArrayList<>();
    private final String tmpPath = "index/tmp/";

    private static final byte SEP = 0x00;
    private final ByteBuffer keyBuf = ByteBuffer.allocateDirect(1024);
    private final ByteBuffer valBuf = ByteBuffer.allocateDirect(1024 * 1024);

    static {
        RocksDB.loadLibrary();
    }

    public RocksService(String name) throws RocksDBException, IOException {
        Options dbOptions = new Options()
                .setCreateIfMissing(true)
                .prepareForBulkLoad()
                .setCompressionType(CompressionType.LZ4_COMPRESSION);

        Files.createDirectories(Paths.get(tmpPath + name));
        db = RocksDB.open(dbOptions, "index/" + name);
    }

    public void write(RouterQueueItem queueItem, String tmpFilePath) throws RocksDBException {
        String filePath = tmpPath + tmpFilePath;

        try (SstFileWriter writer = new SstFileWriter(envOptions, sstOptions)) {
            writer.open(filePath);

            RouterQueueItem.IndexBatch batch = (RouterQueueItem.IndexBatch) queueItem;
            for (Map.Entry<String, List<PositionalPosting>> item : batch.batch().entrySet()) {
                byte[] token = item.getKey().getBytes(StandardCharsets.UTF_8);

                for (PositionalPosting posting : item.getValue()) {
                    UUID uuid = posting.uuid();
                    IntArrayList positions = posting.positions();

                    keyBuf.clear();
                    valBuf.clear();

                    keyBuf.put(token).put(SEP);
                    keyBuf.putLong(uuid.getMostSignificantBits());
                    keyBuf.putLong(uuid.getLeastSignificantBits());
                    keyBuf.flip();

                    int[] rawArray = positions.elements();
                    int count = positions.size();

                    for (int i = 0; i < count; i++)
                        valBuf.putInt(rawArray[i]);

                    valBuf.flip();

                    writer.put(keyBuf, valBuf);
                }
            }

            writer.finish();
            tmpFiles.add(filePath);
        }
    }

    public List<PositionalPosting> retrieve(String input) {
        byte[] token = input.getBytes(StandardCharsets.UTF_8);

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

    public void ingestAndCompact() throws RocksDBException {
        db.ingestExternalFile(tmpFiles, ingestOptions);
        db.compactRange(null, null);
    }

    public void close() {
        db.close();
    }

}