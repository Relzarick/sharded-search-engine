import bootstrap.AppSetup;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import indexer.InvertedIndexer;
import indexer.tokenizer.StemTokenization;
import mongo.Database;
import mongo.Repository;
import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rocks.RocksService;
import search.SearchHandler;
import search.SearchService;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public class Server {
    private static final Logger logger = LoggerFactory.getLogger(Server.class);

    public static void main(String[] args) {
        try {
            Repository db = new Database();
            RocksService index = new RocksService();
            InvertedIndexer indexer = new InvertedIndexer(new StemTokenization());

            if (!db.ifExists())
                AppSetup.run(db, index, indexer);

            index.compact();
            index.test();

            SearchService search = new SearchService(db, indexer);

            HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
            start(server, new SearchHandler(search));

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                server.stop(0);

                try {
                    db.close();
                    index.close();

                    logger.info("Database closed.");
                } catch (Exception e) {
                    logger.error("Error while closing database: {}", e.getMessage());
                }
            }));

        } catch (IOException e) {
            logger.error("IO ERROR: Failed to create the server");
        } catch (RuntimeException e) {
            logger.error("RUNTIME ERROR: Something crashed");
        } catch (RocksDBException e) {
            logger.error("RocksDB ERROR: Constructor/Compaction failure");
        }
    }

    private static void start(HttpServer server, HttpHandler handler) {
        server.createContext("/search", handler);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();

        logger.info("Server is running on http://wretch:8080");
    }

}