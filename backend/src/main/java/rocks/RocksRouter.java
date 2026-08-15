package rocks;

import etl.CommandQueue;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.rocksdb.RocksDBException;
import redis.InternalPosting;
import search.PositionalPosting;

import java.util.*;

public class RocksRouter {
    RocksService index;

    public RocksRouter(RocksService service) {
        index = service;
    }

    // for now I dont need it to do routing, it should just be convert indexer records to commands

    public void routeTo(Object2ObjectOpenHashMap<String, List<InternalPosting>> tokensToPosting, UUID[] UUIDs) throws RocksDBException {
        List<CommandQueue.Commands> batch = new ArrayList<>(tokensToPosting.size());

        for (Map.Entry<String, List<InternalPosting>> entry : tokensToPosting.entrySet()) {
            List<PositionalPosting> postings = new ArrayList<>(entry.getValue().size());

            for (InternalPosting post : entry.getValue())
                postings.add(new PositionalPosting(UUIDs[post.docIndex()], post.positions()));

            Map<String, List<PositionalPosting>> cmd = new HashMap<>(1);
            cmd.put(entry.getKey(), postings);

            batch.add(new CommandQueue.Commands(cmd));
        }

        index.set(batch);
    }

}