import bootstrap.FirstIngestion;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import indexer.InvertedIndexer;
import indexer.tokenizer.StemTokenization;
import mongo.Database;
import mongo.Repository;
import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import search.SearchEngine;
import search.SearchHandler;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public class Server {
    private static final Logger logger = LoggerFactory.getLogger(Server.class);

    public static void main(String[] args) {
        try {
            Repository db = new Database();
            InvertedIndexer indexer = new InvertedIndexer(new StemTokenization());

            if (!db.ifExists())
                FirstIngestion.run(db, indexer);

            SearchEngine search = new SearchEngine(db, indexer);

            HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
            start(server, new SearchHandler(search));

            shutdownHook(server, db);
        } catch (IOException e) {
            logger.error("IO ERROR: Failed to create the server");
        } catch (RuntimeException e) {
            logger.error("RUNTIME ERROR: Something crashed");
        } catch (RocksDBException e) {
            logger.error("ROCKSDB ERROR: Failed to open index");
        }
    }

    private static void start(HttpServer server, HttpHandler handler) {
        server.createContext("/search", handler);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();

        logger.info("Server is running on http://wretch:8080");
    }

    private static void shutdownHook(HttpServer server, Repository db) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop(0);

            try {
                db.close();
                logger.info("Database closed.");
            } catch (Exception e) {
                logger.error("Error while closing database: {}", e.getMessage());
            }
        }));
    }

}