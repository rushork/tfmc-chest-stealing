package net.tfminecraft;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.io.Serializable;

import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Objects.Faction;

public class LockedChest implements Serializable {

    private String owner;
    private String ownerName;
    private String world;
    private int x, y, z;

    // constructor
    public LockedChest(String ownerStr, String ownerNameStr, Location locationLoc) {
        this.owner = ownerStr;
        this.ownerName = ownerNameStr;
        this.world = locationLoc.getWorld().getName();
        this.x = locationLoc.getBlockX();
        this.y = locationLoc.getBlockY();
        this.z = locationLoc.getBlockZ();
    }

    public Boolean canAccessChest(String userID, String userName) {

        // save time, owner check should be fast
        if (this.owner.equals(userID)) return true;

        Faction userFaction = FactionManager.getByMember(userName);
        Faction ownerFaction = FactionManager.getByMember(this.getOwnerName());

        if (userFaction == null) return false;
        if (ownerFaction == null) return false;

        if (userFaction.getId().equals(ownerFaction.getId())) return true;

        return false;
    }

    // getters/setters
     public String getOwnerName() {
        return ownerName;
    }

    public Location getLocation() {
        World w = Bukkit.getWorld(world);
        return new Location(w, x, y, z);
    }

    public String getOwner() {
        return owner;
    }

    public void setOwnerName(String name) {
        this.ownerName = name;
    }

    public void setLocation(Location location) {
        this.world = location.getWorld().getName();
        this.x = location.getBlockX();
        this.y = location.getBlockY();
        this.z = location.getBlockZ();
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getWorld() { return world; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }
}