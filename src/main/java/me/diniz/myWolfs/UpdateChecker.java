package me.diniz.myWolfs;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class UpdateChecker implements Listener {

    private final MyWolfs plugin;
    private final String projectSlug;
    private final String currentVersion;

    // Stores the latest version if an update is found (null if up to date)
    private String latestVersionFound = null;

    public UpdateChecker(MyWolfs plugin, String projectSlug) {
        this.plugin = plugin;
        this.projectSlug = projectSlug;
        this.currentVersion = plugin.getPluginMeta().getVersion();
    }

    //region UPDATE CHECK
    /**
     * Starts the asynchronous update check.
     */
    public void checkForUpdates() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(5))
                        .build();

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("https://api.modrinth.com/v2/project/" + projectSlug + "/version"))
                        .header("User-Agent", "dinization/MyWolfs/" + currentVersion + " (https://github.com/dinization/MyWolfs-dz)")
                        .timeout(Duration.ofSeconds(5))
                        .GET()
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    plugin.getLogger().warning("Failed to check for updates. HTTP Status: " + response.statusCode());
                    return;
                }

                JsonArray versions = JsonParser.parseString(response.body()).getAsJsonArray();
                if (versions.isEmpty()) return;

                JsonObject latestVersionObj = versions.get(0).getAsJsonObject();
                String latestVersion = latestVersionObj.get("version_number").getAsString();

                if (!currentVersion.equals(latestVersion)) {
                    this.latestVersionFound = latestVersion;

                    // Switch to main thread to send messages
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        for (Player player : Bukkit.getOnlinePlayers()) {
                            sendUpdateMessage(player);
                        }
                    });
                    plugin.getLogger().info("New version available on Modrinth: " + latestVersion);
                } else {
                    plugin.getLogger().info("MyWolfs is up to date (v" + currentVersion + ").");
                }

            } catch (Exception e) {
                plugin.getLogger().warning("Error checking for updates: " + e.getMessage());
            }
        });
    }
    //endregion

    //region PLAYER NOTIFICATION
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (latestVersionFound != null) {
            // Delay by 5 seconds (100 ticks) so the player can see the chat clearly
            Bukkit.getScheduler().runTaskLater(plugin, () -> sendUpdateMessage(event.getPlayer()), 100L);
        }
    }

    private void sendUpdateMessage(Player player) {
        // Only notify admins or players with specific permission
        if (player.hasPermission("mywolfs.admin") || player.isOp()) {
            String[] replacements = {
                    "%new_version%", latestVersionFound,
                    "%current_version%", currentVersion,
                    "%url%", "https://modrinth.com/plugin/" + projectSlug
            };

            // Fetches messages from config.yml
            plugin.sendConfigMessage(player, "update_available", replacements);
            plugin.sendConfigMessage(player, "update_download_link", replacements);
        }
    }
    //endregion
}