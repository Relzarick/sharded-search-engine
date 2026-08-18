package mongo;

import org.bson.BsonDocument;

import java.util.List;
import java.util.stream.Collectors;

public record DocumentResults(List<BsonDocument> documents) {
    /**
     *
     * @return A Json document for frontend
     */
    public String jsonify(int count) {
        String rows = documents.stream()
                .map(BsonDocument::toJson)
                .collect(Collectors.joining(",", "[", "]"));


        return "{\"count\": %d, \"rows\": %s}".formatted(count, rows);
    }

}