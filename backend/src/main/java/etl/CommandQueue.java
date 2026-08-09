package etl;

import search.PositionalPosting;

import java.util.List;
import java.util.Map;

public interface CommandQueue {
    record Commands(Map<String, List<PositionalPosting>> batch) implements CommandQueue {
    }

    record PoisonPill() implements CommandQueue {
    }

}