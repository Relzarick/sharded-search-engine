package rocks;

import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import redis.Index;
import search.PositionalPosting;

import java.util.List;
import java.util.concurrent.ExecutionException;

public class RocksService implements Index {
    Options options;
    RocksDB db;

    public RocksService() throws RocksDBException {
        options = new Options().setCreateIfMissing(true);
        db = RocksDB.open(options, "/app/data");
    }

    @Override
    public void set(String key, List<PositionalPosting> postings) {

    }

    @Override
    public void flush() {

    }

    @Override
    public List<PositionalPosting> retrieve(String key) throws ExecutionException, InterruptedException {
        return List.of();
    }

    @Override
    public void close() {
        db.close();
    }
}