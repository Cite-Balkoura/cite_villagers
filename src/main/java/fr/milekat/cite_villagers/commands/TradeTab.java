package fr.milekat.cite_villagers.commands;

import fr.milekat.cite_core.MainCore;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TradeTab implements TabCompleter {
    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (sender instanceof Player) {
            //Player player = (Player) sender;
            if (cmd.getName().equalsIgnoreCase("trade")) {
                //ArrayList<String> arg1 = new ArrayList<>(Arrays.asList("on", "off", "refresh"));
                /*  -> Non conservé pour la prochaine cité.
                if (player.hasPermission("admin.trade.reboot")) {
                    arg1.add("reboot");
                }*/
                /*final ArrayList<String> completions = new ArrayList<>();
                StringUtil.copyPartialMatches(args[0], arg1, completions);
                Collections.sort(completions);*/
                return MainCore.getTabArgs(args[0],new ArrayList<>(Arrays.asList("on", "off", "refresh")));
            }
        }
        return null;
    }
}
