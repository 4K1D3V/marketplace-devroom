package gg.kite.core.database;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Indexes;
import gg.kite.core.utils.ItemUtils;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class MongoManager {
    private MongoClient client;
    private MongoDatabase database;
    private MongoCollection<Document> listings;
    private MongoCollection<Document> players;
    private final String connectionString;

    public MongoManager(String connectionString) {
        this.connectionString = connectionString;
    }

    public void connect() {
        int retries = 3;
        while (retries > 0) {
            try {
                client = MongoClients.create(connectionString);
                database = client.getDatabase("marketplace");
                listings = database.getCollection("listings");
                players = database.getCollection("players");

                // Create index on expiresAt for efficient queries
                listings.createIndex(Indexes.descending("expiresAt"));
                players.createIndex(Indexes.ascending("playerId"));

                // Clean up invalid listings on startup
                cleanupInvalidListings();

                return;
            } catch (Exception e) {
                retries--;
                if (retries == 0) {
                    throw new RuntimeException("Failed to connect to MongoDB after retries: " + e.getMessage(), e);
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    public void disconnect() {
        if (client != null) {
            client.close();
            client = null;
        }
    }

    public CompletableFuture<Void> addListing(UUID seller, ItemStack item, double price) {
        return CompletableFuture.runAsync(() -> {
            Document doc = new Document()
                    .append("seller", seller.toString())
                    .append("item", ItemUtils.serializeItem(item))
                    .append("price", price)
                    .append("listedAt", System.currentTimeMillis())
                    .append("expiresAt", System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000); // 7 days
            listings.insertOne(doc);

            // Update PlayerData
            PlayerData playerData = getPlayerData(seller).join();
            playerData.addListing(doc.getObjectId("_id").toString());
            playerData.save(players);
        });
    }

    public CompletableFuture<List<Document>> getActiveListings(int page, int pageSize) {
        return CompletableFuture.supplyAsync(() -> listings.find(Filters.gt("expiresAt", System.currentTimeMillis()))
                .skip((page - 1) * pageSize)
                .limit(pageSize)
                .into(new ArrayList<>()));
    }

    public CompletableFuture<Integer> getActiveListingsCount() {
        return CompletableFuture.supplyAsync(() -> (int) listings.countDocuments(Filters.gt("expiresAt", System.currentTimeMillis())));
    }

    public CompletableFuture<List<Document>> getBlackMarketListings() {
        return CompletableFuture.supplyAsync(() -> {
            List<Document> allListings = listings.find(Filters.gt("expiresAt", System.currentTimeMillis()))
                    .limit(10)
                    .into(new ArrayList<>());
            Collections.shuffle(allListings);
            return allListings;
        });
    }

    public CompletableFuture<Void> removeListing(String listingId) {
        return CompletableFuture.runAsync(() -> {
            Document listing = listings.find(Filters.eq("_id", new ObjectId(listingId))).first();
            if (listing != null) {
                UUID seller = UUID.fromString(listing.getString("seller"));
                PlayerData playerData = getPlayerData(seller).join();
                playerData.removeListing(listingId);
                playerData.save(players);
                listings.deleteOne(Filters.eq("_id", new ObjectId(listingId)));
            }
        });
    }

    public CompletableFuture<Void> addTransaction(UUID buyer, UUID seller, ItemStack item, double price, boolean isBlackMarket) {
        return CompletableFuture.runAsync(() -> {
            PlayerData.Transaction transaction = new PlayerData.Transaction(
                    buyer, seller, item, price, isBlackMarket, System.currentTimeMillis()
            );

            // Update buyer's PlayerData
            PlayerData buyerData = getPlayerData(buyer).join();
            buyerData.addTransaction(transaction);
            buyerData.save(players);

            // Update seller's PlayerData
            PlayerData sellerData = getPlayerData(seller).join();
            sellerData.addTransaction(transaction);
            sellerData.save(players);
        });
    }

    public CompletableFuture<PlayerData> getPlayerData(UUID player) {
        return CompletableFuture.supplyAsync(() -> PlayerData.load(players, player));
    }

    public int getPlayerListingCount(UUID player) {
        return (int) listings.countDocuments(Filters.and(
                Filters.eq("seller", player.toString()),
                Filters.gt("expiresAt", System.currentTimeMillis())
        ));
    }

    public void cleanupExpiredListings() {
        listings.deleteMany(Filters.lte("expiresAt", System.currentTimeMillis()));
    }

    public void cleanupInvalidListings() {
        List<Document> invalidListings = new ArrayList<>();
        for (Document listing : listings.find()) {
            String itemData = listing.getString("item");
            if (ItemUtils.deserializeItem(itemData) == null) {
                invalidListings.add(listing);
            }
        }

        for (Document listing : invalidListings) {
            String listingId = listing.getObjectId("_id").toString();
            UUID seller = UUID.fromString(listing.getString("seller"));
            PlayerData playerData = getPlayerData(seller).join();
            playerData.removeListing(listingId);
            playerData.save(players);
            listings.deleteOne(Filters.eq("_id", listing.getObjectId("_id")));
        }
    }
}