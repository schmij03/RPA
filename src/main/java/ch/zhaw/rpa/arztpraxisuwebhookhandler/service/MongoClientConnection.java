package ch.zhaw.rpa.arztpraxisuwebhookhandler.service;

import org.bson.Document;
import org.bson.conversions.Bson;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoException;
import com.mongodb.ServerApi;
import com.mongodb.ServerApiVersion;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;

public class MongoClientConnection {

    private static final String CONNECTION_STRING = "mongodb+srv://rpa:rpa@cluster0.qyhbks8.mongodb.net/?retryWrites=true&w=majority&appName=Cluster0";
    private MongoClient mongoClient;
    private MongoDatabase database;
    private MongoCollection<Document> collection;

    public MongoClientConnection() {
        this.mongoClient = createMongoClient();
        this.database = connectToDatabase();
        this.collection = getCollection(database, "Cluster0");
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

    public void savePatientToMongoDB(String name, String vorname, String ahvnummer, String email, String phonenumber) {
        try {
            Bson filter = Filters.eq("ahvnummer", ahvnummer);
            Document existingPatient = collection.find(filter).first();

            if (existingPatient != null) {
                System.out.println("Patient with AHV number " + ahvnummer + " already exists.");
            } else {
                Document patient = new Document("name", name)
                        .append("vorname", vorname)
                        .append("ahvnummer", ahvnummer)
                        .append("email", email)
                        .append("phonenumber", phonenumber);

                collection.insertOne(patient);
                System.out.println("Patient saved to MongoDB!");
            }
        } catch (MongoException e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to save patient to MongoDB", e);
        }
    }

    public boolean checkIfPatientExists(String ahvnummer) {
        Bson filter = Filters.eq("ahvnummer", ahvnummer);
        Document existingPatient = collection.find(filter).first();
        return existingPatient != null;
    }

    public String getEmailByAhvnummer(String ahvnummer) {
        Bson filter = Filters.eq("ahvnummer", ahvnummer);
        Document patient = collection.find(filter).first();
        
        if (patient != null) {
            return patient.getString("email");
        } else {
            return null;
        }
    }

    public void closeClient() {
        if (mongoClient != null) {
            mongoClient.close();
            System.out.println("MongoClient connection closed.");
        }
    }
}
