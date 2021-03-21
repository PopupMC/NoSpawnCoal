package com.popupmc.nospawncoal;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class NoSpawnCoal extends JavaPlugin implements Listener {
    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);

        spawn = Bukkit.getWorld("imperial_city");

        if(spawn == null) {
            getLogger().warning("ERROR: imperial_city is null, disabling plugin");
            this.setEnabled(false);
            return;
        }

        // Log enabled status
        getLogger().info("NoSpawnCoal is enabled.");
    }

    // Log disabled status
    @Override
    public void onDisable() {
        getLogger().info("NoSpawnCoal is disabled");
    }

    @EventHandler
    public void onEvent (PlayerMoveEvent e) {

        // Do nothing if not at spawn
        if(e.getPlayer().getWorld() != spawn)
            return;

        // Queue permission check and evict player if not allowed to be in the city
        // PlayerMoveEvent is called extremely rapidly so this is designed to be run very quickly to mitigate lag
        // It dismisses all but 1 check per second per player and looks up duplicate checks in a hashtable which is
        // lightning fast
        runCheck(e.getPlayer().getUniqueId());
    }

    // Do a batch check for players starting 20 ticks from now
    public void runCheck() {
        runCheck(null, loopDelayTicks);
    }

    // Do a batch check for players, adding one of it's not already on the list, starting 20 ticks from now
    public void runCheck(@Nullable UUID p) {
        runCheck(p, loopDelayTicks);
    }

    // Do a batch check for players starting x ticks from now
    public void runCheck(int tickDelay) {
        runCheck(null, tickDelay);
    }

    // Do a batch check for players, adding one of it's not already on the list, starting x ticks from now
    public void runCheck(@Nullable UUID p, int tickDelay) {

        // Add player to check list if it's not already there
        // We're only doing this because it's a hashmap, if it was an ArrayList it would be out of the question

        // If this was called with a player
        if(p != null) {
            // and that player doesn't exist in the checks and the checks don't surpass the size hard limit
            if(!playersToCheck.containsKey(p) &&
                    playersToCheck.size() < maxCheckSize) {
                playersToCheck.put(p, p);
            }
        }

        // Don't do anything if the timer is ongoing
        if(checkRunning != null) {
            return;
        }

        // Do the check
        checkRunning = new BukkitRunnable() {
            @Override
            public void run() {

                // Reset loop counter
                loopCounter = loopMaxPerTick;

                // If players to check is empty stop here
                if(playersToCheck.isEmpty()) {
                    checkRunning = null;
                    return;
                }

                // Do a shallow copy of the check list, this is because they will be changed while the loop is running
                // which can cause errors
                for(Map.Entry<UUID, UUID> entry : new HashMap<>(playersToCheck).entrySet()) {
                    loopCounter--;
                    if(loopCounter <= 0) {
                        // We've reached our limit for this tick, mark task complete, queue another loop expidited on
                        // the very next tick
                        checkRunning = null;
                        runCheck(1);
                        break;
                    }

                    // Get player, we want a new player object as a lot could have changed since this loop was last run
                    // The player may not even be online anymore
                    OfflinePlayer p = Bukkit.getOfflinePlayer(entry.getValue());

                    // See if still online
                    if(!p.isOnline()) {
                        // Remove player
                        playersToCheck.remove(entry.getKey());

                        // Skip
                        continue;
                    }

                    // Convert to online player
                    Player p1 = (Player)p;

                    // Check permissions, skip if player has permission to be there or has immunity
                    if(p1.isOp() || p1.hasPermission("essentials.worlds.imperial_city") || isImmune(p1)) {
                        // Remove player
                        playersToCheck.remove(entry.getKey());

                        // Skip
                        continue;
                    }

                    // Notify player of this
                    p1.sendMessage(ChatColor.GOLD + "You can't be at spawn until you've reached iron rank!");

                    // Player is in the city without permission, remove immidiately
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "travel " + p.getName());

                    // Remove check
                    playersToCheck.remove(entry.getKey());
                }

                // Mark task complete, do another loop to check for stragglers, do this at normal loop delays
                checkRunning = null;
                runCheck();
            }

            public boolean isImmune(Player p) {

                // Stop here if player has played before
                if(p.hasPlayedBefore())
                    return false;

                // Make sure new player has immunity key
                if(!hasMetadata(p, immunityMetaKey))
                    resetImmunity(p);

                // If this is an expidited loop because the previous loop was full then don't decrement immunity counter
                if(tickDelay == 1)
                    return hasImmunity(p);
                else
                    // Decrement immunity and return if player is immune
                    return !decrementImmunity(p);
            }

        }.runTaskLater(this, tickDelay);
    }

    public void setMetadata(Player player, String key, Object value){
        player.setMetadata(key,new FixedMetadataValue(this, value));
    }

    public List<MetadataValue> getMetadata(Player player, String key) {
        return player.getMetadata(key);
    }

    public void removeMetadata(Player player, String key) {
        player.removeMetadata(key, this);
    }

    public boolean hasMetadata(Player player, String key) {
        return player.hasMetadata(key);
    }

    public void resetImmunity(Player player) {
        if(hasMetadata(player, immunityMetaKey))
            removeMetadata(player, immunityMetaKey);

        setMetadata(player, immunityMetaKey, loopsToWaitNewPlayers);
    }

    public boolean decrementImmunity(Player player) {

        // Act as though no longer immune if no such immunity exists
        if(!hasMetadata(player, immunityMetaKey)) {
            setMetadata(player, immunityMetaKey, 0);
            return true;
        }

        // Act as though no longer immune if it exists but is empty which is technically an error
        List<MetadataValue> countList = getMetadata(player, immunityMetaKey);
        if(countList.size() == 0) {
            removeMetadata(player, immunityMetaKey);
            setMetadata(player, immunityMetaKey, 0);
            return true;
        }

        // Get current count and decrement it
        int count = countList.get(0).asInt();
        count--;

        // If count is 0 or less don't decrement anymore, ensure it stays at 0
        if(count <= 0) {
            count = 0;
        }

        getLogger().info("Immunity Count: " + count);

        // Update counter
        setMetadata(player, immunityMetaKey, count);

        // Return still immune if count is above 0
        return count == 0;
    }

    public boolean hasImmunity(Player player) {
        /// Act as though no longer immune if no such immunity exists
        if(!hasMetadata(player, immunityMetaKey)) {
            setMetadata(player, immunityMetaKey, 0);
            return false;
        }

        // Act as though no longer immune if it exists but is empty which is technically an error
        List<MetadataValue> countList = getMetadata(player, immunityMetaKey);
        if(countList.size() == 0) {
            removeMetadata(player, immunityMetaKey);
            setMetadata(player, immunityMetaKey, 0);
            return false;
        }

        // Get current count
        int count = countList.get(0).asInt();

        return count > 0;
    }

    // To keep track if the lop is running or not
    public static BukkitTask checkRunning = null;

    // List of players to check, we actually don't need a hashmap as we don't care about "Player"
    // however we use hashmap because it's insanely fast meaning it's desirable and it won't hurt
    public static final HashMap<UUID, UUID> playersToCheck = new HashMap<>();

    // Reference to spawn world
    public static World spawn;

    // How many loops have we done this time
    public static int loopCounter = 0;

    // How many loops should we do in a single tick, excess will be carried over to the next loop
    public static final int loopMaxPerTick = 25;

    // How often should the queue wait to begin being processed under normal cases
    // An influx of players will create additional expidited loops that run on each tick
    public static final int loopDelayTicks = 20;

    // There's already code that exists for new players to /travel them
    // This prevents this code from interferring
    // New players will be immune to checks for this many normal, non-expidited loops
    public static final int loopsToWaitNewPlayers = 5;

    // A globally unique key to be present on players for a limited time indicating immunity to
    // Permission checks
    public static final String immunityMetaKey = NoSpawnCoal.class.getTypeName() + ".immunity";

    // Max checks overall, here we set a hard limit of players to process to essentially be the max each tick until
    // The next check to run which is most efficient

    // At current settings it comes out to be 475 people max that can be on the check list and processed at a rate of
    // 475 people per second (20 ticks), 25 people per tick
    // That means this holds very roughly ~15KB Max of memory at current settings and thats for a large server
    public static final int maxCheckSize = loopMaxPerTick * (loopDelayTicks - 1);
}
