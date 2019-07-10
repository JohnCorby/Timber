package com.johncorby.timber;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.stream.Collectors;

import static java.lang.Math.min;

public class Timber extends JavaPlugin implements Listener {
    private static int BREAK_DELAY;
    private static int MAX_BREAKS_PER_CYCLE;
    private static Mode MODE;
    private static List<Material> AXE_TYPES, LOG_TYPES, LEAVES_TYPES;

    private static Queue<BreakData> breakQueue = new ArrayDeque<>();

    public void onEnable() {
        // Copy defaults
        saveDefaultConfig();

        // Get config stuff
        BREAK_DELAY = getConfig().getInt("break-delay");
        MAX_BREAKS_PER_CYCLE = getConfig().getInt("max-breaks-per-cycle");
        MODE = Mode.valueOf(getConfig().getString("mode").toUpperCase());
        AXE_TYPES = getConfig().getStringList("axe-types").stream()
                .map(Material::matchMaterial)
                .collect(Collectors.toList());
        LOG_TYPES = getConfig().getStringList("log-types").stream()
                .map(Material::matchMaterial)
                .collect(Collectors.toList());
        LEAVES_TYPES = getConfig().getStringList("leaves-types").stream()
                .map(Material::matchMaterial)
                .collect(Collectors.toList());

        // Register events
        getServer().getPluginManager().registerEvents(this, this);

        // Init break queue loop
        new BukkitRunnable() {
            public void run() {
                for (var i = 0; i < min(breakQueue.size(), MAX_BREAKS_PER_CYCLE); i++)
                    breakQueue.remove().doBreak();
            }
        }.runTaskTimer(this, 0, BREAK_DELAY);

        getLogger().info("Timber enabled");
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!event.getPlayer().isSneaking()) return;
        // Add block to queue, checking if axe and if log
        var data = new BreakData(event.getBlock().getLocation(),
                event.getPlayer().getInventory());
        if (data.isValid(Filter.LOG)) data.addNear();
    }

    private enum Mode {
        CLASSIC,
        CLASSIC_WITH_LEAVES,
        FULL,
        FULL_WITHOUT_LEAVES
    }

    private enum Filter {
        LOG,
        LEAVES,
        BOTH
    }

    private class BreakData {
        private final Location location;
        private final PlayerInventory inventory;
        private final ItemStack item;

        private BreakData(Location location,
                          PlayerInventory inventory) {
            this.location = location;
            this.inventory = inventory;
            this.item = inventory.getItemInMainHand();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            BreakData breakData = (BreakData) o;
            return Objects.equals(location, breakData.location);
        }

        @Override
        public int hashCode() {
            return Objects.hash(location);
        }

        private boolean isAxe() {
            return AXE_TYPES.contains(item.getType());
        }

        private boolean isLog() {
            return LOG_TYPES.contains(location.getBlock().getType());
        }

        private boolean isLeaves() {
            return LEAVES_TYPES.contains(location.getBlock().getType());
        }

        private boolean isValid(Filter filter) {
            if (breakQueue.contains(this)) return false;
            if (!isAxe()) return false;

            switch (filter) {
                case LOG:
                    return isLog();
                case LEAVES:
                    return isLeaves();
                case BOTH:
                    return isLog() || isLeaves();
                default:
                    return false;
            }
        }

        private void addRelative(BlockFace direction, Filter filter) {
            var newData = new BreakData(location.clone().add(
                    direction.getModX(),
                    direction.getModY(),
                    direction.getModZ()
            ), inventory);
            if (newData.isValid(filter)) breakQueue.add(newData);
        }

        private void addNear() {
            // Add near locations to queue
            switch (MODE) {
                case CLASSIC:
                    addRelative(BlockFace.UP, Filter.LOG);
                    break;
                case CLASSIC_WITH_LEAVES:
                    addRelative(BlockFace.UP, Filter.BOTH);
                    addRelative(BlockFace.DOWN, Filter.LEAVES);
                    addRelative(BlockFace.NORTH, Filter.LEAVES);
                    addRelative(BlockFace.SOUTH, Filter.LEAVES);
                    addRelative(BlockFace.EAST, Filter.LEAVES);
                    addRelative(BlockFace.WEST, Filter.LEAVES);
                    break;
                case FULL:
                    addRelative(BlockFace.UP, Filter.BOTH);
                    addRelative(BlockFace.DOWN, Filter.BOTH);
                    addRelative(BlockFace.NORTH, Filter.BOTH);
                    addRelative(BlockFace.SOUTH, Filter.BOTH);
                    addRelative(BlockFace.EAST, Filter.BOTH);
                    addRelative(BlockFace.WEST, Filter.BOTH);
                    break;
                case FULL_WITHOUT_LEAVES:
                    addRelative(BlockFace.UP, Filter.LOG);
                    addRelative(BlockFace.DOWN, Filter.LOG);
                    addRelative(BlockFace.NORTH, Filter.LOG);
                    addRelative(BlockFace.SOUTH, Filter.LOG);
                    addRelative(BlockFace.EAST, Filter.LOG);
                    addRelative(BlockFace.WEST, Filter.LOG);
                    break;
            }
        }

        private void doBreak() {
            // Add 1 damage the tool
            var meta = (Damageable) item.getItemMeta();
            // check if should be broken (durability = 0)
            if (meta.getDamage() >= item.getType().getMaxDurability()) {
                // remove it
                // this should prevent further access to meta
                inventory.remove(item);
                return;
            }
            // add 1 damage
            meta.setDamage(meta.getDamage() + 1);
            item.setItemMeta((ItemMeta) meta);

            // Break the block and drop items
            location.getBlock().breakNaturally(item);

            // add near blocks to queue
            addNear();
        }
    }
}
