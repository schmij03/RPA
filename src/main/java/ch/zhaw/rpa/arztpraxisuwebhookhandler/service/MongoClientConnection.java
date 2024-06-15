package ch.zhaw.rpa.arztpraxisuwebhookhandler.service;

import java.util.List;
import java.util.Map;

import org.bson.Document;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoException;
import com.mongodb.ServerApi;
import com.mongodb.ServerApiVersion;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

public class MongoClientConnection {

    private static final String CONNECTION_STRING = "mongodb+srv://rpa:rpa@cluster0.qyhbks8.mongodb.net/?retryWrites=true&w=majority&appName=Cluster0";
    private MongoClient mongoClient;

    public MongoClientConnection() {
        this.mongoClient = createMongoClient();
    }

    public static void main(String[] args) {
        MongoClientConnection connection = new MongoClientConnection();

        MongoDatabase database = connection.connectToDatabase();
        MongoCollection<Document> collection = connection.getCollection(database, "Cluster0");

        System.out.println("Connected to Database: " + database.getName());
        System.out.println("Using Collection: " + collection.getNamespace().getCollectionName());

        // Example usage
        connection.saveToMongoDB(List.of(Map.of("exampleKey", "exampleValue")));
        connection.clearMongoDB();

        // Close the client connection when done
        connection.closeClient();
    }

    public MongoDatabase connectToDatabase() {
        try {
            MongoDatabase database = mongoClient.getDatabase("RPA");
            database.runCommand(new Document("ping", 1));
            System.out.println("Successfully connected to MongoDB!");
            return database;
        } catch (MongoException e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to connect to the database", e);
        }
    }

    public MongoCollection<Document> getCollection(MongoDatabase database, String collectionName) {
        return database.getCollection(collectionName);
    }

    private MongoClient createMongoClient() {
        ServerApi serverApi = ServerApi.builder()
                .version(ServerApiVersion.V1)
                .build();

        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(CONNECTION_STRING))
                .serverApi(serverApi)
                .build();

        return MongoClients.create(settings);
    }

    public void saveToMongoDB(List<Map<String, Object>> data) {
        MongoDatabase database = connectToDatabase();
        MongoCollection<Document> collection = getCollection(database, "Cluster0");

        List<Document> documents = data.stream()
                .map(Document::new)
                .toList();

        collection.insertMany(documents);
        System.out.println("Data saved to MongoDB!");
    }

    public void clearMongoDB() {
        MongoDatabase database = connectToDatabase();
        MongoCollection<Document> collection = getCollection(database, "Cluster0");

        long deletedCount = collection.deleteMany(new Document()).getDeletedCount();

        if (deletedCount > 0) {
            System.out.println("Collection cleared.");
        } else {
            System.out.println("No documents found to delete.");
        }
    }

    public void closeClient() {
        if (mongoClient != null) {
            mongoClient.close();
            System.out.println("MongoClient connection closed.");
        }
    }
}
