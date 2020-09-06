package fr.milekat.cite_villagers;

import fr.milekat.cite_villagers.commands.Trade;
import fr.milekat.cite_villagers.commands.TradeTab;
import fr.milekat.cite_villagers.engine.Refresh;
import fr.milekat.cite_villagers.events.PlayerTrade;
import fr.milekat.cite_villagers.utils.VillagerTradeListener;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.List;

import static fr.milekat.cite_villagers.engine.Refresh.setLockTrade;

public class MainVillager extends JavaPlugin {
    // Init des var static, pour tous le projet
    public static String prefixConsole = "[Balkoura-villageois] ";
    public static boolean jedisDebug;
    public static boolean manuLockTrades;
    public static boolean autoLockTrades;
    public static HashMap<Integer, List<MerchantRecipe>> tradesLists = new HashMap<>();
    public static HashMap<Integer, Integer> blackMarketid = new HashMap<>();
    public static MainVillager mainVillager;

    @Override
    public void onEnable() {
        this.getConfig();
        jedisDebug = this.getConfig().getBoolean("redis.debug");
        mainVillager = this;
        // SQL
        // Events
        getServer().getPluginManager().registerEvents(new PlayerTrade(),this);
        VillagerTradeListener tradeEvent = new VillagerTradeListener(this);
        // Commandes
        getCommand("trade").setExecutor(new Trade());
        //  Tab
        getCommand("trade").setTabCompleter(new TradeTab());
        // Engine
        setLockTrade();
        new Refresh().Trade();
        new Refresh().resetTrades();
        autoLockTrades = false;
        manuLockTrades = false;
    }

    @Override
    public void onDisable(){
        setLockTrade();
    }

    public static MainVillager getInstance(){
        return mainVillager;
    }
}
