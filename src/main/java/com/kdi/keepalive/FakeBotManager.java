package com.kdi.keepalive;

import java.util.Locale;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class FakeBotManager implements Listener {
    private static final UUID DEFAULT_UUID = UUID.fromString("da4a53df-c821-4f4c-b2ab-1a8af6d66124");

    private final JavaPlugin plugin;
    private final FakePlayerNms nms = new FakePlayerNms();
    private UUID configuredUuid = DEFAULT_UUID;
    private UUID activeUuid;
    private String defaultName = "KDI_Bot";
    private String activeName = defaultName;
    private String password = "CmD83x!xC32+0lX2";
    private String authStatus = "not attempted";
    private Location lastLocation;
    private boolean enabled = true;
    private boolean autoAuth = true;
    private boolean antiAfk;
    private boolean startupComplete;
    private boolean everSpawned;
    private boolean manuallyKilled;
    private boolean suppressRespawn;
    private boolean shuttingDown;
    private boolean respawnQueued;
    private long checkIntervalTicks = 100L;
    private long antiAfkIntervalMillis = 300_000L;
    private long startedAt;
    private long lastAntiAfkAt;
    private long lastSpawnAttemptAt;
    private long spawnGeneration;
    private BukkitTask maintenanceTask;

    public FakeBotManager(JavaPlugin plugin) {
        this.plugin = plugin;
        loadSettings();
    }

    public void start() {
        restartMaintenanceTask();
    }

    public void reloadSettings() {
        plugin.reloadConfig();
        loadSettings();
        restartMaintenanceTask();
        if (startupComplete && enabled && !manuallyKilled && getActivePlayer() == null) {
            queueRespawn();
        }
    }

    private void loadSettings() {
        enabled = plugin.getConfig().getBoolean("enabled", true);
        defaultName = plugin.getConfig().getString("bot-name", "KDI_Bot");
        if (defaultName == null || !defaultName.matches("[A-Za-z0-9_]{1,16}")) {
            defaultName = "KDI_Bot";
        }
        password = plugin.getConfig().getString("bot-password", "CmD83x!xC32+0lX2");
        if (password == null) {
            password = "CmD83x!xC32+0lX2";
        }
        autoAuth = plugin.getConfig().getBoolean("auto-auth", true);
        antiAfk = plugin.getConfig().getBoolean("anti-afk", false);
        int checkSeconds = Math.max(1, Math.min(300, plugin.getConfig().getInt("check-interval-seconds", 5)));
        int antiAfkSeconds = Math.max(1, Math.min(86_400, plugin.getConfig().getInt("anti-afk-interval-seconds", 300)));
        checkIntervalTicks = checkSeconds * 20L;
        antiAfkIntervalMillis = antiAfkSeconds * 1_000L;

        String configured = plugin.getConfig().getString("bot-uuid");
        try {
            UUID parsed = UUID.fromString(configured == null ? "" : configured);
            if (!parsed.toString().equalsIgnoreCase(configured)) {
                throw new IllegalArgumentException("UUID must use the standard 36-character format");
            }
            configuredUuid = parsed;
        } catch (IllegalArgumentException exception) {
            configuredUuid = DEFAULT_UUID;
            plugin.getLogger().warning("Invalid bot-uuid; using safe default " + DEFAULT_UUID + ".");
            plugin.getConfig().set("bot-uuid", DEFAULT_UUID.toString());
            plugin.saveConfig();
        }
    }

    private void restartMaintenanceTask() {
        if (maintenanceTask != null) {
            maintenanceTask.cancel();
        }
        maintenanceTask = Bukkit.getScheduler().runTaskTimer(plugin, this::maintain, checkIntervalTicks, checkIntervalTicks);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onServerLoad(ServerLoadEvent event) {
        startupComplete = true;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (enabled && !manuallyKilled && !everSpawned && !shuttingDown) {
                spawnAutomatic();
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player joining = event.getPlayer();
        if (isBot(joining)) {
            event.setJoinMessage(null);
            hideBotFromOnlinePlayers(joining);
            return;
        }
        Player bot = getActivePlayer();
        if (bot != null && bot.isOnline()) {
            joining.hidePlayer(plugin, bot);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (!isBot(player)) {
            return;
        }
        event.setQuitMessage(null);
        lastLocation = player.getLocation().clone();
        if (!suppressRespawn && !shuttingDown && !manuallyKilled) {
            queueRespawn();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerKick(PlayerKickEvent event) {
        if (isBot(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && isBot(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPotionEffect(EntityPotionEffectEvent event) {
        if (event.getEntity() instanceof Player player && isBot(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && isBot(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        if (isBot(event.getPlayer()) && event.getNewGameMode() != GameMode.SPECTATOR) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBotTeleport(PlayerTeleportEvent event) {
        if (isBot(event.getPlayer()) && event.getTo() != null) {
            lastLocation = event.getTo().clone();
        }
    }

    private void maintain() {
        if (shuttingDown || !startupComplete || !enabled || manuallyKilled) {
            return;
        }
        Player bot = getActivePlayer();
        if (bot != null && bot.isOnline() && bot.isValid()) {
            lastLocation = bot.getLocation().clone();
            nms.protect();
            if (bot.getGameMode() != GameMode.SPECTATOR) {
                bot.setGameMode(GameMode.SPECTATOR);
            }
            if (antiAfk && System.currentTimeMillis() - lastAntiAfkAt >= antiAfkIntervalMillis) {
                bot.swingMainHand();
                lastAntiAfkAt = System.currentTimeMillis();
            }
            return;
        }
        if (activeUuid != null) {
            queueRespawn();
            return;
        }
        if ((!everSpawned || System.currentTimeMillis() - lastSpawnAttemptAt >= checkIntervalTicks * 50L)
                && startupComplete) {
            spawnAutomatic();
        }
    }

    private void queueRespawn() {
        if (respawnQueued || shuttingDown || manuallyKilled || !enabled) {
            return;
        }
        respawnQueued = true;
        Bukkit.getScheduler().runTask(plugin, () -> {
            respawnQueued = false;
            if (shuttingDown || manuallyKilled || !enabled) {
                return;
            }
            Player bot = getActivePlayer();
            if (bot != null && bot.isOnline() && bot.isValid()) {
                return;
            }
            if (nms.hasHandle()) {
                removeSilently();
            }
            activeUuid = null;
            Location location = lastLocation == null ? defaultLocation() : lastLocation.clone();
            if (location != null) {
                spawn(location, activeName == null ? defaultName : activeName);
            }
        });
    }

    private void spawnAutomatic() {
        Location location = lastLocation == null ? defaultLocation() : lastLocation.clone();
        if (location == null) {
            plugin.getLogger().warning("Cannot spawn the fake player because no world is loaded.");
            return;
        }
        spawn(location, defaultName);
    }

    private Location defaultLocation() {
        if (Bukkit.getWorlds().isEmpty()) {
            return null;
        }
        World world = Bukkit.getWorlds().get(0);
        return world.getSpawnLocation();
    }

    public boolean spawn(Location location, String name) {
        if (!enabled || location == null || location.getWorld() == null) {
            return false;
        }
        Player current = getActivePlayer();
        if (current != null && current.isOnline()) {
            manuallyKilled = false;
            boolean moved = nms.move(activeUuid, location);
            if (moved) {
                lastLocation = location.clone();
            }
            return moved;
        }
        if (name == null || !name.matches("[A-Za-z0-9_]{1,16}")) {
            return false;
        }
        Player collision = Bukkit.getPlayer(configuredUuid);
        if (collision != null) {
            return false;
        }
        if (nms.hasHandle()) {
            removeSilently();
        }

        UUID previousUuid = activeUuid;
        String previousName = activeName;
        activeUuid = configuredUuid;
        activeName = name;
        lastLocation = location.clone();
        manuallyKilled = false;
        lastSpawnAttemptAt = System.currentTimeMillis();
        long generation = ++spawnGeneration;
        authStatus = autoAuth ? "pending" : "disabled";
        try {
            nms.spawn(name, activeUuid, lastLocation);
            Player bot = getActivePlayer();
            if (bot == null) {
                throw new IllegalStateException("PlayerList did not expose the fake player after join.");
            }
            bot.setGameMode(GameMode.SPECTATOR);
            hideBotFromOnlinePlayers(bot);
            startedAt = System.currentTimeMillis();
            lastAntiAfkAt = startedAt;
            everSpawned = true;
            if (autoAuth) {
                scheduleAuth(activeUuid, generation);
            }
            return true;
        } catch (RuntimeException | LinkageError exception) {
            suppressRespawn = true;
            try {
                nms.remove();
            } catch (RuntimeException | LinkageError ignored) {
            } finally {
                suppressRespawn = false;
            }
            activeUuid = null;
            activeName = defaultName;
            if (!everSpawned) {
                activeUuid = previousUuid;
                activeName = previousName;
            }
            plugin.getLogger().severe("Failed to create the Paper 1.21.11 fake player: "
                    + exception.getClass().getSimpleName() + ": " + exception.getMessage());
            return false;
        }
    }

    public boolean move(Player sender) {
        Player bot = getActivePlayer();
        if (bot == null || !bot.isOnline()) {
            return false;
        }
        Location location = sender.getLocation().clone();
        boolean moved = nms.move(activeUuid, location);
        if (moved) {
            lastLocation = location;
            manuallyKilled = false;
        }
        return moved;
    }

    public boolean kill() {
        if (!nms.hasHandle() && getActivePlayer() == null) {
            return false;
        }
        manuallyKilled = true;
        spawnGeneration++;
        removeSilently();
        activeUuid = null;
        activeName = defaultName;
        authStatus = autoAuth ? "not attempted" : "disabled";
        return true;
    }

    private void removeSilently() {
        boolean oldValue = suppressRespawn;
        suppressRespawn = true;
        try {
            nms.remove();
        } catch (RuntimeException | LinkageError exception) {
            plugin.getLogger().warning("Fake-player cleanup reported " + exception.getClass().getSimpleName() + ".");
        } finally {
            suppressRespawn = oldValue;
        }
    }

    private void hideBotFromOnlinePlayers(Player bot) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.getUniqueId().equals(bot.getUniqueId())) {
                viewer.hidePlayer(plugin, bot);
            }
        }
    }

    private void scheduleAuth(UUID uuid, long generation) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> runAuthAttempt(uuid, generation, 1), 40L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> runAuthAttempt(uuid, generation, 2), 120L);
    }

    private void runAuthAttempt(UUID uuid, long generation, int attempt) {
        if (!autoAuth || shuttingDown || generation != spawnGeneration || !uuid.equals(activeUuid)) {
            return;
        }
        Player bot = Bukkit.getPlayer(uuid);
        if (bot == null || !bot.isOnline()) {
            return;
        }
        authStatus = "attempt " + attempt + " sent; result unverified";
        try {
            bot.performCommand("register " + password + " " + password);
        } catch (RuntimeException exception) {
            authStatus = "attempt " + attempt + " failed; result unverified";
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (generation != spawnGeneration || !uuid.equals(activeUuid) || shuttingDown) {
                return;
            }
            Player current = Bukkit.getPlayer(uuid);
            if (current == null || !current.isOnline()) {
                return;
            }
            try {
                current.performCommand("login " + password);
            } catch (RuntimeException exception) {
                authStatus = "attempt " + attempt + " failed; result unverified";
            }
        }, 20L);
    }

    public void sendStatus(CommandSender sender) {
        Player bot = getActivePlayer();
        boolean online = bot != null && bot.isOnline();
        UUID shownUuid = online ? activeUuid : configuredUuid;
        String shownName = online ? bot.getName() : (activeName == null ? defaultName : activeName);
        sender.sendMessage(ChatColor.GOLD + "Fake player: " + (online ? ChatColor.GREEN + "online" : ChatColor.RED + "offline"));
        sender.sendMessage(ChatColor.GOLD + "Name: " + ChatColor.WHITE + shownName);
        sender.sendMessage(ChatColor.GOLD + "UUID: " + ChatColor.WHITE + shownUuid);
        if (online) {
            Location location = bot.getLocation();
            sender.sendMessage(ChatColor.GOLD + "Location: " + ChatColor.WHITE + location.getWorld().getName()
                    + " " + String.format(Locale.ROOT, "%.1f %.1f %.1f", location.getX(), location.getY(), location.getZ()));
            long elapsed = Math.max(0L, System.currentTimeMillis() - startedAt) / 1_000L;
            sender.sendMessage(ChatColor.GOLD + "Uptime: " + ChatColor.WHITE
                    + String.format(Locale.ROOT, "%02d:%02d:%02d", elapsed / 3_600L, (elapsed / 60L) % 60L, elapsed % 60L));
        } else {
            sender.sendMessage(ChatColor.GOLD + "Location: " + ChatColor.WHITE + "unavailable");
            sender.sendMessage(ChatColor.GOLD + "Uptime: " + ChatColor.WHITE + "0s");
        }
        sender.sendMessage(ChatColor.GOLD + "Connection: " + ChatColor.WHITE
                + (online ? "synthetic local connection (active)" : "offline"));
        sender.sendMessage(ChatColor.GOLD + "Authentication: " + ChatColor.WHITE + (autoAuth ? authStatus : "disabled"));
    }

    public void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "/fk spawn add [name] " + ChatColor.GRAY + "- spawn or move the fake player");
        sender.sendMessage(ChatColor.GOLD + "/fk kill " + ChatColor.GRAY + "- remove the fake player");
        sender.sendMessage(ChatColor.GOLD + "/fk status " + ChatColor.GRAY + "- show live status");
        sender.sendMessage(ChatColor.GOLD + "/fk reload " + ChatColor.GRAY + "- reload configuration");
        sender.sendMessage(ChatColor.GOLD + "/fk help " + ChatColor.GRAY + "- show this help");
    }

    public String getDefaultName() {
        return defaultName;
    }

    public boolean isBot(Player player) {
        return activeUuid != null && activeUuid.equals(player.getUniqueId());
    }

    private Player getActivePlayer() {
        return activeUuid == null ? null : Bukkit.getPlayer(activeUuid);
    }

    private void shutdown() {
        shuttingDown = true;
        spawnGeneration++;
        if (maintenanceTask != null) {
            maintenanceTask.cancel();
        }
        removeSilently();
        activeUuid = null;
    }

    public void shutdownPlugin() {
        shutdown();
    }
}