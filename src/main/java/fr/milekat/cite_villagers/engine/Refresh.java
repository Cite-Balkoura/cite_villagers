package fr.milekat.cite_villagers.engine;

import fr.milekat.cite_core.MainCore;
import fr.milekat.cite_core.utils_tools.DateMilekat;
import fr.milekat.cite_villagers.MainVillager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class Refresh {
    private int hour;
    private int lasthour;
    private final MainVillager mainVillager = MainVillager.getInstance();

    /**
     *      Engine pour update les trades toutes les nuits MC
     */
    public void Trade(){
        new BukkitRunnable() {
            @Override
            public void run() {
                double mcTime = Objects.requireNonNull(Bukkit.getServer().getWorld("world")).getTime();
                mcTime = mcTime/1000;
                hour = (int) mcTime;
                if (hour!=lasthour && hour==18) {
                    Bukkit.getScheduler().runTask(mainVillager, Refresh::setLockTrade);
                    //updateBourse(); -> Non conservé pour la prochaine cité.
                    resetTrades();
                } else MainVillager.autoLockTrades = hour == 18;
                lasthour = hour;
            }
        }.runTaskTimerAsynchronously(mainVillager,0,20);
    }

    /**
     *      Fonction pour bloquer les trades des joueurs !
     */
    public static void setLockTrade(){
        MainVillager.autoLockTrades = true;
        for (Player onlineP : Bukkit.getOnlinePlayers()){
            if (onlineP.getOpenInventory().getType().equals(InventoryType.MERCHANT)){
                onlineP.closeInventory();
            }
        }
    }

    /*
    public void updateBourse(){
        Connection connection = MainCore.sql.getConnection();
        try {
            PreparedStatement q = connection.prepareStatement("SELECT q1.moyenneprX2, q2.total_trades FROM " +
                    "(SELECT AVG(prDesventes) as moyenneprX2 FROM (SELECT (SUM(`emeraudes`)*100/(SELECT SUM(`emeraudes`) " +
                    "FROM `" + MainCore.SQLPREFIX + "trade_log` WHERE `emeraudes` != 0)) as prDesventes " +
                    "FROM `" + MainCore.SQLPREFIX + "trade_log` WHERE `emeraudes` != 0 GROUP BY `trade_id`) as a) as q1, " +
                    "(SELECT COUNT(`trade_id`) as total_trades " +
                    "FROM `" + MainCore.SQLPREFIX + "trade_log` WHERE `emeraudes` != 0) as q2 WHERE 1;");
            q.execute();
            q.getResultSet().last();
            float moyenneprX2 = q.getResultSet().getFloat("moyenneprX2")/100*2;
            float trade_idCount = q.getResultSet().getFloat("total_trades");
            q.close();
            q = connection.prepareStatement("SELECT `trade_id`, SUM(`emeraudes`) as totE, " +
                    "(SUM(emeraudes)*100/(SELECT SUM(`emeraudes`) FROM `" + MainCore.SQLPREFIX +
                    "trade_log` WHERE `emeraudes` != 0)) as prEm FROM `" + MainCore.SQLPREFIX +
                    "trade_log` WHERE `emeraudes` != 0 GROUP BY `trade_id` ORDER BY totE DESC;");
            q.execute();
            HashMap<Integer,Float> prGlissant20final = new HashMap<>();
            while ((q.getResultSet().next())){
                // % calculé sur les émeraudes
                float prEmX2 = q.getResultSet().getFloat("prEm")/100 * 2f;
                float prEmGlissant100;
                if (moyenneprX2/2f-prEmX2<0f){
                    prEmGlissant100 = prEmX2*2f;
                } else {
                    prEmGlissant100 = moyenneprX2/2f-(1f-prEmX2);
                }
                float prEmGlissant50 = (prEmGlissant100/2)*-1f;
                // % calculé sur le rang
                float rang = (float) q.getResultSet().getRow();
                float prRang = rang / trade_idCount;
                float prRangX2 = prRang*2f;
                float prRangGlissant100 = 1f-prRangX2;
                float prRangGlissant50 = (prRangGlissant100/2f)*-1f;
                // Somme des %
                float prSomme50 = (prEmGlissant50+prRangGlissant50)/2f;
                float truquageNegatif;
                if (prSomme50/2.5f<0f){
                    truquageNegatif = 1.3f;
                } else {
                    truquageNegatif = 1f;
                }
                float prGlissant20 = truquageNegatif*prSomme50/2.5f;
                if (prGlissant20>0.2f){
                    prGlissant20final.put(q.getResultSet().getInt("trade_id"),0.2f);
                } else prGlissant20final.put(q.getResultSet().getInt("trade_id"), Math.max(prGlissant20, -0.2f));
            }
            q.close();
            q = connection.prepareStatement("SELECT `trade_id`, `bourse_selecter`, `qt_1`, `qt_2`, `qt_r` FROM `"
                            + MainCore.SQLPREFIX + "trade_liste`;");
            q.execute();
            while (q.getResultSet().next()) {
                if (prGlissant20final.containsKey(q.getResultSet().getInt("trade_id"))) {
                    int trade_id = q.getResultSet().getInt("trade_id");
                    PreparedStatement qUpdate = connection.prepareStatement("UPDATE `" + MainCore.SQLPREFIX +
                            "trade_liste` SET `qt_1_day`=?,`qt_2_day`=?,`qt_r_day`=? WHERE `trade_id` = ?;");
                    if (q.getResultSet().getBoolean("bourse_selecter")) {
                        // Update du result
                        qUpdate.setNull(1, Types.INTEGER);
                        qUpdate.setNull(2, Types.INTEGER);
                        int qt_r = Math.round(q.getResultSet().getFloat("qt_r") * (1f + prGlissant20final.get(trade_id)));
                        if (qt_r<1){
                            qt_r =1;
                        }
                        qUpdate.setInt(3, qt_r);
                    } else {
                        // Update du qt_1 & qt_2 si non null
                        int qt_1 = Math.round(q.getResultSet().getFloat("qt_1") * (1f - prGlissant20final.get(trade_id)));
                        if (qt_1<1){
                            qt_1 =1;
                        }
                        qUpdate.setInt(1, qt_1);
                        qUpdate.setNull(3, Types.INTEGER);
                        if (q.getResultSet().getString("qt_2") == null) {
                            qUpdate.setNull(2, Types.INTEGER);
                        } else {
                            // + qt_2 (non null)
                            int qt_2 = Math.round(q.getResultSet().getFloat("qt_2") * (1f - prGlissant20final.get(trade_id)));
                            if (qt_2<1){
                                qt_2 =1;
                            }
                            qUpdate.setInt(2, qt_2);
                        }
                    }
                    qUpdate.setInt(4, trade_id);
                    qUpdate.execute();
                    qUpdate.close();
                }
            }
            q.close();
        } catch (SQLException e) {
            Bukkit.getLogger().warning(MainVillager.prefixConsole + "Update bourse en erreur : " + e);
            e.printStackTrace();
        }
    }*/

    /**
     *      Re-paramettre tous les trades par rapport au valeurs SQL !
     */
    public void resetTrades(){
        Connection connection = MainCore.sql.getConnection();
        Bukkit.getLogger().info(MainVillager.prefixConsole + "mise à jour des échanges en cours...");
        try {
            // Récup des trades normaux
            PreparedStatement q = connection.prepareStatement("SELECT trade_trigger FROM `" + MainCore.SQLPREFIX + "trade_liste`");
            q.execute();
            while (q.getResultSet().next()) {
                PreparedStatement q2 = getNPCTrades(q.getResultSet().getString("trade_trigger"));
                if (q2==null) continue;
                q2.execute();
                List<MerchantRecipe> recipes = new ArrayList<>();
                boolean isBlackMarket = q.getResultSet().getString("trade_trigger").equalsIgnoreCase(DateMilekat.setLiteDateNow());
                if (isBlackMarket) MainVillager.blackMarketid.clear();
                int trade_nb = 0;
                while (q2.getResultSet().next()) {
                    // Mise en forme des échanges
                    recipes.add(setTrading(q2));
                    if (isBlackMarket)
                        trade_nb++;
                        MainVillager.blackMarketid.put(trade_nb,q2.getResultSet().getInt("trade_id"));
                }
                // Ajout au PNJ concerné (Si trigger = date du jour alors NPCID = 1 = BlackMarket)
                if (isBlackMarket) {
                    MainVillager.tradesLists.put(1, recipes);
                } else {
                    MainVillager.tradesLists.put(q.getResultSet().getInt("trade_trigger"), recipes);
                }
                q2.close();
            }
            q.close();
            Bukkit.getLogger().info(MainVillager.prefixConsole + "mise à jour des échanges terminée.");
        } catch (SQLException e) {
            Bukkit.getLogger().warning(MainVillager.prefixConsole + "Erreur lors de la mise à jour des trades.");
            e.printStackTrace();
        }
    }

    /**
     *      Permet de récupérer tous les échanges du PNJ ciblé
     * @param npc_trigger id du PNJ ciblé OU date de l'échange (cas marché noir)
     * @return query avec tous les trades
     */
    private PreparedStatement getNPCTrades(String npc_trigger) {
        Connection connection = MainCore.sql.getConnection();
        try {
            PreparedStatement q = connection.prepareStatement("SELECT " +
                    "trade_id, " +
                    "mat_1.Material as 1_item, " +
                    "mat_1.Name as 1_name, " +
                    "mat_1.Enchantment as 1_ench, " +
                    "mat_1.Lore as 1_lore, " +
                    "qt_1, " +
                    "mat_2.Material as 2_item, " +
                    "mat_2.Name as 2_name, " +
                    "mat_2.Enchantment as 2_ench, " +
                    "mat_2.Lore as 2_lore, " +
                    "qt_2, " +
                    "mat_r.Material as r_item, " +
                    "mat_r.Name as r_name, " +
                    "mat_r.Enchantment as r_ench, " +
                    "mat_r.Lore as r_lore, " +
                    "qt_r, " +
                    "max_uses " +
                    "FROM `" + MainCore.SQLPREFIX + "trade_liste` trade " +
                    "LEFT JOIN `" + MainCore.SQLPREFIX + "trade_material_liste` mat_1 ON trade.mat_1 = mat_1.item_id " +
                    "LEFT JOIN `" + MainCore.SQLPREFIX + "trade_material_liste` mat_2 ON trade.mat_2 = mat_2.item_id " +
                    "LEFT JOIN `" + MainCore.SQLPREFIX + "trade_material_liste` mat_r ON trade.mat_r = mat_r.item_id " +
                    "WHERE trade.trade_trigger = ?;");
            if (npc_trigger==null) {
                return null;
            } else if (npc_trigger.length()==10) {
                q.setString(1, npc_trigger);
            } else {
                q.setInt(1, Integer.parseInt(npc_trigger));
            }
            return q;
        } catch (SQLException throwables) {
            Bukkit.getLogger().warning(MainVillager.prefixConsole + "Erreur lors de la récupérations des trades de: " + npc_trigger);
            throwables.printStackTrace();
        }
        return null;
    }

    /**
     *      Défini un trade par rapport à une querry
     */
    private MerchantRecipe setTrading(PreparedStatement q) throws SQLException {
        if (q==null) return null;
        MerchantRecipe recipe = new MerchantRecipe(
                setItem(q.getResultSet().getString("r_item")
                        , q.getResultSet().getString("r_name")
                        , q.getResultSet().getString("r_ench")
                        , q.getResultSet().getString("r_lore")
                        , q.getResultSet().getInt("qt_r"))
                        , q.getResultSet().getInt("max_uses"));
        recipe.addIngredient(
                setItem(q.getResultSet().getString("1_item")
                        , q.getResultSet().getString("1_name")
                        , q.getResultSet().getString("1_ench")
                        , q.getResultSet().getString("1_lore")
                        , q.getResultSet().getInt("qt_1")));
        if (q.getResultSet().getString("qt_2") != null) {
            recipe.addIngredient(setItem(q.getResultSet().getString("2_item")
                    , q.getResultSet().getString("2_name")
                    , q.getResultSet().getString("2_ench")
                    , q.getResultSet().getString("2_lore")
                    , q.getResultSet().getInt("qt_2")));
        }
        return recipe;
    }

    /**
     *      Mini-lib pour définir les items plus simplement !
     */
    private ItemStack setItem(String material, String name,String enchant, String lore, int amount){
        ItemStack item = new ItemStack(Material.valueOf(material),amount);
        ItemMeta data = item.getItemMeta();
        if (data==null) return item;
        if (name!=null){
            data.setDisplayName(name);
        }
        if (enchant!=null){
            String[] enchants = enchant.split(",");
            for (int i=1;i<=enchants.length;i++){
                String[] ench = enchants[i-1].split(":");
                data.addEnchant(Objects.requireNonNull(Enchantment.getByKey(NamespacedKey.minecraft(ench[0].toLowerCase()))),
                        Integer.parseInt(ench[1]),true);
            }
        }
        if (lore!=null){
            List<String> lores = Arrays.asList(lore.split("%nl%"));
            data.setLore(lores);
        }
        item.setItemMeta(data);
        return item;
    }
}
