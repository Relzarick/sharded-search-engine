package search;

import org.rocksdb.RocksDBException;
import rocks.RocksService;

import java.io.IOException;

public class QueryService {
    private final RocksService[] shards;

    public QueryService() throws RocksDBException, IOException {
        shards = new RocksService[]{
                new RocksService("shard-0"),
                new RocksService("shard-1")
        };
    }

}