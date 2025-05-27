package org.example;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.example.Commands.LuckyClamCommand;

import java.util.*;

public class Main extends JavaPlugin implements Listener {
    private final Map<Location, Long> clamCooldowns = new HashMap<>();
    private final Set<ArmorStand> activeClams = new HashSet<>();
    private final Random random = new Random();
    private final String spawnWorld = "atlantic";
    private int maxClams, minX, maxX, minZ, maxZ, maxAttempts;
    private static final String CLAM_NAME = "LuckyClam";
    private static final String CLAM_DISPLAY_NAME = ChatColor.translateAlternateColorCodes('&', "&bLuckyClam");
    private List<Reward> rewards;

    private static class Reward {
        String type;
        Material material;
        int amount;
        String command;
        double chance;
        double cumulativeChance;

        Reward(String type, Material material, int amount, String command, double chance) {
            this.type = type;
            this.material = material;
            this.amount = amount;
            this.command = command;
            this.chance = chance;
        }
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfigValues();
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("luckyclam").setExecutor(new LuckyClamCommand(this));

        // Clear stray clams on enable
        new BukkitRunnable() {
            @Override
            public void run() {
                World world = getServer().getWorld(spawnWorld);
                if (world != null) {
                    int removed = 0;
                    for (Entity entity : world.getEntitiesByClass(ArmorStand.class)) {
                        String customName = entity.getCustomName();
                        if (customName != null
                                && (customName.equals(CLAM_NAME) || customName.equals(CLAM_DISPLAY_NAME))) {
                            entity.remove();
                            removed++;
                        }
                    }
                    getLogger().info("Cleared " + removed + " stray LuckyClam entities.");
                }
            }
        }.runTask(this);

        getLogger().info("LuckyClam plugin enabled. Starting initial spawn...");
        spawnClams();
    }

    public void loadConfigValues() {
        FileConfiguration config = getConfig();
        maxClams = config.getInt("spawn.max-clams", 5);
        minX = config.getInt("spawn.min-x", -200);
        maxX = config.getInt("spawn.max-x", 200);
        minZ = config.getInt("spawn.min-z", -200);
        maxZ = config.getInt("spawn.max-z", 200);
        maxAttempts = config.getInt("spawn.max-attempts", 100);
        loadRewards(config);
        getLogger()
                .info("Config loaded: world=" + spawnWorld + ", maxClams=" + maxClams + ", rewards=" + rewards.size());
    }

    private void loadRewards(FileConfiguration config) {
        rewards = new ArrayList<>();
        List<Map<?, ?>> rewardConfigs = config.getMapList("rewards");
        double totalChance = 0;

        for (Map<?, ?> rewardConfig : rewardConfigs) {
            String type = (String) rewardConfig.get("type");
            double chance = rewardConfig.containsKey("chance") ? ((Number) rewardConfig.get("chance")).doubleValue()
                    : 0;

            if (type == null || chance <= 0) {
                getLogger().warning("Invalid reward config: " + rewardConfig);
                continue;
            }

            Reward reward;
            if (type.equalsIgnoreCase("item")) {
                String materialName = (String) rewardConfig.get("material");
                int amount = rewardConfig.containsKey("amount") ? ((Number) rewardConfig.get("amount")).intValue() : 1;
                Material material = Material.matchMaterial(materialName != null ? materialName : "");
                if (material == null) {
                    getLogger().warning("Invalid material: " + materialName);
                    continue;
                }
                reward = new Reward(type, material, amount, null, chance);
            } else if (type.equalsIgnoreCase("command")) {
                String command = (String) rewardConfig.get("command");
                if (command == null) {
                    getLogger().warning("Missing command in: " + rewardConfig);
                    continue;
                }
                reward = new Reward(type, null, 0, command, chance);
            } else {
                getLogger().warning("Unknown reward type: " + type);
                continue;
            }

            reward.cumulativeChance = totalChance;
            totalChance += chance;
            rewards.add(reward); // Fixed: Changed addItem to add
        }

        if (totalChance > 100) {
            getLogger().warning("Total reward chance exceeds 100%: " + totalChance);
        }
    }

