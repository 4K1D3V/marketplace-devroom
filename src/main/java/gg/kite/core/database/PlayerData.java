package gg.kite.core.database;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import gg.kite.core.utils.ItemUtils;
import org.bson.Document;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PlayerData {
    private final UUID playerId;
    private final List<Transaction> transactions;
    private final List<String> activeListings;

    public PlayerData(UUID playerId) {
        this.playerId = playerId;
        this.transactions = new ArrayList<>();
        this.activeListings = new ArrayList<>();
    }

    public PlayerData(UUID playerId, List<Transaction> transactions, List<String> activeListings) {
        this.playerId = playerId;
        this.transactions = transactions != null ? transactions : new ArrayList<>();
        this.activeListings = activeListings != null ? activeListings : new ArrayList<>();
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public List<Transaction> getTransactions() {
        return new ArrayList<>(transactions);
    }

    public List<String> getActiveListings() {
        return new ArrayList<>(activeListings);
    }

    public void addTransaction(Transaction transaction) {
        transactions.add(transaction);
    }

    public void addListing(String listingId) {
        activeListings.add(listingId);
    }

    public void removeListing(String listingId) {
        activeListings.remove(listingId);
    }

    public Document toDocument() {
        List<Document> transactionDocs = new ArrayList<>();
        for (Transaction transaction : transactions) {
            transactionDocs.add(transaction.toDocument());
        }

        return new Document()
                .append("playerId", playerId.toString())
                .append("transactions", transactionDocs)
                .append("activeListings", activeListings);
    }

    public static PlayerData fromDocument(Document doc) {
        UUID playerId = UUID.fromString(doc.getString("playerId"));
        List<Document> transactionDocs = doc.getList("transactions", Document.class);
        List<String> activeListings = doc.getList("activeListings", String.class);
        List<Transaction> transactions = new ArrayList<>();

        for (Document transactionDoc : transactionDocs) {
            transactions.add(Transaction.fromDocument(transactionDoc));
        }

        return new PlayerData(playerId, transactions, activeListings);
    }

    public static PlayerData load(MongoCollection<Document> collection, UUID playerId) {
        Document doc = collection.find(Filters.eq("playerId", playerId.toString())).first();
        return doc != null ? fromDocument(doc) : new PlayerData(playerId);
    }

    public void save(MongoCollection<Document> collection) {
        collection.replaceOne(
                Filters.eq("playerId", playerId.toString()),
                toDocument(),
                new com.mongodb.client.model.ReplaceOptions().upsert(true)
        );
    }

    public static class Transaction {
        private final UUID buyer;
        private final UUID seller;
        private final ItemStack item;
        private final double price;
        private final boolean isBlackMarket;
        private final long timestamp;

        public Transaction(UUID buyer, UUID seller, ItemStack item, double price, boolean isBlackMarket, long timestamp) {
            this.buyer = buyer;
            this.seller = seller;
            this.item = item;
            this.price = price;
            this.isBlackMarket = isBlackMarket;
            this.timestamp = timestamp;
        }

        public UUID getBuyer() {
            return buyer;
        }

        public UUID getSeller() {
            return seller;
        }

        public ItemStack getItem() {
            return item;
        }

        public double getPrice() {
            return price;
        }

        public boolean isBlackMarket() {
            return isBlackMarket;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public Document toDocument() {
            return new Document()
                    .append("buyer", buyer.toString())
                    .append("seller", seller.toString())
                    .append("item", ItemUtils.serializeItem(item))
                    .append("price", price)
                    .append("isBlackMarket", isBlackMarket)
                    .append("timestamp", timestamp);
        }

        public static Transaction fromDocument(Document doc) {
            return new Transaction(
                    UUID.fromString(doc.getString("buyer")),
                    UUID.fromString(doc.getString("seller")),
                    ItemUtils.deserializeItem(doc.getString("item")),
                    doc.getDouble("price"),
                    doc.getBoolean("isBlackMarket", false),
                    doc.getLong("timestamp")
            );
        }
    }
}