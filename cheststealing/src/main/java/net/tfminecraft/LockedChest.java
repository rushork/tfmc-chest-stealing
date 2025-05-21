package net.tfminecraft;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.io.Serializable;

public class LockedChest implements Serializable {

    private String owner;
    private String faction;
    private String world;
    private int x, y, z;

    // constructor
    public LockedChest(String ownerStr, String factionStr, Location locationLoc) {
        this.owner = ownerStr;
        this.faction = factionStr;
        this.world = locationLoc.getWorld().getName();
        this.x = locationLoc.getBlockX();
        this.y = locationLoc.getBlockY();
        this.z = locationLoc.getBlockZ();
    }

    public Boolean canAccessChest(String userID) {
        return this.owner.equals(userID);
    }

    // getters/setters
     public String getFaction() {
        return faction;
    }

    public Location getLocation() {
        World w = Bukkit.getWorld(world);
        return new Location(w, x, y, z);
    }

    public String getOwner() {
        return owner;
    }

    public void setFaction(String faction) {
        this.faction = faction;
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