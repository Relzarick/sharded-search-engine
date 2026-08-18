package etl;

import bootstrap.ConfigLoader;
import etl.parser.CsvParser;
import indexer.InvertedIndexer;
import mongo.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rocks.RocksRouter;

import java.io.IOException;
import java.util.concurrent.*;

/**
 * Running a producer-consumer pattern here.
 * <br><br>
 * Parser > Mongo/Indexer > Router > Index
 */
public final class CreateWorkers {
    private static final Logger logger = LoggerFactory.getLogger(CreateWorkers.class);

    private final int PARSER_TC = ConfigLoader.getInt("parser.threadCount", "1");
    private final int INDEXER_TC = ConfigLoader.getInt("indexer.threadCount", "1");
    private final int MONGO_TC = ConfigLoader.getInt("mongo.threadCount", "1");
    private final int ROUTER_TC = ConfigLoader.getInt("router.threadCount", "1");

    private final BlockingQueue<QueueItem> mongoQueue = new LinkedBlockingQueue<>(5);
    private final BlockingQueue<QueueItem> indexerQueue = new ArrayBlockingQueue<>(10);
    private final BlockingQueue<QueueItem> routerQueue = new ArrayBlockingQueue<>(10);

    private final ExecutorService parserThreadPool = Executors.newFixedThreadPool(PARSER_TC);
    private final ExecutorService indexerThreadPool = Executors.newFixedThreadPool(INDEXER_TC);
    private final ExecutorService mongoThreadPool = Executors.newFixedThreadPool(MONGO_TC);
    private final ExecutorService routerThreadPool = Executors.newFixedThreadPool(ROUTER_TC);

    private record Target(BlockingQueue<QueueItem> queue, int pillCount) {
    }

    /**
     * This method throws unchecked exceptions to caller.
     *
     * @throws CancellationException if the computation was cancelled
     * @throws CompletionException   if this future completed
     */
    public void run(CsvParser parser, Repository db, InvertedIndexer indexer, RocksRouter router) {
        CompletableFuture<Void> producers = runProducers(parser, indexer);
        CompletableFuture<Void> consumers = runConsumers(db, router);

        CompletableFuture.allOf(producers, consumers).join();
    }

    /**
     * Parser threadPool feeds two queues: mongo and indexer
     * Indexer threadPool takes from the queue and feeds redis
     */
    private CompletableFuture<Void> runProducers(CsvParser parser, InvertedIndexer indexer) {
        CompletableFuture<?>[] parserFutures = new CompletableFuture<?>[PARSER_TC];
        CompletableFuture<?>[] indexerFutures = new CompletableFuture<?>[INDEXER_TC];

        for (int i = 0; i < PARSER_TC; i++) {
            final int index = i;

            parserFutures[i] = CompletableFuture.runAsync(() -> {
                try {
                    int[] range = parser.getPageRange(index, PARSER_TC);
                    parser.parseDataTo(mongoQueue, indexerQueue, range[0], range[1]);
                } catch (IOException | InterruptedException e) {
                    throw new CompletionException(e);
                }
            }, parserThreadPool);
        }

        for (int i = 0; i < INDEXER_TC; i++) {
            indexerFutures[i] = CompletableFuture.runAsync(() -> {
                try {
                    while (true) {
                        QueueItem item = indexerQueue.take();

                        if (item instanceof QueueItem.PoisonPill)
                            break;

                        indexer.tokenizeToQueue((QueueItem.DocumentBatch) item, routerQueue);
                    }
                } catch (Exception e) {
                    throw new CompletionException(e);
                }
            }, indexerThreadPool);
        }

        CompletableFuture<Void> allParser = insertPoisonPills(parserFutures, new Target(mongoQueue, MONGO_TC), new Target(indexerQueue, INDEXER_TC));
        CompletableFuture<Void> allIndexer = insertPoisonPills(indexerFutures, new Target(routerQueue, ROUTER_TC));

        return CompletableFuture.allOf(allParser, allIndexer).whenComplete((result, throwable) -> {
            parserThreadPool.shutdown();
            indexerThreadPool.shutdown();
        });
    }

    private CompletableFuture<Void> insertPoisonPills(CompletableFuture<?>[] futures, Target... targets) {
        return CompletableFuture.allOf(futures).whenComplete((result, throwable) -> {
            if (throwable == null) {
                try {
                    for (Target target : targets) {
                        for (int i = 0; i < target.pillCount(); i++)
                            target.queue().put(new QueueItem.PoisonPill());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    abortIngestion("Interrupted while queueing poison pills");
                    throw new CompletionException(e);
                }
            } else
                abortIngestion("Producer pool failed: " + throwable);
        });
    }

    /**
     * Consumers will only do insertions
     */
    private CompletableFuture<Void> runConsumers(Repository db, RocksRouter router) {
        CompletableFuture<?>[] mongoFuturesArray = new CompletableFuture<?>[MONGO_TC];
        CompletableFuture<?>[] routerFuturesArray = new CompletableFuture<?>[ROUTER_TC];

        for (int i = 0; i < MONGO_TC; i++) {
            mongoFuturesArray[i] = CompletableFuture.runAsync(() -> {
                try {
                    while (true) {
                        QueueItem item = mongoQueue.take();

                        if (item instanceof QueueItem.PoisonPill)
                            break;

                        QueueItem.DocumentBatch batch = (QueueItem.DocumentBatch) item;
                        db.insert(batch.documents());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    abortIngestion("Mongo consumer interrupted");
                    throw new CompletionException(e);
                } catch (Exception e) {
                    abortIngestion("Mongo consumer failed: " + e);
                    throw new CompletionException(e);
                }
            }, mongoThreadPool);
        }

        for (int i = 0; i < ROUTER_TC; i++) {
            routerFuturesArray[i] = CompletableFuture.runAsync(() -> {
                try {
                    while (true) {
                        QueueItem item = routerQueue.take();

                        if (item instanceof QueueItem.PoisonPill)
                            break;

                        QueueItem.IndexerBatch batch = (QueueItem.IndexerBatch) item;

                        router.routeTo(batch.term(), batch.uuids());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    abortIngestion("RocksDB interrupted");
                    throw new CompletionException(e);
                } catch (Exception e) {
                    logger.error("Failed to process batch. Reason: {}", e.getMessage());

                    abortIngestion("RocksDB failed: " + e);
                    throw new CompletionException(e);
                }
            }, routerThreadPool);
        }

        return futuresBatched(mongoFuturesArray, routerFuturesArray);
    }

    /**
     * This will crash the whole pipeline if anything messes up, so only insert good csv datasets.
     *
     * @return A super wrapped completable future.
     */
    private CompletableFuture<Void> futuresBatched(CompletableFuture<?>[] m, CompletableFuture<?>[] r) {
        CompletableFuture<Void> nestedRedisFutures = CompletableFuture.allOf(r);
        CompletableFuture<Void> nestedMongofutures = CompletableFuture.allOf(m);

        return CompletableFuture.allOf(nestedMongofutures, nestedRedisFutures).whenComplete((result, throwable) -> {
            mongoThreadPool.shutdown();
            routerThreadPool.shutdown();
        });
    }

    private void abortIngestion(String reason) {
        logger.error("Aborting ingestion: {}", reason);

        parserThreadPool.shutdownNow();
        indexerThreadPool.shutdownNow();
        mongoThreadPool.shutdownNow();
        routerThreadPool.shutdownNow();
    }

}