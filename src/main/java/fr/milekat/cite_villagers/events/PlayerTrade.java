package fr.milekat.cite_villagers.events;

import fr.milekat.cite_core.MainCore;
import fr.milekat.cite_core.core.engines.TeamEngine;
import fr.milekat.cite_core.core.obj.Team;
import fr.milekat.cite_libs.MainLibs;
import fr.milekat.cite_libs.utils_tools.DateMilekat;
import fr.milekat.cite_villagers.MainVillager;
import fr.milekat.cite_villagers.utils.VillagerTradeListener;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Merchant;
import org.bukkit.inventory.MerchantRecipe;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class PlayerTrade implements Listener {

    @EventHandler
    void onVillagerTradeEvent(VillagerTradeListener.VillagerTradeEvent event) throws SQLException {
        if (MainVillager.autoLockTrades){
            event.setCancelled(true);
            return;
        }
        StringBuilder query = new StringBuilder("INSERT INTO `" + MainCore.SQLPREFIX + "trade_log`(`player_id`, `trade_id`, `emeraudes`, `date`) VALUES");
        for (int i=1;i<=event.getOrders();i++){
            query.append(" ((SELECT `player_id` FROM `").append(MainCore.SQLPREFIX).append("player` WHERE `uuid` = '").append(event.getPlayer().getUniqueId()).append("'), (SELECT `trade_id` FROM `").append(MainCore.SQLPREFIX).append("trade_liste` WHERE `mat_1` = (SELECT `item_id` FROM `").append(MainCore.SQLPREFIX).append("material_liste` WHERE `Material` = '").append(event.getRecipe().getIngredients().get(0).getType().toString()).append("') AND qt_1 = '").append(event.getRecipe().getIngredients().get(0).getAmount()).append("'), ").append("'").append(event.getRecipe().getResult().getAmount()).append("', '").append(DateMilekat.setDateNow()).append("'),");
        }
        query = new StringBuilder(query.substring(0, query.length() - 1) + " RETURNING " +
                "(SELECT `trade_id` FROM `" + MainCore.SQLPREFIX + "trade_liste` " +
                "WHERE `mat_1` = (SELECT `item_id` FROM `" + MainCore.SQLPREFIX + "material_liste` " +
                "WHERE `Material` = '" + event.getRecipe().getIngredients().get(0).getType().toString() + "') " +
                "AND qt_1 = '" + event.getRecipe().getIngredients().get(0).getAmount() + "') as trade_id;");
        Connection connection = MainLibs.getSql();
        PreparedStatement q = connection.prepareStatement(query.toString());
        q.execute();
        if (q.getResultSet().last() && q.getResultSet().getInt("trade_id")>0) {
            int trade_id = q.getResultSet().getInt("trade_id");
            Team team = MainCore.teamHashMap.get(MainCore.profilHashMap.get(event.getPlayer().getUniqueId()).getTeam());
            HashMap<Integer, Integer> tradesuses = team.getTradesuses();
            tradesuses.put(trade_id,team.getTradesuses().getOrDefault(trade_id,0) + event.getOrders());
            team.setTradesuses(tradesuses);
            new TeamEngine().saveTradesUses(team);
        }
        q.close();
    }

    @EventHandler
    void onVillagerClick(NPCRightClickEvent event) {
        if (event.getNPC().getId() == 1) {
            // BlackMarket
            Team team = MainCore.teamHashMap.get(MainCore.profilHashMap.get(event.getClicker().getUniqueId()).getTeam());
            if (team.isTrading()) {
                event.getClicker().sendMessage(MainCore.prefixCmd + "§cDésolé mais ton équipe parle déjà sur ce PNJ.");
            } else {
                team.setTrading(true);
                Merchant merchant = setUsesforTrades(team);
                if (merchant==null) {
                    event.getClicker().sendMessage(MainCore.prefixCmd + "§cPas de BlackMarket aujourd'hui ?");
                    team.setTrading(false);
                    return;
                }
                event.getClicker().openMerchant(merchant, true);
            }
        } else {
            if (!MainVillager.tradesLists.containsKey(event.getNPC().getId())) return;
            if (MainVillager.autoLockTrades || MainVillager.manuLockTrades) {
                event.setCancelled(true);
                event.getClicker().sendMessage(MainCore.prefixCmd + "§cJe me repose, passe me voir un peu plus tard.");
                return;
            }
            Merchant merchant = Bukkit.createMerchant(event.getNPC().getName());
            List<MerchantRecipe> recipes = new ArrayList<>();
            for (MerchantRecipe recipe: MainVillager.tradesLists.get(event.getNPC().getId())) {
                recipe.setUses(0);
                MerchantRecipe recipe1 = new MerchantRecipe(recipe.getResult().clone(), 100000);
                recipe1.setExperienceReward(false);
                recipe1.setIngredients(recipe.getIngredients());
                recipe1.setUses(0);
                recipes.add(recipe1);
            }
            merchant.setRecipes(recipes);
            event.getClicker().openMerchant(merchant, true);
        }
    }

    /**
     *      Appliques les uses sur les trades par rapport à l'équipe
     */
    private Merchant setUsesforTrades(Team team) {
        List<MerchantRecipe> recipes = MainVillager.tradesLists.getOrDefault(1,null);
        if (recipes==null) return null;
        int trade_nb = 0;
        for (MerchantRecipe recipe : recipes) {
            trade_nb++;
            recipe.setUses(team.getTradesuses().getOrDefault(MainVillager.blackMarketid.get(trade_nb),0));
        }
        Merchant merchant = Bukkit.createMerchant("BlackMarket");
        merchant.setRecipes(recipes);
        return merchant;
    }

    @EventHandler
    void onVillagerQuit(InventoryCloseEvent event) {
        if (event.getView().getTitle().equalsIgnoreCase("BlackMarket")) {
            Team team = MainCore.teamHashMap.get(MainCore.profilHashMap.get(event.getPlayer().getUniqueId()).getTeam());
            team.setTrading(false);
        }
    }
}