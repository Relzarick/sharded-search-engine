package mongo;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.InsertManyOptions;
import org.bson.BsonDocument;
import org.bson.UuidRepresentation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class Database implements Repository {
    private final MongoClient client;
    private final MongoCollection<BsonDocument> collection;

    private static final ConnectionString CONNECTION_STRING = new ConnectionString("mongodb://mongrel:27017");
    private static final InsertManyOptions UNORDERED = new InsertManyOptions().ordered(false);
    private static final String DATABASENAME = "mongrel-db";
    private static final String COLLECTION = "col";
    private static final String id = "_id";

    public Database() {
        MongoClientSettings settings = MongoClientSettings.builder()
                .uuidRepresentation(UuidRepresentation.STANDARD)
                .applyConnectionString(CONNECTION_STRING)
                .build();

        client = MongoClients.create(settings);

        MongoDatabase db = client.getDatabase(DATABASENAME);
        collection = db.getCollection(COLLECTION, BsonDocument.class);
    }

    @Override
    public BsonDocument fetch(UUID val) {
        return collection.find(Filters.eq(id, val)).first();
    }

    @Override
    public DocumentResults fetchMany(List<UUID> val) {
        return new DocumentResults(collection.find(Filters.in(id, val)).into(new ArrayList<>()));
    }

    @Override
    public void insert(List<BsonDocument> batch) {
        collection.insertMany(batch, UNORDERED);
    }

    @Override
    public Boolean ifExists() {
        return collection.find().first() != null;
    }

    @Override
    public MongoCollection<BsonDocument> getCollection() {
        return collection;
    }

    @Override
    public void close() {
        client.close();
    }

}