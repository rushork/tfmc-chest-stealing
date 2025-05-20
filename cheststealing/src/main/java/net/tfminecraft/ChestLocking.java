package net.tfminecraft;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

/** 
 * MAIN
 */
public class ChestLocking extends JavaPlugin {
    
    /**
     * Function which handles right clicking chests.
     * @param e
     */
   @EventHandler
	public void openMenuEvent(PlayerInteractEvent e) {
        if(!e.getAction().equals(Action.RIGHT_CLICK_BLOCK)) return;

        // Three functionalities:
        // 1 - If the item in hand is a key, lock/unlock the chest.
        // 2 - If the item in hand is a lockpick, trigger lockpicking event.
        // 3 - Otherwise, check permissions & open chest.

        System.out.println(e.getClickedBlock().getType());
    }

}