package net.tfminecraft;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/** 
 * MAIN
 */
public class ChestLocking extends JavaPlugin implements Listener {
    
    ChestDatabase db = new ChestDatabase();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
    }

    /**
     * Function which handles right clicking chests.
     * @param e
     */
   @EventHandler
	public void openMenuEvent(PlayerInteractEvent e) {
        if(!e.getAction().equals(Action.RIGHT_CLICK_BLOCK)) return;
        if(!e.getClickedBlock().getType().equals(Material.CHEST)) return;

        // Three functionalities: 
        // 1 - If the item in hand is a key, lock/unlock the chest.
        // 2 - If the item in hand is a lockpick, trigger lockpicking event.
        // 3 - Otherwise, check permissions & open chest.

        // TODO:
        // Handle double chests
        // Handle faction permissions

        // get info about the player AND the chest
        Player p = e.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();

        Block b = e.getClickedBlock();

        // We are trying to LOCK the chest
        if(hand.getType().equals(Material.IRON_INGOT)) {
            e.setCancelled(true);

            // Is this chest locked alrdy?
            LockedChest c = db.getChest(b.getLocation()); 
            if (c == null) {
                // Save the chest info
                c = new LockedChest(p.getUniqueId().toString(), "", b.getLocation());
                db.saveChest(c);
                p.sendMessage(ChatColor.GOLD + "Successfully locked the chest!");
            } else {

                if (p.getUniqueId().toString() != null && c.canAccessChest(p.getUniqueId().toString())) {
                    db.deleteChest(b.getLocation());
                    p.sendMessage(ChatColor.LIGHT_PURPLE + "The locked chest has been unlocked!");
                    e.setCancelled(true);
                } else {
                    p.sendMessage(ChatColor.DARK_RED + "Sorry, you can't lock a locked chest!");
                }

            }
                                

        } else {
            // normal chest opening, unless its locked!
            LockedChest c = db.getChest(b.getLocation()); 
            if (c != null) {

                // if the user does not have access, close the event
                if (p.getUniqueId().toString() != null && !c.canAccessChest(p.getUniqueId().toString())) {
                    p.sendMessage(ChatColor.DARK_RED + "Sorry, you can't open a locked chest!");
                    e.setCancelled(true);
                }
                
            }
            return;
        }

    }

    @EventHandler
	public void breakChest(BlockBreakEvent e) {
		Block b = e.getBlock();
		String type = e.getBlock().getType().toString();
		if(!b.getType().toString().contains("CHEST")) return;
		LockedChest c = db.getChest(b.getLocation());
		if(c != null) {

            // Is the person breaking the chest the owner/in faction?
            Player p = e.getPlayer();
            if (p.getUniqueId().toString() != null && !c.canAccessChest(p.getUniqueId().toString())) {
                p.sendMessage(ChatColor.DARK_RED + "Sorry, you can't break a locked chest!");
                e.setCancelled(true);
            }

            // do faction later


			new BukkitRunnable()
			{
				public void run()
			    {
					if(e.getBlock().getLocation().getBlock().getType().toString().equals(type)) return;
					db.deleteChest(b.getLocation());
			    }
			}.runTaskLater(this,5L);
		}
	}

}