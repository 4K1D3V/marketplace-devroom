package gg.kite.core.utils;

import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.logging.Level;

import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

public class ItemUtils {
    private static JavaPlugin plugin;

    public static void initialize(JavaPlugin pluginInstance) {
        plugin = pluginInstance;
    }

    /**
     * Serializes an ItemStack to a Base64 string, preserving NBT data.
     *
     * @param item The ItemStack to serialize.
     * @return A Base64 string representing the ItemStack.
     */
    public static String serializeItem(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return "";
        }

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream)) {
            dataOutput.writeObject(item);
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to serialize item: " + item.getType(), e);
            return "";
        }
    }

    /**
     * Deserializes a Base64 string to an ItemStack, restoring NBT data.
     *
     * @param data The Base64 string to deserialize.
     * @return The deserialized ItemStack, or null if the data is invalid.
     */
    public static ItemStack deserializeItem(String data) {
        if (data == null || data.isEmpty()) {
            return null;
        }

        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(data));
             BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {
            return (ItemStack) dataInput.readObject();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to deserialize item: " + data, e);
            return null;
        }
    }
}