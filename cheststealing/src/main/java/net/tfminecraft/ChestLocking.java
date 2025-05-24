package net.tfminecraft;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Chest.Type;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** 
 * MAIN
 */
public class ChestLocking extends JavaPlugin implements Listener {

    private final Map<UUID, Block> lockpickingSessions = new HashMap<>();
    private ChestDatabase db = new ChestDatabase();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
    }

    /**
     * Function which handles opening clicking chests.
     * @param e
     */
    @EventHandler
	public void openMenuEvent(PlayerInteractEvent e) {
        if(!e.getAction().equals(Action.RIGHT_CLICK_BLOCK)) return;
        if(e.getHand() != EquipmentSlot.HAND) return;
        if(!e.getClickedBlock().getType().equals(Material.CHEST)) return;
        
        // Three functionalities: 
        // 1 - If the item in hand is a key, lock/unlock the chest.
        // 2 - If the item in hand is a lockpick, trigger lockpicking event.
        // 3 - Otherwise, check permissions & open chest.

        // TODO:
        // Handle faction permissions
        // MCMMO item for lockpicking
        // Roguery attribute for lockpicking

        // get info about the player AND the chest
        Player p = e.getPlayer();

        ItemStack hand = p.getInventory().getItemInMainHand();

        Block b = e.getClickedBlock();

        // get the chest we're trying to open/lockpick
        LockedChest c = db.getChest(b.getLocation()); 

        // if are trying to LOCK the chest
        if(hand.getType().equals(Material.IRON_INGOT) && p.isSneaking()) {
            e.setCancelled(true);

            if (c == null) {
                // locking chests
                BlockState chestState = b.getState();
                if (chestState instanceof Chest chest) {
                    Inventory inventory = chest.getInventory();

                    if (inventory instanceof DoubleChestInventory doubleChestInventory) {
                        // we are dealing with a double chest
                        DoubleChest doubleChest = (DoubleChest) doubleChestInventory.getHolder();
                        if (doubleChest != null) {
                            Chest leftChest = (Chest) doubleChest.getLeftSide();
                            Chest rightChest = (Chest) doubleChest.getRightSide();

                            LockedChest lc = new LockedChest(p.getUniqueId().toString(), "", leftChest.getLocation());
                            db.saveChest(lc);
                            LockedChest rc = new LockedChest(p.getUniqueId().toString(), "", rightChest.getLocation());
                            db.saveChest(rc);

                            p.sendMessage(ChatColor.GOLD + "Successfully locked the double chest!");
                        }
                    } else {
                        // single chest case
                        c = new LockedChest(p.getUniqueId().toString(), "", b.getLocation());
                        db.saveChest(c);
                        p.sendMessage(ChatColor.GOLD + "Successfully locked the chest!");
                    }
                }

            } else {
                // unlocking chests
                if (p.getUniqueId().toString() != null && c.canAccessChest(p.getUniqueId().toString())) {
                    BlockState chestState = b.getState();
                    if (chestState instanceof Chest chest) {                                    
                        Inventory inventory = chest.getInventory();                                    
                        if (inventory instanceof DoubleChestInventory doubleChestInventory) {                                    
                            // we are dealing with a double chest                                    
                            DoubleChest doubleChest = (DoubleChest) doubleChestInventory.getHolder();                                    
                            if (doubleChest != null) {                                    
                                Chest leftChest = (Chest) doubleChest.getLeftSide();                                    
                                Chest rightChest = (Chest) doubleChest.getRightSide();

                                db.deleteChest(leftChest.getLocation());                                    
                                db.deleteChest(rightChest.getLocation());

                                p.sendMessage(ChatColor.LIGHT_PURPLE + "The locked double chest has been unlocked!");       
                                e.setCancelled(true);                      
                            }                                    
                        } else {                                    
                            // single chest case                                    
                            db.deleteChest(b.getLocation());
                            p.sendMessage(ChatColor.LIGHT_PURPLE + "The locked chest has been unlocked!");
                            e.setCancelled(true);                                 
                        }                                    
                    }                       
                } else {
                    p.sendMessage(ChatColor.DARK_RED + "Sorry, you can't lock a locked chest!");
                }

            }
        } else if (c != null && 
                    hand.getType().equals(Material.SHEARS) && 
                    p.isSneaking()
        ) {

            if (c.canAccessChest(p.getUniqueId().toString())) {
                p.sendMessage(ChatColor.DARK_RED + "Sorry, you can't lockpick your own chest!");
                return;
            } else {
                // initiate lockpicking
                p.sendMessage(ChatColor.ITALIC + "" + ChatColor.DARK_RED + "Lockpicking in progress...");
                e.setCancelled(true);
                this.lockpickChest(e);
            }

        } else {
            // normal chest opening, unless its locked!
            if (c != null) {
                // if the user does not have access, close the event
                if (p.getUniqueId().toString() != null && !c.canAccessChest(p.getUniqueId().toString())) {
                    p.sendMessage(ChatColor.DARK_RED + "Sorry, you can't open a locked chest!");
                    e.setCancelled(true);
                } else {
                    b.setMetadata(getName(), null);
                }
                
            }
        }

    }

    /**
     * Initiate the lockpicking logic
     */
    private void lockpickChest(PlayerInteractEvent e) {
        Block b = e.getClickedBlock();
        Player p = e.getPlayer();

        BlockState state = b.getState();
        if (!(state instanceof Chest chest)) return;

        Inventory chestInv = chest.getInventory();
        int invSize = chestInv.getSize();

        Inventory lockpickInv = Bukkit.createInventory(null, invSize, ChatColor.DARK_GRAY + "Lockpicking...");

        ItemStack unkPanes = new ItemStack(Material.GRAY_STAINED_GLASS_PANE, 1);
        ItemMeta unkPaneMeta = unkPanes.getItemMeta();
        unkPaneMeta.setDisplayName("???");
        unkPanes.setItemMeta(unkPaneMeta);

        ItemStack curPanes = new ItemStack(Material.BLACK_STAINED_GLASS_PANE, 1);
        ItemMeta curPanesMeta = curPanes.getItemMeta();
        curPanesMeta.setDisplayName("Searching");
        curPanes.setItemMeta(curPanesMeta);

        for (int i = 0; i < chestInv.getSize(); i++) {
            if (i == 0) {
                lockpickInv.setItem(i, curPanes);
            } else {
                lockpickInv.setItem(i, unkPanes);
            }
        }

        p.openInventory(lockpickInv);

        // Start searching
        lockpickingSessions.put(p.getUniqueId(), b);

        new BukkitRunnable() {
            int slot = 0;

            @Override
            public void run() {
                if (slot >= invSize) {
                    this.cancel();
                    return;
                }

                ItemStack realItem = chestInv.getItem(slot);

                // 40% chance to reveal the real item
                // we are changing this tho.... based on roguery?? dunno * 1/roguery attribute or smth
                if (realItem != null && Math.random() < 0.4) {
                    lockpickInv.setItem(slot, realItem.clone());
                } else {
                    // Red pane means fail
                    ItemStack failedPane = new ItemStack(Material.RED_STAINED_GLASS_PANE, 1);
                    ItemMeta failMeta = failedPane.getItemMeta();
                    failMeta.setDisplayName(ChatColor.RED + "Nothing found.");
                    failedPane.setItemMeta(failMeta);
                    lockpickInv.setItem(slot, failedPane);
                }

                lockpickInv.setItem(slot+1, curPanes);

                slot++;
            }
        }.runTaskTimer(this, 10L, 5L); // Start after 10 ticks, run every 5 ticks
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        Player player = (Player) e.getWhoClicked();
        UUID uuid = player.getUniqueId();

        // Is this a lockpicking session?
        if (!lockpickingSessions.containsKey(uuid)) return;

        Inventory clickedInv = e.getClickedInventory();
        if (clickedInv == null || !e.getView().getTitle().equals(ChatColor.DARK_GRAY + "Lockpicking...")) return;

        ItemStack currentItem = e.getCurrentItem();
        if (currentItem == null) return;

        Material mat = currentItem.getType();

        // Prevent removing panes
        if (mat == Material.GRAY_STAINED_GLASS_PANE || 
            mat == Material.RED_STAINED_GLASS_PANE ||
            mat == Material.BLACK_STAINED_GLASS_PANE
        ) {
            e.setCancelled(true);
            return;
        }

        // If it's a valid item, wait for them to take it out
        // USE OF AI GENERATED CODE HERE..... maybe needs a rewrite (but seems fine)
        Bukkit.getScheduler().runTaskLater(this, () -> {
            Block chestBlock = lockpickingSessions.get(uuid);
            if (!(chestBlock.getState() instanceof Chest chest)) return;

            Inventory chestInv = chest.getInventory();
            Inventory fakeInv = e.getView().getTopInventory();

            for (int i = 0; i < fakeInv.getSize(); i++) {
                ItemStack fakeItem = fakeInv.getItem(i);
                ItemStack realItem = chestInv.getItem(i);

                boolean isFakePane = isDummyPane(fakeItem);

                // Only sync if:
                // - Slot was emptied (fake is null, real was not)
                // - Slot now has a real item (not a dummy pane)
                if ((fakeItem == null && realItem != null) || (!isFakePane && !itemsEqual(fakeItem, realItem))) {
                    chestInv.setItem(i, fakeItem); // could be null (taken) or actual item
                }
            }
        }, 1L); // Give inventory time to update
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {
        lockpickingSessions.remove(e.getPlayer().getUniqueId());
    }

    private boolean isDummyPane(ItemStack item) {
        if (item == null) return false;
        Material mat = item.getType();
        return mat == Material.GRAY_STAINED_GLASS_PANE ||
            mat == Material.RED_STAINED_GLASS_PANE ||
            mat == Material.BLACK_STAINED_GLASS_PANE;
    }

    private boolean itemsEqual(ItemStack a, ItemStack b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.isSimilar(b);
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

    /**
     * Deals with if you place a chest next to a locked chest.
     * @param e the event of placing a block 
     */
    @EventHandler
    public void onChestPlace(BlockPlaceEvent e) {
        Player player = e.getPlayer();
        Block block = e.getBlockPlaced();

        if (block.getType() != Material.CHEST) return;

        Block[] adjacentBlocks = new Block[] {
            block.getRelative(BlockFace.EAST),
            block.getRelative(BlockFace.WEST),
            block.getRelative(BlockFace.NORTH),
            block.getRelative(BlockFace.SOUTH)
        };

        for (Block adjacent : adjacentBlocks) {
            if (adjacent.getType() == Material.CHEST) {
                // check if the adjacent chest is locked 
                if (db.getChest(adjacent.getLocation()) != null) {
                    // cancel merge by forcing both chests to be single
                    Bukkit.getScheduler().runTaskLater(this, () -> {
                        // set placed chest to SINGLE
                        BlockData placedData = block.getBlockData();
                        if (placedData instanceof org.bukkit.block.data.type.Chest placedChestData) {
                            placedChestData.setType(org.bukkit.block.data.type.Chest.Type.SINGLE);
                            block.setBlockData(placedChestData, false);
                        }

                        // set adjacent chest to SINGLE,
                        // TODO - ONLY SET IT TO SINGLE IF WE KNOW ITS PART OF THE CURRENT CHESTS DOUBLE CHEST
                        // E.G IF IT HAS CREATED A MERGE

                        Chest adjacentChest = (Chest) adjacent;
                        Inventory inventory = adjacentChest.getInventory();                                    
                        if (inventory instanceof DoubleChestInventory doubleChestInventory) {                                    
                            // we are dealing with a double chest                                    
                            DoubleChest doubleChest = (DoubleChest) doubleChestInventory.getHolder();                                    
                            if (doubleChest != null) {                                    
                                Chest leftChest = (Chest) doubleChest.getLeftSide();                                    
                                Chest rightChest = (Chest) doubleChest.getRightSide();

                                // TODO ... maybe here something like
                                // make sure the chest is not part of a locked chest or smth
                                if (!((Chest) block == rightChest || (Chest) block == leftChest)) {
                                    // original code
                                    BlockData adjacentData = adjacent.getBlockData();
                                    if (adjacentData instanceof org.bukkit.block.data.type.Chest adjacentChestData) {
                                        adjacentChestData.setType(org.bukkit.block.data.type.Chest.Type.SINGLE);
                                        adjacent.setBlockData(adjacentChestData, false);
                                    }
                                }
                                
                            }
                        }

                    }, 1L);
                    // 1L ... because it happens on the same tick, best it can be is the tick afterwards.
                    // dont say anything to the user. think this is best
                    break;
                }
            }
        }

    }


}