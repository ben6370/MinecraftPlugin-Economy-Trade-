package myplugin.tutorial;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import myplugin.tutorial.EconomyManager;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;


public final class MinecraftPlugin extends JavaPlugin implements Listener {

    private static Map<String, ShopItem> shopItems; // 集中化的商店商品列表
    private EconomyManager economyManager; // 經濟系統管理器
    private Set<UUID> playersWithTradeStick; // 已經獲得交易棍棒的玩家列表
    private Set<UUID> deadPlayers = new HashSet<>();

    @Override
    public void onEnable() {
        if (!getDataFolder().exists()) {
            if (!getDataFolder().mkdirs()) {
                getLogger().warning("無法創建插件數據文件夾");
            }
        }

        // 初始化經濟系統和商店物品
        economyManager = new EconomyManager(this);
        shopItems = new HashMap<>();

        // 載入商店物品
        loadShopItems();

        // 初始化已經獲得交易棍棒的玩家集合
        playersWithTradeStick = new HashSet<>();

        // 註冊事件監聽器和命令處理器
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("money").setExecutor(this);

        getLogger().info("插件已啟用！");
    }

    @Override
    public void onDisable() {
        // 在插件停用時保存經濟系統數據和商店物品
        economyManager.saveData();
        saveShopItems();
    }

