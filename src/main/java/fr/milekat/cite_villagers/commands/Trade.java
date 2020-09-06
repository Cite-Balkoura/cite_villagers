package fr.milekat.cite_villagers.commands;

import fr.milekat.cite_core.MainCore;
import fr.milekat.cite_villagers.MainVillager;
import fr.milekat.cite_villagers.engine.Refresh;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import static fr.milekat.cite_villagers.engine.Refresh.setLockTrade;

public class Trade implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("trade")){
            // Lock des trades
            if (args[0].equalsIgnoreCase("on")){
                MainVillager.manuLockTrades = true;
                setLockTrade();
                return true;
            // UnLock des trades
            } else if (args[0].equalsIgnoreCase("off")) {
                MainVillager.manuLockTrades = false;
                return true;
            // Force refresh des trades
            } else if (args[0].equalsIgnoreCase("refresh")) {
                Refresh.setLockTrade();
                new Refresh().resetTrades();
                MainVillager.autoLockTrades = false;
                sender.sendMessage(MainCore.prefixCmd + "§6Mise à jour des échanges §2terminée§6.");
                return true;
            /*
            // Refresh des trades + update de la bourse -> Non conservé pour la prochaine cité.
            } else if (args[0].equalsIgnoreCase("reboot")) {
                if (!sender.hasPermission("admin.trade.reboot")) {
                    sender.sendMessage(MainCore.prefixCmd + "§cCommande admin !");
                    return true;
                }
                Refresh refresh = new Refresh();
                setLockTrade();
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        refresh.updateBourse();
                        refresh.resetTrades();
                    }
                }.runTaskAsynchronously(mainVillager);
                MainVillager.autoLockTrades = false;
                sender.sendMessage(MainCore.prefixCmd + "§6Mise à jour des échanges §2terminée§6.");
                return true;*/
            }
        }
        return false;
    }


}