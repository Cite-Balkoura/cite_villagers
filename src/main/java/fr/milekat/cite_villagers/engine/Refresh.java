package fr.milekat.cite_villagers.engine;

import fr.milekat.cite_core.MainCore;
import fr.milekat.cite_libs.MainLibs;
import fr.milekat.cite_libs.utils_tools.DateMilekat;
import fr.milekat.cite_libs.utils_tools.ItemParser;
import fr.milekat.cite_villagers.MainVillager;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;

public class Refresh {
    private int lastDayOfWeek = 10;
    private final MainVillager mainVillager = MainVillager.getInstance();

    /**
     *      Engine pour update les trades toutes les nuits MC
     */
    public void Trade(){
        new BukkitRunnable() {
            @Override
            public void run() {
                Calendar calendar = Calendar.getInstance();
                calendar.setTime(new Date());
                int currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
                if (currentDayOfWeek!=lastDayOfWeek) {
                    Bukkit.getScheduler().runTask(mainVillager, () -> {
                        setLockTrade();
                        updateBlackMarketPos(currentDayOfWeek);
                        resetTrades();
                        MainVillager.autoLockTrades = false;
                    });
                    //updateBourse(); -> Non conservé pour la prochaine cité.
                }
                lastDayOfWeek = currentDayOfWeek;
            }
        }.runTaskTimerAsynchronously(mainVillager,0,600);
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

    /**
     *      Re-paramettre tous les trades par rapport au valeurs SQL !
     */
    public void resetTrades(){
        Connection connection = MainLibs.getSql();
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
        Connection connection = MainLibs.getSql();
        try {
            PreparedStatement q = connection.prepareStatement("SELECT " +
                    "trade_id, " +
                    "mat_1.material as 1_item, " +
                    "mat_1.name as 1_name, " +
                    "mat_1.enchantment as 1_ench, " +
                    "mat_1.lore as 1_lore, " +
                    "qt_1, " +
                    "mat_2.material as 2_item, " +
                    "mat_2.name as 2_name, " +
                    "mat_2.enchantment as 2_ench, " +
                    "mat_2.lore as 2_lore, " +
                    "qt_2, " +
                    "mat_r.material as r_item, " +
                    "mat_r.name as r_name, " +
                    "mat_r.enchantment as r_ench, " +
                    "mat_r.lore as r_lore, " +
                    "qt_r, " +
                    "max_uses " +
                    "FROM `" + MainCore.SQLPREFIX + "trade_liste` trade " +
                    "LEFT JOIN `" + MainCore.SQLPREFIX + "material_liste` mat_1 ON trade.mat_1 = mat_1.item_id " +
                    "LEFT JOIN `" + MainCore.SQLPREFIX + "material_liste` mat_2 ON trade.mat_2 = mat_2.item_id " +
                    "LEFT JOIN `" + MainCore.SQLPREFIX + "material_liste` mat_r ON trade.mat_r = mat_r.item_id " +
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
                ItemParser.setItem(q.getResultSet().getString("r_item")
                        , q.getResultSet().getString("r_name")
                        , q.getResultSet().getString("r_ench")
                        , q.getResultSet().getString("r_lore")
                        , q.getResultSet().getInt("qt_r"))
                        , q.getResultSet().getInt("max_uses"));
        recipe.addIngredient(
                ItemParser.setItem(q.getResultSet().getString("1_item")
                        , q.getResultSet().getString("1_name")
                        , q.getResultSet().getString("1_ench")
                        , q.getResultSet().getString("1_lore")
                        , q.getResultSet().getInt("qt_1")));
        if (q.getResultSet().getString("qt_2") != null) {
            recipe.addIngredient(ItemParser.setItem(q.getResultSet().getString("2_item")
                    , q.getResultSet().getString("2_name")
                    , q.getResultSet().getString("2_ench")
                    , q.getResultSet().getString("2_lore")
                    , q.getResultSet().getInt("qt_2")));
        }
        return recipe;
    }

    /**
     *      Téléporte le NPC#ID:1 à la pos du jour
     */
    @SuppressWarnings("deprecation")
    private static void updateBlackMarketPos(int currentDayOfWeek) {
        Connection connection = MainLibs.getSql();
        try {
            PreparedStatement q = connection.prepareStatement("SELECT " +
                    "`pos`, `TEXTURE_PROPERTIES`, `TEXTURE_PROPERTIES_SIGN` " +
                    "FROM `balkoura_blackmarket` WHERE `day` = ?;");
            q.setInt(1, currentDayOfWeek);
            q.execute();
            q.getResultSet().last();
            int[] rawpos =  Arrays.stream(q.getResultSet().getString("pos").split(";"))
                    .mapToInt(Integer::parseInt)
                    .toArray();
            NPC npc = CitizensAPI.getNPCRegistry().getById(1);
            npc.data().setPersistent(NPC.PLAYER_SKIN_TEXTURE_PROPERTIES_METADATA,
                    q.getResultSet().getString("TEXTURE_PROPERTIES"));
            npc.data().setPersistent(NPC.PLAYER_SKIN_TEXTURE_PROPERTIES_SIGN_METADATA,
                    q.getResultSet().getString("TEXTURE_PROPERTIES_SIGN"));
            npc.teleport(new Location(Bukkit.getWorld("world"),rawpos[0],rawpos[1],rawpos[2],rawpos[3],rawpos[4]),
                    PlayerTeleportEvent.TeleportCause.PLUGIN);
            q.close();
        } catch (SQLException | NullPointerException throwables) {
            Bukkit.getLogger().warning(MainVillager.prefixConsole + "Erreur lors de l'update de la pos BlackMarket.");
            throwables.printStackTrace();
        }
    }
}
