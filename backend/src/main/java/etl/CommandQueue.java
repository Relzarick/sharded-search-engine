package etl;

import it.unimi.dsi.fastutil.bytes.ByteArrayList;

import java.util.Map;

public interface CommandQueue {
    record Commands(Map<String, ByteArrayList> batch) implements CommandQueue {
    }

    record PoisonPill() implements CommandQueue {
    }

}