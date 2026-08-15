package bootstrap;

import etl.CreateWorkers;
import etl.parser.CsvParser;
import indexer.InvertedIndexer;
import logging.StopWatch;
import mongo.Repository;
import rocks.RocksService;

public final class AppSetup {
    private AppSetup() {
    }

    /**
     * Handles setup logic including, parsing, tokenizing and ingestion to mongo and redis.
     *
     */
    public static void run(Repository db, RocksService index, InvertedIndexer indexer) {
        StopWatch pTimer = new StopWatch("Parsing pipeline");

        try {
            StopWatch iTimer = new StopWatch("CSV Index");
            CsvParser parser = new CsvParser();
            iTimer.stop();

            CreateWorkers workers = new CreateWorkers(db, index);
            workers.run(parser, indexer);

            pTimer.stop();
        } catch (Exception e) {
            pTimer.stopOnFailure();
            throw new RuntimeException(e.getMessage(), e);
        }
    }

}