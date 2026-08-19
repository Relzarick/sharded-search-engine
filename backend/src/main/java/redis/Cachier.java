package redis;

import search.QueryResult;

import java.util.List;

public class Cachier {
    private final Cache cache = new Cache("cache");

    public QueryResult checkCache(List<String> tokens) {
        return null;
    }

    /**
     * Store in cache only after 2 or more queries
     */
    public void setInCache() {
    }

}