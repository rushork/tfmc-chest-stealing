package net.tfminecraft;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import me.Plugins.TLibs.TLibs;
import me.Plugins.TLibs.Enums.APIType;
import me.Plugins.TLibs.Objects.API.ItemAPI;
import net.Indyuce.mmocore.api.player.PlayerData;
import net.Indyuce.mmocore.api.player.attribute.PlayerAttributes.AttributeInstance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * MAIN
 */
public class ChestLocking extends JavaPlugin implements Listener {

    private final Map<Location, LockedChest> lockedChestCache = new HashMap<>();

    private final Map<UUID, Block> lockpickingSessions = new HashMap<>(); // stores players and which block theyre lockpickig
    private final Map<UUID, BukkitRunnable> activeLockpickingTasks = new HashMap<>(); // stores players and which task is running the lockpicking
    private final Map<Location, Long> chestCooldowns = new HashMap<>(); // stores chests and how recently they have been lockpicked
    private final Map<UUID, List<Integer>> lockpickingSlotOrders = new HashMap<>(); // stores lockpicking slot orders

    private ChestDatabase db = new ChestDatabase();
    CommandManager commands = new CommandManager();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        getCommand(commands.cmd1).setExecutor(commands);

        //db load all
        this.lockedChestCache.putAll(db.loadAllChests());
    }

    /**
     * Function which handles opening/clicking chests.
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
        LockedChest c = lockedChestCache.get(b.getLocation()); 


        // check if the item is an mmoitem
        ItemAPI api = (ItemAPI) TLibs.getApiInstance(APIType.ITEM_API);

        // if are trying to LOCK the chest
        if(p.isSneaking() && api.getChecker().checkItemWithPath(hand, "m.tools.LOCKING_TOOL")) {
 
            e.setCancelled(true);

            if (c == null) {
                // locking chests
                BlockState chestState = b.getState();
                if (chestState instanceof Chest chest) {
                    Inventory inventory = chest.getInventory();
                    if (inventory instanceof DoubleChestInventory doubleChestInventory) {
                        // we are dealing with a double chest

                        Integer lockedChestCount = db.getChestCountUser(p.getUniqueId());
                        if ((lockedChestCount+2) > getConfig().getInt("chest.max-chests")) {
                            p.sendMessage(ChatColor.DARK_RED + "Sorry, you have locked too many chests!");
                            return;
                        }

                        DoubleChest doubleChest = (DoubleChest) doubleChestInventory.getHolder();
                        if (doubleChest != null) {
                            Chest leftChest = (Chest) doubleChest.getLeftSide();
                            Chest rightChest = (Chest) doubleChest.getRightSide();

                            //saving both sides
                            LockedChest lc = new LockedChest(p.getUniqueId().toString(), p.getName(), leftChest.getLocation());
                            db.saveChest(lc);
                            lockedChestCache.put(lc.getLocation(), lc);
                            LockedChest rc = new LockedChest(p.getUniqueId().toString(), p.getName(), rightChest.getLocation());
                            db.saveChest(rc);
                            lockedChestCache.put(rc.getLocation(), rc);

                            p.sendMessage(ChatColor.GOLD + "Successfully locked the double chest!");
                        }
                    } else {
                        Integer lockedChestCount = db.getChestCountUser(p.getUniqueId());
                        if ((lockedChestCount+1) > getConfig().getInt("chest.max-chests")) {
                            p.sendMessage(ChatColor.DARK_RED + "Sorry, you have locked too many chests!");
                            return;
                        }

                        // single chest case
                        c = new LockedChest(p.getUniqueId().toString(), p.getName(), b.getLocation());
                        db.saveChest(c);
                        lockedChestCache.put(c.getLocation(), c);
                        p.sendMessage(ChatColor.GOLD + "Successfully locked the chest!");
                    }
                }

            } else {
                // unlocking chests
                if (p.getUniqueId().toString() != null && c.canAccessChest(p.getUniqueId().toString(), p.getName())) {
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
                                lockedChestCache.remove(leftChest.getLocation());                               
                                db.deleteChest(rightChest.getLocation());
                                lockedChestCache.remove(rightChest.getLocation());      

                                p.sendMessage(ChatColor.LIGHT_PURPLE + "The locked double chest has been unlocked!");       
                                e.setCancelled(true);                      
                            }                                    
                        } else {                                    
                            // single chest case                                    
                            db.deleteChest(b.getLocation());
                            lockedChestCache.remove(b.getLocation());      
                            p.sendMessage(ChatColor.LIGHT_PURPLE + "The locked chest has been unlocked!");
                            e.setCancelled(true);                                 
                        }                                    
                    }                       
                } else {
                    p.sendMessage(ChatColor.DARK_RED + "Sorry, you can't lock a locked chest!");
                }

            }
        } else if (c != null && 
                    p.isSneaking()
                    && api.getChecker().checkItemWithPath(hand, "m.tools.LOCKPICK")
        ) {

 
            if (c.canAccessChest(p.getUniqueId().toString(), p.getName())) {
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
                if (p.getUniqueId().toString() != null && !c.canAccessChest(p.getUniqueId().toString(), p.getName())) {
                    p.sendMessage(ChatColor.DARK_RED + "Sorry, you can't open a locked chest!");
                    e.setCancelled(true);
                }
                
            }
        }

    }

    @EventHandler
    public void ShearSheep (PlayerShearEntityEvent e) {

        Player p = e.getPlayer();

        ItemStack hand = p.getInventory().getItemInMainHand();
        // check if the shears is an mmoitem
        ItemAPI api = (ItemAPI) TLibs.getApiInstance(APIType.ITEM_API);
        if (api.getChecker().checkItemWithPath(hand, "m.tools.LOCKPICK")) {
            e.setCancelled(true);
        }
    }

    
    private void lockpickChest(PlayerInteractEvent e) {
        Block b = e.getClickedBlock();
        Player p = e.getPlayer();

        e.setCancelled(true);

        if (lockpickingSessions.containsValue(b)) {
            p.sendMessage(ChatColor.DARK_RED + "Someone is already lockpicking this chest!");
            return;
        }

        long now = System.currentTimeMillis();
        Location chestLoc = b.getLocation();
        if (chestCooldowns.containsKey(chestLoc)) {
            long lastTime = chestCooldowns.get(chestLoc);
            int cooldown = getConfig().getInt("lockpicking.cooldown");
            if (now - lastTime < cooldown) {
                long secondsLeft = (cooldown - (now - lastTime)) / 1000;
                p.sendMessage(ChatColor.RED + "Try again in " + secondsLeft + " seconds.");
                return;
            } else {
                // deal with double chests too
                BlockState chestState = b.getState();
                if (chestState instanceof Chest chest) {                                    
                    Inventory inventory = chest.getInventory();                                    
                    if (inventory instanceof DoubleChestInventory doubleChestInventory) {                                    
                        // we are dealing with a double chest                                    
                        DoubleChest doubleChest = (DoubleChest) doubleChestInventory.getHolder();                                    
                        if (doubleChest != null) {                                    
                            Chest leftChest = (Chest) doubleChest.getLeftSide();                                    
                            Chest rightChest = (Chest) doubleChest.getRightSide();

                            chestCooldowns.remove(leftChest.getLocation(), now);
                            chestCooldowns.remove(rightChest.getLocation(), now);
                        }
                    } else {
                        chestCooldowns.remove(chestLoc);
                    }
                }       
                chestCooldowns.remove(chestLoc);
            }
        }
        
        // deal with double chests too
        BlockState chestState = b.getState();
        if (chestState instanceof Chest chest) {                                    
            Inventory inventory = chest.getInventory();                                    
            if (inventory instanceof DoubleChestInventory doubleChestInventory) {                                    
                // we are dealing with a double chest                                    
                DoubleChest doubleChest = (DoubleChest) doubleChestInventory.getHolder();                                    
                if (doubleChest != null) {                                    
                    Chest leftChest = (Chest) doubleChest.getLeftSide();                                    
                    Chest rightChest = (Chest) doubleChest.getRightSide();

                    chestCooldowns.put(leftChest.getLocation(), now);
                    chestCooldowns.put(rightChest.getLocation(), now);
                }
            } else {
                chestCooldowns.put(chestLoc, now);
            }
        }                               


        p.sendMessage(ChatColor.ITALIC + "" + ChatColor.DARK_RED + "Lockpicking in progress...");

        BlockState state = b.getState();
        if (!(state instanceof Chest chest)) return;

        Inventory chestInv = chest.getInventory();
        int invSize = chestInv.getSize();
        Inventory lockpickInv = Bukkit.createInventory(null, invSize, ChatColor.DARK_RED + "Lockpicking...");

        NamespacedKey dummyKey = new NamespacedKey(this, "dummy");

        ItemStack unkPane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta unkMeta = unkPane.getItemMeta();
        unkMeta.setDisplayName("???");
        unkMeta.getPersistentDataContainer().set(dummyKey, PersistentDataType.BYTE, (byte) 1);
        unkPane.setItemMeta(unkMeta);

        ItemStack curPane = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta curMeta = curPane.getItemMeta();
        curMeta.setDisplayName("Searching");
        curMeta.getPersistentDataContainer().set(dummyKey, PersistentDataType.BYTE, (byte) 1);
        curPane.setItemMeta(curMeta);

        List<Integer> randomizedSlots = IntStream.range(0, invSize).boxed().collect(Collectors.toList());
        Collections.shuffle(randomizedSlots);
        lockpickingSlotOrders.put(p.getUniqueId(), randomizedSlots);

        boolean start = true;
        for (Integer slot : randomizedSlots) {
            lockpickInv.setItem(slot, start ? curPane : unkPane);
            start = false;
        }

        AttributeInstance dexterityAttr = PlayerData.get(p.getUniqueId()).getAttributes().getInstance("dexterity");
        int dexterity = dexterityAttr.getTotal(); // dexterity ranges from 0 to 40

        // linear interp for each one
        double minSuccess = getConfig().getDouble("lockpicking.success-rate.min", 0.4);
        double maxSuccess = getConfig().getDouble("lockpicking.success-rate.max", 0.9);
        double lockpickSuccessRate = minSuccess + ((maxSuccess - minSuccess) * dexterity / 40.0);

        double minBreak = getConfig().getDouble("lockpicking.break-chance.min", 0.01);
        double maxBreak = getConfig().getDouble("lockpicking.break-chance.max", 0.15);
        double lockpickBreakChance = maxBreak - ((maxBreak - minBreak) * dexterity / 40.0);

        long minDelay = getConfig().getLong("lockpicking.delay.min", 5L);
        long maxDelay = getConfig().getLong("lockpicking.delay.max", 20L);
        long lockpickDelay = maxDelay - (long)((maxDelay - minDelay) * dexterity / 40.0);


        p.openInventory(lockpickInv);
        lockpickingSessions.put(p.getUniqueId(), b);

        BukkitRunnable task = new BukkitRunnable() {
            int slot = 0;
            @Override
            public void run() {
                if (b.getType() != Material.CHEST) {
                    cleanup();
                    return;
                }

                if (slot >= invSize) {
                    cleanup();
                    return;
                }

                if (Math.random() < lockpickBreakChance) {
                    p.getInventory().setItemInMainHand(null);
                    p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
                    p.sendMessage(ChatColor.RED + "Your lockpick broke!");
                    cleanup();
                    return;
                }

                p.playSound(p.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 0.4f, 0.8f);
                p.playSound(p.getLocation(), Sound.BLOCK_LEVER_CLICK, 0.3f, 1.2f);

                int chestSlot = randomizedSlots.get(slot);
                ItemStack realItem = chestInv.getItem(chestSlot);

                if (realItem != null && Math.random() < lockpickSuccessRate) {
                    lockpickInv.setItem(chestSlot, realItem.clone());
                } else {
                    ItemStack failPane = new ItemStack(Material.RED_STAINED_GLASS_PANE);
                    ItemMeta failMeta = failPane.getItemMeta();
                    failMeta.setDisplayName(ChatColor.RED + "Nothing found.");
                    failMeta.getPersistentDataContainer().set(dummyKey, PersistentDataType.BYTE, (byte) 1);
                    failPane.setItemMeta(failMeta);
                    lockpickInv.setItem(chestSlot, failPane);
                }

                if (slot < invSize - 1) {
                    int nextSlot = randomizedSlots.get(slot + 1);
                    lockpickInv.setItem(nextSlot, curPane);
                }

                slot++;
            }

            void cleanup() {
                this.cancel();
                activeLockpickingTasks.remove(p.getUniqueId());
            }
        };

        activeLockpickingTasks.put(p.getUniqueId(), task);
        task.runTaskTimer(this, lockpickDelay / 2, lockpickDelay);
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

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        Player player = (Player) e.getWhoClicked();
        UUID uuid = player.getUniqueId();

        // Is this a lockpicking session?
        if (!lockpickingSessions.containsKey(uuid)) return;

        Inventory clickedInv = e.getClickedInventory();
        if (clickedInv == null || !e.getView().getTitle().equals(ChatColor.DARK_RED + "Lockpicking...")) return;

        ItemStack clickedItem = e.getCurrentItem();
        if (clickedItem == null || isDummyPane(clickedItem)) {
            e.setCancelled(true);
            return;
        }

        // Only allow left click in the top inventory
        if (e.getClickedInventory() == e.getView().getTopInventory()) {
            ClickType click = e.getClick();

            if (click == ClickType.LEFT) {
                // Give the item to the player
                HashMap<Integer, ItemStack> leftovers = player.getInventory().addItem(clickedItem.clone());

                // If it didn't fully fit, don't take it out
                if (!leftovers.isEmpty()) {
                    player.sendMessage(ChatColor.RED + "You don't have enough inventory space!");
                    e.setCancelled(true);
                    return;
                }

                // Remove from both GUI and real chest
                int slot = e.getSlot();
                clickedInv.setItem(slot, null);

                Block chestBlock = lockpickingSessions.get(uuid);
                if (chestBlock.getState() instanceof Chest chest) {
                    chest.getInventory().setItem(slot, null);
                }
            } else {
                e.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Only left-clicking is allowed!");
            }
        } else {
            // Prevent putting stuff in from player inventory
            e.setCancelled(true);
        }
    }



    private boolean isDummyPane(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;

        ItemMeta meta = item.getItemMeta();
        NamespacedKey key = new NamespacedKey(this, "dummy");

        return meta.getPersistentDataContainer().has(key, PersistentDataType.BYTE);
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
		LockedChest c = lockedChestCache.get(b.getLocation());
		if(c != null) {
            // Is the person breaking the chest the owner/in faction?
            Player p = e.getPlayer();
            if (p.getUniqueId().toString() != null && !c.canAccessChest(p.getUniqueId().toString(), p.getName())) {
                p.sendMessage(ChatColor.DARK_RED + "Sorry, you can't break a locked chest!");
                e.setCancelled(true);
            }

			new BukkitRunnable()
			{
				public void run()
			    {
					if(e.getBlock().getLocation().getBlock().getType().toString().equals(type)) return;
					db.deleteChest(b.getLocation());
                    lockedChestCache.remove(b.getLocation());  
			    }
			}.runTaskLater(this,5L);
		}
	}

    /**
     * handle if the chest has been blown up
     * @param e
     */
    @EventHandler
    public void onEntityExplode(EntityExplodeEvent e)
    {
        ArrayList<Block> list = new ArrayList<>(e.blockList());
        for (Block block : list) {
            if (block.getType() != Material.CHEST) continue;

            LockedChest c = lockedChestCache.get(block.getLocation());
            if (c==null) {
                continue;
            }
            
            // needs to be removed from files
            db.deleteChest(block.getLocation());
            lockedChestCache.remove(block.getLocation());  
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
        LockedChest c = lockedChestCache.get(e.getSource().getLocation());

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
                if (lockedChestCache.get(leftLoc) != null || lockedChestCache.get(rightLoc) != null) {
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