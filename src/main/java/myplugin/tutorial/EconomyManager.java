

package myplugin.tutorial;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public class EconomyManager {
    private final JavaPlugin plugin;
    private final Map<UUID, Double> playerBalances;
    private final Map<String, Integer> shopItemPrices;
    private final Map<String, Inventory> shopInventories;
    private final File dataFile;

    public EconomyManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.playerBalances = new HashMap();
        this.shopItemPrices = new HashMap();
        this.shopInventories = new HashMap();
        this.dataFile = new File(plugin.getDataFolder(), "player_balances.yml");
        this.loadPlayerBalances();
        this.setupShopItems();
    }

    private void setupShopItems() {
        this.shopItemPrices.put("wood", 20);
        this.shopItemPrices.put("stone", 20);
    }

    public boolean hasPlayerData(Player player) {
        return this.playerBalances.containsKey(player.getUniqueId());
    }

    private void loadPlayerBalances() {
        if (this.dataFile.exists()) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(this.dataFile);
            Iterator var2 = config.getKeys(false).iterator();

            while(var2.hasNext()) {
                String uuidStr = (String)var2.next();
                UUID uuid = UUID.fromString(uuidStr);
                double balance = config.getDouble(uuidStr);
                this.playerBalances.put(uuid, balance);
            }

        }
    }

    public void saveData() {
        YamlConfiguration config = new YamlConfiguration();
        Iterator var2 = this.playerBalances.keySet().iterator();

        while(var2.hasNext()) {
            UUID uuid = (UUID)var2.next();
            config.set(uuid.toString(), this.playerBalances.get(uuid));
        }

        try {
            config.save(this.dataFile);
        } catch (IOException var4) {
            this.plugin.getLogger().warning("無法保存玩家余额数据到文件 " + this.dataFile.getName());
        }

    }

    public double getPlayerBalance(Player player) {
        return (Double)this.playerBalances.getOrDefault(player.getUniqueId(), 0.0);
    }

    public void givePlayerMoney(Player player, double amount) {
        double currentBalance = this.getPlayerBalance(player);
        this.playerBalances.put(player.getUniqueId(), currentBalance + amount);
    }

    public void takePlayerMoney(Player player, double amount) {
        double currentBalance = this.getPlayerBalance(player);
        this.playerBalances.put(player.getUniqueId(), currentBalance - amount);
    }

    public void transferMoney(Player sender, Player receiver, double amount) {
        double senderBalance = this.getPlayerBalance(sender);
        if (senderBalance >= amount) {
            this.givePlayerMoney(receiver, amount);
            this.givePlayerMoney(sender, -amount);
        } else {
            sender.sendMessage("你的餘額不足以轉移這個金額！");
        }

    }

    public void savePlayerData(Player player) {
    }

    public int getShopItemPrice(String itemName) {
        return (Integer)this.shopItemPrices.getOrDefault(itemName, 0);
    }

    public void setShopItemPrice(String itemName, int price) {
        this.shopItemPrices.put(itemName, price);
    }

    public void addItemToShopInventory(String itemName, ItemStack itemStack) {
        Inventory shopInventory = (Inventory)this.shopInventories.computeIfAbsent(itemName, (k) -> {
            return Bukkit.createInventory((InventoryHolder)null, 27, "商店 - " + k);
        });
        shopInventory.addItem(new ItemStack[]{itemStack});
    }

    public Inventory getShopInventory(String itemName) {
        return (Inventory)this.shopInventories.get(itemName);
    }

    public boolean hasShopInventory(String itemName) {
        return this.shopInventories.containsKey(itemName);
    }
}
