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
import org.bukkit.Sound;
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
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
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

    private final Map<UUID, Block> lockpickingSessions = new HashMap<>(); // stores players and which block theyre lockpickig
    private final Map<UUID, BukkitRunnable> activeLockpickingTasks = new HashMap<>(); // stores players and which task is running the lockpicking
    private final Map<Location, Long> chestCooldowns = new HashMap<>(); // stores chests and how recently they have been lockpicked
    private ChestDatabase db = new ChestDatabase();

    @Override
    public void onEnable() {
        saveDefaultConfig();
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

        // we always want to cancel
        e.setCancelled(true);

        if (lockpickingSessions.containsValue(b)) {
            p.sendMessage(ChatColor.DARK_RED + "Someone is already lockpicking this chest!");
            return;
        }

        // CHEST PICKING COOLDOWNS
        long now = System.currentTimeMillis();
        Location chestLoc = b.getLocation();

        if (chestCooldowns.containsKey(chestLoc)) {
            long lastTime = chestCooldowns.get(chestLoc);
            int cooldown = getConfig().getInt("lockpicking.cooldown");
            if (now - lastTime < cooldown) { // 10 seconds cooldown
                long secondsLeft = (cooldown - (now - lastTime)) / 1000;
                p.sendMessage(ChatColor.RED + "This chest has been lockpicked recently. Try again in " + secondsLeft + " seconds.");
                return;
            } else {
                chestCooldowns.remove(chestLoc);
            }
        }

        // Set cooldown start time
        chestCooldowns.put(chestLoc, now);

        
        p.sendMessage(ChatColor.ITALIC + "" + ChatColor.DARK_RED + "Lockpicking in progress...");

        BlockState state = b.getState();
        if (!(state instanceof Chest chest)) return;

        Inventory chestInv = chest.getInventory();
        int invSize = chestInv.getSize();

        Inventory lockpickInv = Bukkit.createInventory(null, invSize, ChatColor.DARK_RED + "Lockpicking...");

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

        // LOCKPICKING TASK
        // TODO - MAKE THIS CONFIG FRIENDLY
        lockpickingSessions.put(p.getUniqueId(), b);

        BukkitRunnable task = new BukkitRunnable() {
            int slot = 0;
            @Override
            public void run() {
                if (b.getType() != Material.CHEST) {
                    this.cancel();
                    lockpickingSessions.remove(p.getUniqueId());
                    activeLockpickingTasks.remove(p.getUniqueId());
                    return;
                }

                if (slot >= invSize) {
                    this.cancel();
                    lockpickingSessions.remove(p.getUniqueId());
                    activeLockpickingTasks.remove(p.getUniqueId());
                    return;
                }

                p.playSound(p.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 0.4f, 0.8f);
                p.playSound(p.getLocation(), Sound.BLOCK_LEVER_CLICK, 0.3f, 1.2f);

                ItemStack realItem = chestInv.getItem(slot);

                int chance = getConfig().getInt("lockpicking.success-rate");
                if (realItem != null && Math.random() < chance) {
                    lockpickInv.setItem(slot, realItem.clone());
                } else {
                    ItemStack failedPane = new ItemStack(Material.RED_STAINED_GLASS_PANE, 1);
                    ItemMeta failMeta = failedPane.getItemMeta();
                    failMeta.setDisplayName(ChatColor.RED + "Nothing found.");
                    failedPane.setItemMeta(failMeta);
                    lockpickInv.setItem(slot, failedPane);
                }

                if (slot != invSize - 1) {
                    lockpickInv.setItem(slot + 1, curPanes);
                }
                slot++;
            }
        };

        activeLockpickingTasks.put(p.getUniqueId(), task);
        int delay = getConfig().getInt("lockpicking.delay");
        task.runTaskTimer(this, delay/2, delay);
    }

    /**
     * Deals with when the lockpicking session is closed
     * @param event
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        UUID uuid = player.getUniqueId();

        // If the player was lockpicking
        if (lockpickingSessions.containsKey(uuid)) {
            // Cancel their lockpicking task
            BukkitRunnable task = activeLockpickingTasks.remove(uuid);
            if (task != null) {
                task.cancel();
            }

            lockpickingSessions.remove(uuid);
            player.sendMessage(ChatColor.RED + "You stopped lockpicking.");
        }
    }

    /**
     * Deals when something is picked up from the lockpicking session
     * @param e
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        Player player = (Player) e.getWhoClicked();
        UUID uuid = player.getUniqueId();

        // Is this a lockpicking session?
        if (!lockpickingSessions.containsKey(uuid)) return;

        Inventory clickedInv = e.getClickedInventory();
        if (clickedInv == null || !e.getView().getTitle().equals(ChatColor.DARK_RED + "Lockpicking...")) return;

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
     * Deals with people trying to take stuff out of chests with hoppers.
     * @param e
     */
    @EventHandler
    public void onChestRemove(InventoryMoveItemEvent e) {
        // if its not from a chest into a hopper, return
        if(e.getInitiator().getType() != InventoryType.HOPPER) return;
        if(e.getSource().getType() != InventoryType.CHEST) return;
        LockedChest c = db.getChest(e.getSource().getLocation());

        // allow unlocked chests
        if (c == null) return;

        // otherwise cancel the event
        e.setCancelled(true);
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

        // praise be chatgpt for this logic
        Bukkit.getScheduler().runTaskLater(this, () -> {
            Block placedBlock = block;
            if (!(placedBlock.getState() instanceof Chest placedChestState)) return;

            Inventory placedInv = placedChestState.getInventory();
            if (!(placedInv instanceof DoubleChestInventory doubleChestInv)) return;

            DoubleChest doubleChest = (DoubleChest) doubleChestInv.getHolder();
            if (doubleChest == null) return;

            Chest left = (Chest) doubleChest.getLeftSide();
            Chest right = (Chest) doubleChest.getRightSide();

            Location placedLoc = placedBlock.getLocation();
            Location leftLoc = left.getBlock().getLocation();
            Location rightLoc = right.getBlock().getLocation();

            boolean isPlacedChestInvolved = placedLoc.equals(leftLoc) || placedLoc.equals(rightLoc);
            boolean isAdjacentChestInvolved = false;

            for (Block adjacent : adjacentBlocks) {
                Location adjacentLoc = adjacent.getLocation();
                if (adjacentLoc.equals(leftLoc) || adjacentLoc.equals(rightLoc)) {
                    isAdjacentChestInvolved = true;
                    break;
                }
            }

            // Only cancel if this chest and one of the adjacent chests formed the double
            if (isPlacedChestInvolved && isAdjacentChestInvolved) {
                // Check if either side is locked
                if (db.getChest(leftLoc) != null || db.getChest(rightLoc) != null) {
                    // Cancel merge
                    BlockData placedData = placedBlock.getBlockData();
                    if (placedData instanceof org.bukkit.block.data.type.Chest placedChestData) {
                        placedChestData.setType(org.bukkit.block.data.type.Chest.Type.SINGLE);
                        placedBlock.setBlockData(placedChestData, false);
                    }

                    Block adjacentBlock = placedLoc.equals(leftLoc) ? right.getBlock() : left.getBlock();
                    BlockData adjacentData = adjacentBlock.getBlockData();
                    if (adjacentData instanceof org.bukkit.block.data.type.Chest adjacentChestData) {
                        adjacentChestData.setType(org.bukkit.block.data.type.Chest.Type.SINGLE);
                        adjacentBlock.setBlockData(adjacentChestData, false);
                    }
                }
            }
        }, 1L);

    }


}