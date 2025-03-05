package org.desp.mining.database;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import lombok.Getter;

public class DatabaseRegister {

    private final MongoClient mongoClient;

    @Getter
    private final MongoDatabase database;

    public DatabaseRegister() {
        DBConfig connector = new DBConfig();
        String path = connector.getMongoConnectionContent();
        MongoClientSettings settings = MongoClientSettings.builder().applyConnectionString(new ConnectionString(path))
                .build();

        this.mongoClient = MongoClients.create(settings);
        this.database = mongoClient.getDatabase("Mining");
    }
}
