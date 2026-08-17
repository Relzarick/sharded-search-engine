package rocks;

import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Per shard writer, starts a background thread on construction to submit tasks to index
 */
class Submissioner {
    private final RocksService index;
    private static final Logger logger = LoggerFactory.getLogger(Submissioner.class);

    private final BlockingQueue<RouterQueueItem> queue = new LinkedBlockingQueue<>(10);
    private final ExecutorService service = Executors.newSingleThreadExecutor();

    Submissioner(RocksService index) {
        this.index = index;
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

                    if (item instanceof RouterQueueItem.PoisonPill)
                        break;

                    index.write(item);
                } catch (InterruptedException | RocksDBException e) {
                    logger.error("ROCKSDB ERRROR: failed to write");
                    throw new RuntimeException(e);
                }
            }
        });
    }

    // collect futures and block compact
    void compactThenClose() throws RocksDBException {
        index.ingest();
        index.compact();
        service.shutdown();
        index.close();
    }

}