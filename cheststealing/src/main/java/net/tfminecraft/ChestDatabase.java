package net.tfminecraft;

import java.io.FileInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;


public class ChestDatabase {

    /**
     * Saves a chest to the DB.
     * Uses serializable magic.
     */
    public void saveChest(LockedChest chest) {

        File dir = new File("plugins/ChestStealing/Data");
        if (!dir.exists()) {
            dir.mkdirs();
        }       

        try {
    			UUID uuid = UUID.randomUUID();
    			String uuidAsString = uuid.toString();

                String filePath = "plugins/ChestStealing/Data/" + uuidAsString + ".ser";
                FileOutputStream fos = new FileOutputStream(filePath);
                ObjectOutputStream oos = new ObjectOutputStream(fos);
                oos.writeObject(chest);
                oos.close();
                fos.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }

    }

    public boolean deleteChest(Location location) {
        File folder = new File("plugins/ChestStealing/Data");

        if (!folder.exists() || !folder.isDirectory()) {
            return false;
        }

        for (final File file : folder.listFiles()) {
            if (!file.isFile() || !file.getName().endsWith(".ser")) continue;

            try (FileInputStream fis = new FileInputStream(file);
                ObjectInputStream ois = new ObjectInputStream(fis)) {

                LockedChest chest = (LockedChest) ois.readObject();
                if (chest.getLocation() != null && chest.getLocation().equals(location)) {
                    return file.delete(); // returns true if deletion succeeded
                }

            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        }

        return false; // No matching chest found
    }
    

    public LockedChest getChest(Location location) {
        File folder = new File("plugins/ChestStealing/Data");

        if (!folder.exists() || !folder.isDirectory()) {
            return null;
        }

        for (final File file : folder.listFiles()) {
            if (!file.isFile() || !file.getName().endsWith(".ser")) continue;

            try (
                FileInputStream fis = new FileInputStream(file);
                ObjectInputStream ois = new ObjectInputStream(fis)) {

                LockedChest chest = (LockedChest) ois.readObject();
                if (chest.getLocation() != null && chest.getLocation().equals(location)) {
                    return chest;
                }

            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        }

        return null;
    }

    public Integer getChestCountUser(UUID uuid) {
        Integer count = 0;

        File folder = new File("plugins/ChestStealing/Data");

        if (!folder.exists() || !folder.isDirectory()) {
            return 0;
        }

        for (final File file : folder.listFiles()) {
            if (!file.isFile() || !file.getName().endsWith(".ser")) continue;

            try (
                FileInputStream fis = new FileInputStream(file);
                ObjectInputStream ois = new ObjectInputStream(fis)) {

                LockedChest chest = (LockedChest) ois.readObject();
                if (chest.getOwner() != null && chest.getOwner().equals(uuid.toString())) {
                    count++;
                }

            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        }

        return count;
    }

    public List<LockedChest> getAllChestsForUser(UUID uuid) {
        List<LockedChest> list = new ArrayList<LockedChest>();

        File folder = new File("plugins/ChestStealing/Data");

        if (!folder.exists() || !folder.isDirectory()) {
            return list;
        }

        for (final File file : folder.listFiles()) {
            if (!file.isFile() || !file.getName().endsWith(".ser")) continue;

            try (
                FileInputStream fis = new FileInputStream(file);
                ObjectInputStream ois = new ObjectInputStream(fis)) {

                LockedChest chest = (LockedChest) ois.readObject();
                if (chest.getOwner() != null && chest.getOwner().equals(uuid.toString())) {
                    list.add(chest);
                }

            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        }

        return list;
    }

}
