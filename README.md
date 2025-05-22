# tfmc-chest-stealing
A Maven/Java plugin for the TFMC server that enables locking &amp; stealing from Minecraft chests

# Bio

- A spigot plugin that handles locking chests manually that persists over server restarts.
- Dependant on TFMC addons to save chests to facitons. People in the same faction will be able to unlock the chests.
- Includes a system for stealing from chests which is tied to the 'Roguery' attribute in MMOCore.
- The stealing minigame is a roulette system, where it basically runs a casino 'roll' on every slot to see if you can access it (and how many of the items you can access).
- The MMOCore Roguery attribute makes the stealing go faster/quieter. 

# Usage

- You must build your own pom.xml file using the dependencies in plugin.yml
