package net.tfminecraft;

import java.util.List;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

public class CommandManager implements Listener, CommandExecutor{
    public String cmd1 = "chlock";

    private ChestDatabase db = new ChestDatabase();
    
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if(cmd.getName().equalsIgnoreCase(cmd1)) {
			if(args.length > 0 && args[0].equalsIgnoreCase("find")) {
				if(sender instanceof Player) {
                    Player p = (Player) sender;
                    List<LockedChest> list = db.getAllChestsForUser(p.getUniqueId());

                    if (list.isEmpty()) {
                        p.sendMessage(ChatColor.GOLD + "You have no locked chests!");
                        return true;
                    }
                    
                    p.sendMessage(ChatColor.GOLD + "Your locked chests are located... (X/Y/Z)");
                    Integer count = 0;
                    for (LockedChest lockedChest : list) {
                        count++;
                        p.sendMessage(ChatColor.GOLD + "Chest " + count + ": " + lockedChest.getX() + "/" + lockedChest.getY() +"/" + lockedChest.getZ());
                    }
					return true;
				}
			}
		}
		return false;
    }

}