    public void startClamEvent() {
        World world = getServer().getWorld(spawnWorld);
        if (world == null) {
            getLogger().warning("World " + spawnWorld + " not found!");
            return;
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                // Clear existing clams synchronously
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        int removed = activeClams.size();
                        activeClams.forEach(ArmorStand::remove);
                        activeClams.clear();
                        getLogger().info("Cleared " + removed + " active clams.");
                    }
                }.runTask(Main.this);

                List<Location> clamLocations = new ArrayList<>();
                // Find locations synchronously
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        int spawnedClams = 0;
                        for (int i = 0; i < maxClams; i++) {
                            Location clamLoc = findValidClamLocation(world);
                            if (clamLoc != null) {
                                clamLocations.add(clamLoc);
                                spawnedClams++;
                            }
                        }
                        getLogger().info("Found " + spawnedClams + " valid clam locations in world " + spawnWorld);

                        // Spawn clams synchronously
                        for (Location loc : clamLocations) {
                            spawnClamEntity(loc);
                        }
                        getLogger().info("Spawned " + spawnedClams + " Lucky Clams in world " + spawnWorld
                                + ". Active clams: " + activeClams.size());

                        // Broadcast message
                        getServer().broadcastMessage("§aSự kiện Sò May Mắn đã bắt đầu trong world " + spawnWorld + "!");
                    }
                }.runTask(Main.this);
            }
        }.runTaskAsynchronously(this);
    }

    private void spawnClams() {
        new BukkitRunnable() {
            @Override
            public void run() {
                startClamEvent();
            }
        }.runTaskTimer(this, 0L, 20L * 60 * 60);
    }

    private Location findValidClamLocation(World world) {
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            double x = minX + random.nextDouble() * (maxX - minX);
            double z = minZ + random.nextDouble() * (maxZ - minZ);

            // Ensure chunk is loaded
            int chunkX = ((int) x >> 4);
            int chunkZ = ((int) z >> 4);
            if (!world.isChunkLoaded(chunkX, chunkZ)) {
                world.loadChunk(chunkX, chunkZ, true);
            }

            double y = findSurfaceY(world, x, z);
            if (y == -1) {
                getLogger().info("Attempt " + (attempt + 1) + ": No surface at x=" + x + ", z=" + z);
                continue;
            }

            Location loc = new Location(world, x, y, z);
            Location belowLoc = loc.clone().subtract(0, 1, 0);
            Material belowBlock = belowLoc.getBlock().getType();
            if (!isValidBaseBlock(belowBlock)) {
                getLogger().info("Attempt " + (attempt + 1) + ": Invalid base block " + belowBlock + " at " + loc);
                continue;
            }

            if (!isSafeSpawnLocation(loc)) {
                getLogger().info("Attempt " + (attempt + 1) + ": Unsafe spawn at " + loc);
                continue;
            }

            getLogger().info("Found valid clam location at " + loc);
            return loc;
        }
        getLogger().warning("Could not find valid location for clam after " + maxAttempts + " attempts.");
        return null;
    }

    private double findSurfaceY(World world, double x, double z) {
        for (int y = 100; y >= world.getMinHeight(); y--) {
            Location loc = new Location(world, x, y, z);
            Location below = loc.clone().subtract(0, 1, 0);
            if (below.getBlock().getType().isSolid() && loc.getBlock().getType() == Material.AIR) {
                return y;
            }
        }
        return -1;
    }

    private boolean isValidBaseBlock(Material material) {
        return material == Material.SAND || material == Material.GRAVEL ||
                material == Material.STONE || material == Material.DIORITE ||
                material == Material.ANDESITE || material == Material.GRANITE ||
                material == Material.DIRT || material == Material.GRASS_BLOCK ||
                material == Material.BLUE_STAINED_GLASS;
    }

    private boolean isSafeSpawnLocation(Location loc) {
        Location[] surrounding = {
                loc.clone().add(1, 0, 0),
                loc.clone().add(-1, 0, 0),
                loc.clone().add(0, 0, 1),
                loc.clone().add(0, 0, -1),
                loc.clone().add(0, 1, 0)
        };

        for (Location check : surrounding) {
            if (check.getBlock().getType().isSolid()) {
                return false;
            }
        }
        return true;
    }

    private void spawnClamEntity(Location loc) {
        ArmorStand clam = loc.getWorld().spawn(loc, ArmorStand.class);
        clam.setInvisible(true);
        clam.setGravity(false);
        clam.setCustomName(CLAM_DISPLAY_NAME);
        clam.setCustomNameVisible(true);
        clam.getEquipment().setHelmet(new ItemStack(Material.NAUTILUS_SHELL));
        activeClams.add(clam);
        getLogger().info("Spawned clam at " + loc + ". Active clams: " + activeClams.size());
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractAtEntityEvent event) {
        if (!(event.getRightClicked() instanceof ArmorStand clam))
            return;
        String customName = clam.getCustomName();
        if (customName == null || !(customName.equals(CLAM_NAME) || customName.equals(CLAM_DISPLAY_NAME)))
            return;

        Player player = event.getPlayer();
        Location loc = clam.getLocation();

        long currentTime = System.currentTimeMillis();
        FileConfiguration config = getConfig();
        int minCooldown = config.getInt("cooldown.min", 10) * 60 * 1000;
        int maxCooldown = config.getInt("cooldown.max", 30) * 60 * 1000;
        if (clamCooldowns.containsKey(loc) && currentTime - clamCooldowns.get(loc) < minCooldown) {
            player.sendMessage("§cCon sò này đang nghỉ ngơi! Thử lại sau.");
            return;
        }

        loc.getWorld().spawnParticle(Particle.BUBBLE_POP, loc, 50, 0.5, 0.5, 0.5, 0);
        loc.getWorld().playSound(loc, Sound.ENTITY_DOLPHIN_SPLASH, 1.0f, 1.0f);
        loc.getWorld().spawnParticle(Particle.END_ROD, loc, 10, 0.3, 0.3, 0.3, 0);

        boolean rewarded = giveRandomReward(player);
        if (rewarded) {
            player.sendMessage("§aBạn đã nhận được phần thưởng từ Sò May Mắn!");
        } else {
            player.sendMessage("§cOops! Con sò này rỗng ruột!");
        }

        clamCooldowns.put(loc, currentTime);
        activeClams.remove(clam);
        clam.remove();
        new BukkitRunnable() {
            @Override
            public void run() {
                spawnClamEntity(loc);
            }
        }.runTaskLater(this,
                20L * 60 * (random.nextInt((maxCooldown - minCooldown) / (60 * 1000)) + minCooldown / (60 * 1000)));
    }

    private boolean giveRandomReward(Player player) {
        double roll = random.nextDouble() * 100;
        double current = 0;

        for (Reward reward : rewards) {
            current += reward.chance;
            if (roll < current) {
                if (reward.type.equalsIgnoreCase("item")) {
                    ItemStack item = new ItemStack(reward.material, reward.amount);
                    player.getInventory().addItem(item);
                    return true;
                } else if (reward.type.equalsIgnoreCase("command")) {
                    String command = reward.command.replace("%player%", player.getName());
                    getServer().dispatchCommand(getServer().getConsoleSender(), command);
                    return true;
                }
            }
        }
        return false; // No reward (empty)
    }

    public List<Location> getClamLocations() {
        List<Location> locations = new ArrayList<>();
        for (ArmorStand clam : activeClams) {
            locations.add(clam.getLocation());
        }
        getLogger().info("Listing " + locations.size() + " clams: " + locations);
        return locations;
    }
}