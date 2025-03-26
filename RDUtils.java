package r4d.d7.rdutils;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public final class RDUtils extends JavaPlugin {

    private final PluginManager pluginManager = Bukkit.getPluginManager();
    private FileConfiguration messages;
    private FileConfiguration settings;

    @Override
    public void onEnable() {
        saveDefaultConfigs();
        sendHeader(getMessage("prefix") + "&aПлагин успешно запущен!");
    }

    private void saveDefaultConfigs() {
        saveResource("messages.yml", false);
        saveResource("settings.yml", false);
        messages = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "messages.yml"));
        settings = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "settings.yml"));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!cmd.getName().equalsIgnoreCase("rdutils")) return false;
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "load":
                handleLoad(sender, args);
                break;
            case "unload":
                handleUnload(sender, args);
                break;
            case "reload":
                handleReload(sender, args);
                break;
            case "list":
                handleList(sender);
                break;
            default:
                sendHelp(sender);
                break;
        }
        return true;
    }

    private void handleLoad(CommandSender sender, String[] args) {
        if (!checkPermission(sender, settings.getString("permissions.load"))) return;
        if (args.length < 2) {
            sendMessage(sender, getMessage("commands.usage.load"));
            return;
        }
        loadPlugin(sender, args[1]);
    }

    private void handleUnload(CommandSender sender, String[] args) {
        if (!checkPermission(sender, settings.getString("permissions.unload"))) return;
        if (args.length < 2) {
            sendMessage(sender, getMessage("commands.usage.unload"));
            return;
        }
        unloadPlugin(sender, args[1]);
    }

    private void handleReload(CommandSender sender, String[] args) {
        if (!checkPermission(sender, settings.getString("permissions.reload"))) return;
        if (args.length < 2) {
            sendMessage(sender, getMessage("commands.usage.reload"));
            return;
        }
        reloadPlugin(sender, args[1]);
    }

    private void handleList(CommandSender sender) {
        if (!checkPermission(sender, settings.getString("permissions.list"))) return;
        listPlugins(sender);
    }

    private void sendHelp(CommandSender sender) {
        sendMessage(sender, "&6&lУправление плагинами");
        sendMessage(sender, "&e/rdutils load <файл.jar> &7- Загрузить плагин");
        sendMessage(sender, "&e/rdutils unload <плагин> &7- Выгрузить плагин");
        sendMessage(sender, "&e/rdutils reload <плагин> &7- Перезагрузить плагин");
        sendMessage(sender, "&e/rdutils list &7- Список плагинов");
    }

    private boolean checkPermission(CommandSender sender, String permission) {
        if (!sender.hasPermission(permission)) {
            sendMessage(sender, getMessage("commands.no-permission"));
            return false;
        }
        return true;
    }

    private void loadPlugin(CommandSender sender, String pluginName) {
        File pluginFile = new File("plugins", pluginName.endsWith(".jar") ? pluginName : pluginName + ".jar");
        if (!pluginFile.exists()) {
            sendMessage(sender, getMessage("commands.error.file-not-found").replace("%file%", pluginFile.getName()));
            return;
        }

        Runnable task = () -> {
            try {
                Plugin plugin = pluginManager.loadPlugin(pluginFile);
                if (plugin == null) {
                    sendMessage(sender, getMessage("commands.error.load-failed"));
                    return;
                }
                pluginManager.enablePlugin(plugin);
                sendMessage(sender, getMessage("commands.success.load").replace("%plugin%", plugin.getName()));
            } catch (Exception e) {
                sendMessage(sender, "&cОшибка загрузки: " + e.getMessage());
                getLogger().severe("Ошибка загрузки плагина " + pluginFile.getName());
                e.printStackTrace();
            }
        };

        if (settings.getBoolean("async-loading")) {
            new BukkitRunnable() { @Override public void run() { task.run(); } }.runTaskAsynchronously(this);
        } else {
            task.run();
        }
    }

    private void unloadPlugin(CommandSender sender, String pluginName) {
        Plugin plugin = pluginManager.getPlugin(pluginName);
        if (plugin == null) {
            sendMessage(sender, getMessage("commands.error.not-found"));
            return;
        }

        Runnable task = () -> {
            try {
                pluginManager.disablePlugin(plugin);
                try {
                    java.lang.reflect.Field pluginsField = pluginManager.getClass().getDeclaredField("plugins");
                    pluginsField.setAccessible(true);
                    List<Plugin> plugins = (List<Plugin>) pluginsField.get(pluginManager);
                    plugins.remove(plugin);

                    java.lang.reflect.Field lookupNamesField = pluginManager.getClass().getDeclaredField("lookupNames");
                    lookupNamesField.setAccessible(true);
                    java.util.Map<String, Plugin> lookupNames = (java.util.Map<String, Plugin>) lookupNamesField.get(pluginManager);
                    lookupNames.remove(plugin.getName().toLowerCase());
                } catch (Exception e) {
                    getLogger().warning(getMessage("commands.error.reflection-error"));
                }
                sendMessage(sender, getMessage("commands.success.unload").replace("%plugin%", plugin.getName()));
            } catch (Exception e) {
                sendMessage(sender, "&cОшибка выгрузки: " + e.getMessage());
            }
        };

        if (settings.getBoolean("async-loading")) {
            new BukkitRunnable() { @Override public void run() { task.run(); } }.runTaskAsynchronously(this);
        } else {
            task.run();
        }
    }

    private void reloadPlugin(CommandSender sender, String pluginName) {
        Plugin plugin = pluginManager.getPlugin(pluginName);
        if (plugin == null) {
            sendMessage(sender, getMessage("commands.error.not-found"));
            return;
        }

        unloadPlugin(sender, pluginName);
        new BukkitRunnable() {
            @Override
            public void run() {
                loadPlugin(sender, pluginName + ".jar");
            }
        }.runTaskLater(this, settings.getInt("reload-delay"));
    }

    private void listPlugins(CommandSender sender) {
        List<Plugin> plugins = Arrays.stream(pluginManager.getPlugins())
                .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                .collect(Collectors.toList());

        String header = getMessage("list.header").replace("%count%", String.valueOf(plugins.size()));
        sendMessage(sender, header);

        plugins.forEach(plugin -> {
            String status = plugin.isEnabled() ? "&aВключен" : "&cВыключен";
            String entry = getMessage("list.entry")
                    .replace("%plugin%", plugin.getName())
                    .replace("%status%", status);
            sendMessage(sender, entry);
        });
    }

    // ======== Утилиты ========
    private String getMessage(String path) {
        return colorize(messages.getString(path, "&cСообщение не найдено: " + path));
    }

    private void sendHeader(String message) {
        Bukkit.getConsoleSender().sendMessage(colorize(message));
    }

    private void sendMessage(CommandSender sender, String message) {
        sender.sendMessage(colorize(message));
    }

    private String colorize(String text) {
        return text.replace('&', '§');
    }
}