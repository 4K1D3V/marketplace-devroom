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
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class MongoManager {
    private MongoClient client;
    private MongoDatabase database;
    private MongoCollection<Document> listings;
    private MongoCollection<Document> players;
    private MongoCollection<Document> transactions;
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
                transactions = database.getCollection("transactions");

                listings.createIndex(Indexes.descending("expiresAt"));
                players.createIndex(Indexes.ascending("playerId"));

                // Test the connection by listing collections
                database.listCollectionNames().first();

                cleanupInvalidListings();

                return;
            } catch (Exception e) {
                retries--;
                if (retries == 0) {
                    throw new RuntimeException("Failed to connect to MongoDB after " + (3 - retries) + " retries: " + e.getMessage(), e);
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
            String serializedItem = ItemUtils.serializeItem(item);
            PlayerData.Transaction transaction = new PlayerData.Transaction(
                    buyer, seller, serializedItem, price, isBlackMarket, System.currentTimeMillis()
            );

            PlayerData buyerData = getPlayerData(buyer).join();
            buyerData.addTransaction(transaction);
            buyerData.save(players);

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

    public CompletableFuture<List<Document>> getTransactions(@NotNull UUID uniqueId) {
        return CompletableFuture.supplyAsync(() -> {
            PlayerData playerData = getPlayerData(uniqueId).join();
            List<PlayerData.Transaction> transactionsList = playerData.getTransactions();
            List<Document> transactionDocuments = new ArrayList<>();

            for (PlayerData.Transaction transaction : transactionsList) {
                // Deserialize the item from the transaction
                String serializedItem = transaction.getSerializedItem();
                ItemStack itemStack = serializedItem != null ? ItemUtils.deserializeItem(serializedItem) : null;
                String itemName = "Unknown Item";
                if (itemStack != null) {
                    ItemMeta meta = itemStack.getItemMeta();
                    itemName = (meta != null && meta.hasDisplayName()) ? meta.getDisplayName() : itemStack.getType().name();
                }

                Document transactionDoc = new Document()
                        .append("buyer", transaction.getBuyer().toString())
                        .append("seller", transaction.getSeller().toString())
                        .append("item", serializedItem)
                        .append("itemName", itemName)
                        .append("price", transaction.getPrice())
                        .append("isBlackMarket", transaction.isBlackMarket())
                        .append("timestamp", transaction.getTimestamp());
                transactionDocuments.add(transactionDoc);
            }

            // Sort by timestamp (newest first)
            transactionDocuments.sort((doc1, doc2) -> Long.compare(doc2.getLong("timestamp"), doc1.getLong("timestamp")));

            return transactionDocuments;
        });
    }

    public CompletableFuture<List<Document>> getListingsByPlayer(@NotNull UUID uniqueId) {
        return CompletableFuture.supplyAsync(() -> {
            List<Document> playerListings = new ArrayList<>();
            String uuidString = uniqueId.toString();

            // Find active listings where the player is the seller, sorted by listedAt (newest first)
            for (Document listing : listings.find(
                    Filters.and(
                            Filters.eq("seller", uuidString),
                            Filters.gt("expiresAt", System.currentTimeMillis())
                    )
            ).sort(new Document("listedAt", -1))) {
                playerListings.add(listing);
            }

            return playerListings;
        });
    }

    public CompletableFuture<Document> getListingById(String listingId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ObjectId objectId = new ObjectId(listingId);
                return listings.find(new Document("_id", objectId)).first();
            } catch (IllegalArgumentException e) {
                return null;
            }
        });
    }
}