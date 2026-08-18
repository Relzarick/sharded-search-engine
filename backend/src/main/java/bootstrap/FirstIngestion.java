package bootstrap;

import etl.CreateWorkers;
import etl.parser.CsvParser;
import indexer.InvertedIndexer;
import logging.StopWatch;
import mongo.Repository;
import rocks.RocksRouter;

public final class FirstIngestion {
    private FirstIngestion() {
    }

    /**
     * Handles setup logic including, parsing, tokenizing and ingestion to mongo and redis.
     *
     */
    public static void run(Repository db, InvertedIndexer indexer) {
        StopWatch pTimer = new StopWatch("Parsing pipeline");

        try {
            StopWatch iTimer = new StopWatch("CSV Index");
            CsvParser parser = new CsvParser();
            iTimer.stop();

            CreateWorkers workers = new CreateWorkers();
            RocksRouter router = new RocksRouter();

            workers.run(parser, db, indexer, router);
            pTimer.stop();

            router.shutdown();
        } catch (Exception e) {
            pTimer.stopOnFailure();
            throw new RuntimeException(e.getMessage(), e);
        }
    }

}