    private void loadShopItems() {
        File shopFile = new File(getDataFolder(), "shop.yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(shopFile);

        for (String key : config.getKeys(false)) {
            String materialName = config.getString(key + ".material");
            int price = config.getInt(key + ".price");
            int stock = config.getInt(key + ".stock"); // 加載庫存數量
            Material material = Material.getMaterial(materialName);

            if (material != null) {
                shopItems.put(key, new ShopItem(key, material, price, stock));
            }
        }
    }

    private void saveShopItems() {
        File shopFile = new File(getDataFolder(), "shop.yml");
        YamlConfiguration config = new YamlConfiguration();

        for (Map.Entry<String, ShopItem> entry : shopItems.entrySet()) {
            String key = entry.getKey();
            ShopItem item = entry.getValue();

            config.set(key + ".material", item.getMaterial().name());
            config.set(key + ".price", item.getPrice());
            config.set(key + ".stock", item.getStock()); // 保存庫存數量
        }

        try {
            config.save(shopFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String playerName = ChatColor.RED + player.getName() + ChatColor.RESET;

        player.sendMessage(ChatColor.GOLD + "歡迎 " + playerName + " 加入伺服器！");
        player.sendMessage(ChatColor.GOLD + "進入伺服器後，打開背包可以看到發放的交易棍棒代表商店功能。");
        player.sendMessage(ChatColor.GOLD + "新進玩家可以獲得 $100 獎金，輸入指令 /money 來查詢自己的餘額。");
        player.sendMessage(ChatColor.LIGHT_PURPLE + "祝你遊戲愉快！");

        if (!economyManager.hasPlayerData(player)) {
            economyManager.givePlayerMoney(player, 100); // 給予初始金額
            player.sendMessage(ChatColor.GREEN + "這是新加入伺服器的獎金 $100");
        }

        // 如果玩家沒有獲得過交易棍棒，則發放交易棍棒
        giveTradeStick(player);
    }

    private void giveTradeStick(Player player) {
        if (!playersWithTradeStick.contains(player.getUniqueId())) {
            ItemStack tradeStick = new ItemStack(Material.STICK);
            ItemMeta meta = tradeStick.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.YELLOW + "交易");
                tradeStick.setItemMeta(meta);
            }
            player.getInventory().addItem(tradeStick);
            playersWithTradeStick.add(player.getUniqueId());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        getServer().broadcastMessage(player.getName() + " 已經登出");

        economyManager.savePlayerData(player); // 玩家退出時保存玩家數據
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        getServer().broadcastMessage(player.getName() + " 嘎嘎了，全服默哀 ...");

        deadPlayers.add(player.getUniqueId()); // 將死亡玩家加入死亡玩家列表
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();

        if (deadPlayers.contains(player.getUniqueId())) {
            giveTradeStick(player);
            deadPlayers.remove(player.getUniqueId());
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        // 確保玩家使用交易棍棒才打開交易介面
        if (item != null && item.getType() == Material.STICK && ChatColor.stripColor(item.getItemMeta().getDisplayName()).equals("交易")) {
            openTradeGUI(player);
        }
    }

    private void openTradeGUI(Player player) {
        // 使用 27 格的物品欄大小來顯示交易介面
        Inventory tradeGUI = Bukkit.createInventory(player, 27, "交易");

        // 將交易物品添加到物品欄中
        for (ShopItem item : shopItems.values()) {
            ItemStack displayItem = new ItemStack(item.getMaterial());
            ItemMeta meta = displayItem.getItemMeta();
            meta.setDisplayName(ChatColor.GREEN + "購買 " + item.getName() + " ($" + item.getPrice() + ")");
            displayItem.setItemMeta(meta);
            tradeGUI.addItem(displayItem);
        }

        // 打開交易介面給玩家
        player.openInventory(tradeGUI);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getType() == InventoryType.PLAYER) {
            ItemStack item = event.getOldCursor();
            if (item != null && item.getType() == Material.STICK && ChatColor.stripColor(item.getItemMeta().getDisplayName()).equals("交易")) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        ItemStack item = event.getItemDrop().getItemStack();
        if (item.getType() == Material.STICK && ChatColor.stripColor(item.getItemMeta().getDisplayName()).equals("交易")) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        Inventory clickedInventory = event.getClickedInventory();
        ItemStack currentItem = event.getCurrentItem();

        if (event.getView().getTitle().equals("交易")) {
            event.setCancelled(true); // 防止玩家直接取出交易物品
            if (currentItem != null && currentItem.getType() != Material.AIR) {
                String itemName = currentItem.getItemMeta().getDisplayName();
                if (itemName != null && itemName.startsWith(ChatColor.GREEN + "購買 ")) {
                    String[] parts = itemName.split(" ");
                    String itemNameWithoutColor = ChatColor.stripColor(parts[1]);
                    buyItem(player, itemNameWithoutColor);
                }
            }
        }
    }

    private void buyItem(Player player, String itemName) {
        ShopItem item = shopItems.get(itemName);
        if (item != null) {
            if (economyManager.hasEnoughMoney(player, item.getPrice())) {
                if (item.getStock() > 0) {
                    ItemStack itemStack = new ItemStack(item.getMaterial());
                    player.getInventory().addItem(itemStack);
                    economyManager.subtractMoney(player, item.getPrice());
                    item.decrementStock(); // 減少庫存
                    player.sendMessage(ChatColor.GREEN + "你成功購買了 " + itemName);
                } else {
                    player.sendMessage(ChatColor.RED + "抱歉，該物品目前無庫存。");
                }
            } else {
                player.sendMessage(ChatColor.RED + "抱歉，你的餘額不足以購買 " + itemName);
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("money")) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                sender.sendMessage(ChatColor.GREEN + "你的餘額: $" + economyManager.getBalance(player));
            } else {
                sender.sendMessage(ChatColor.RED + "只有玩家可以使用此指令！");
            }
            return true;
        }
        return false;
    }
}

class ShopItem {
    private final String name;
    private final Material material;
    private final int price;
    private int stock; // 新增庫存屬性

    public ShopItem(String name, Material material, int price ,int stock) {
        this.name = name;
        this.material = material;
        this.price = price;
        this.stock = stock;
    }

    public String getName() {
        return name;
    }

    public Material getMaterial() {
        return material;
    }

    public int getPrice() {
        return price;
    }
    public int getStock() {
        return stock;
    }
    public void setStock(int stock) {
        this.stock = stock;
    }
    public void reduceStock(int amount) {
        this.stock -= amount;
        if (this.stock < 0) {
            this.stock = 0;
        }
    }
}