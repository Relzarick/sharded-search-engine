package rocks;

import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Per shard writer, starts a background thread on construction to submit tasks to index
 */
class Submissioner {
    private final RocksService index;
    private final String pathName;
    private int fileCount = 0;

    private static final Logger logger = LoggerFactory.getLogger(Submissioner.class);

    private final BlockingQueue<RouterQueueItem> queue = new LinkedBlockingQueue<>(10);
    private final ExecutorService service = Executors.newSingleThreadExecutor();

    Submissioner(String shardName) throws RocksDBException, IOException {
        index = new RocksService(shardName);
        pathName = shardName + '/';

        startSubmit();
    }

    void queueBatch(RouterQueueItem batch) throws InterruptedException {
        queue.put(batch);
    }

    void startSubmit() {
        service.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    RouterQueueItem item = queue.take();

                    if (item instanceof RouterQueueItem.PoisonPill) {
                        compactThenClose();
                        break;
                    }

                    index.write(item, pathName + fileCount);
                    fileCount++;
                } catch (InterruptedException | RocksDBException e) {
                    logger.error("ROCKSDB ERRROR: failed to write");
                    throw new RuntimeException(e);
                }
            }
        });
    }

    void compactThenClose() {
        try {
            index.ingest();
            index.compact();
            index.clearnupTmp(pathName);
            index.close();

            Files.deleteIfExists(Paths.get("index/tmp/"));
            service.shutdown();
        } catch (RocksDBException e) {
            logger.error("ROCKSDB ERROR: Failed ingestion/compaction");
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            logger.error("IO ERROR: Failed to delete tmp folder");
            Thread.currentThread().interrupt();
        }
    }

}