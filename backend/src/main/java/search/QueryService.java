package search;

import org.rocksdb.RocksDBException;
import rocks.RocksService;

public class QueryService {
    private final RocksService[] shards;

    public QueryService() throws RocksDBException {
        shards = new RocksService[]{
                new RocksService("index/shard-0"),
                new RocksService("index/shard-1")
        };
    }

}