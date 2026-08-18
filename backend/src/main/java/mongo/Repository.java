package mongo;

import com.mongodb.client.MongoCollection;
import org.bson.BsonDocument;

import java.util.List;
import java.util.UUID;

/**
 * All databases should implement this interface
 */
public interface Repository {
    BsonDocument fetch(UUID val);

    DocumentResults fetchMany(List<UUID> val);

    void insert(List<BsonDocument> batch);

    Boolean ifExists();

    MongoCollection<BsonDocument> getCollection();

    void close();
}