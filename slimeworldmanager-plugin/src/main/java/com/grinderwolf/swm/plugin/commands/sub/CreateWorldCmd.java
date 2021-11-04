package com.grinderwolf.swm.plugin.commands.sub;


import com.grinderwolf.swm.api.exceptions.WorldAlreadyExistsException;
import com.grinderwolf.swm.api.loaders.SlimeLoader;
import com.grinderwolf.swm.api.world.SlimeWorld;
import com.grinderwolf.swm.api.world.properties.SlimePropertyMap;
import com.grinderwolf.swm.plugin.SWMPlugin;
import com.grinderwolf.swm.plugin.commands.CommandManager;
import com.grinderwolf.swm.plugin.config.ConfigManager;
import com.grinderwolf.swm.plugin.config.WorldData;
import com.grinderwolf.swm.plugin.config.WorldsConfig;
import com.grinderwolf.swm.plugin.log.Logging;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Getter
public class CreateWorldCmd implements Subcommand {

    private final String usage = "create <world> <data-source> [OPTS]";
    private final String description = "Create an empty world.";
    private final String permission = "swm.createworld";

    private static final Pattern OPT_PATTERN = Pattern.compile("-\\w+:\\w+");

    private static final List<String> OPT_KEYS = Arrays.stream(WorldData.class.getDeclaredFields()).map(Field::getName)
            .filter(Predicate.not("dataSource"::equals))
            .collect(Collectors.toUnmodifiableList());

    @Override
    public boolean onCommand(CommandSender sender, String[] args) {
        if (args.length > 1) {
            String worldName = args[0];

            if (CommandManager.getInstance().getWorldsInUse().contains(worldName)) {
                sender.sendMessage(Logging.COMMAND_PREFIX + ChatColor.RED + "World " + worldName + " is already being used on another command! Wait some time and try again.");

                return true;
            }

            World world = Bukkit.getWorld(worldName);

            if (world != null) {
                sender.sendMessage(Logging.COMMAND_PREFIX + ChatColor.RED + "World " + worldName + " already exists!");

                return true;
            }

            WorldsConfig config = ConfigManager.getWorldConfig();

            if (config.getWorlds().containsKey(worldName)) {
                sender.sendMessage(Logging.COMMAND_PREFIX + ChatColor.RED + "There is already a world called  " + worldName + " inside the worlds config file.");

                return true;
            }

            String dataSource = args[1];
            SlimeLoader loader = SWMPlugin.getInstance().getLoader(dataSource);

            if (loader == null) {
                sender.sendMessage(Logging.COMMAND_PREFIX + ChatColor.RED + "Unknown data source  " + dataSource + ".");

                return true;
            }

            WorldData worldData;

            // Parse any options provided to the create command
            if (args.length > 2) {
                if (!Arrays.stream(args).skip(2).allMatch(o -> OPT_PATTERN.matcher(o).find())) {
                    sender.sendMessage(Logging.COMMAND_PREFIX + ChatColor.RED + "Invalid format for World Option!");
                    return true;
                }
                Map<String, String> optionsMap = Arrays.stream(args).skip(2).map(o -> o.substring(1))
                        .map(o -> o.split(":"))
                        .collect(Collectors.toMap(o -> o[0], o -> o[1]));

                if (!(OPT_KEYS.containsAll(optionsMap.keySet()))) {
                    sender.sendMessage(Logging.COMMAND_PREFIX + ChatColor.RED + "Invalid key for World Configuration!");
                    return true;
                }

                optionsMap.putIfAbsent("spawn", "0, 64, 0"); // Make sure the world spawn is 0, 64, 0 unless an alternative is specified!
                worldData = WorldData.of(optionsMap);

                sender.sendMessage(optionsMap.entrySet().stream().map(e -> e.getKey() + ":" + e.getValue()).collect(Collectors.joining(", ", "{", "}")));
            } else {
                worldData = new WorldData();
                worldData.setSpawn("0, 64, 0");
            }

            CommandManager.getInstance().getWorldsInUse().add(worldName);
            sender.sendMessage(Logging.COMMAND_PREFIX + ChatColor.GRAY + "Creating empty world " + ChatColor.YELLOW + worldName + ChatColor.GRAY + "...");

            // It's best to load the world async, and then just go back to the server thread and add it to the world list
            Bukkit.getScheduler().runTaskAsynchronously(SWMPlugin.getInstance(), () -> {

                try {
                    long start = System.currentTimeMillis();

                    worldData.setDataSource(dataSource);

                    SlimePropertyMap propertyMap = worldData.toPropertyMap();
                    SlimeWorld slimeWorld = SWMPlugin.getInstance().createEmptyWorld(loader, worldName, false, propertyMap);

                    Bukkit.getScheduler().runTask(SWMPlugin.getInstance(), () -> {
                        try {
                            SWMPlugin.getInstance().generateWorld(slimeWorld);

                            // Bedrock block
                            Location location = new Location(Bukkit.getWorld(worldName), 0, 61, 0);
                            location.getBlock().setType(Material.BEDROCK);

                            // Config
                            config.getWorlds().put(worldName, worldData);
                            config.save();

                            sender.sendMessage(Logging.COMMAND_PREFIX + ChatColor.GREEN + "World " + ChatColor.YELLOW + worldName
                                    + ChatColor.GREEN + " created in " + (System.currentTimeMillis() - start) + "ms!");
                        } catch (IllegalArgumentException ex) {
                            sender.sendMessage(Logging.COMMAND_PREFIX + ChatColor.RED + "Failed to create world " + worldName + ": " + ex.getMessage() + ".");
                        }
                    });
                } catch (WorldAlreadyExistsException ex) {
                    sender.sendMessage(Logging.COMMAND_PREFIX + ChatColor.RED + "Failed to create world " + worldName +
                            ": world already exists (using data source '" + dataSource + "').");
                } catch (IOException ex) {
                    if (!(sender instanceof ConsoleCommandSender)) {
                        sender.sendMessage(Logging.COMMAND_PREFIX + ChatColor.RED + "Failed to create world " + worldName
                                + ". Take a look at the server console for more information.");
                    }

                    Logging.error("Failed to load world " + worldName + ":");
                    ex.printStackTrace();
                } finally {
                    CommandManager.getInstance().getWorldsInUse().remove(worldName);
                }
            });

            return true;
        }

        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length > 3) {
            Set<String> matches = new HashSet<>();
            String search = args[args.length - 1];
            if ("".equals(search)) { return OPT_KEYS.stream().map(s -> s = "-" + s).collect(Collectors.toList()); }
            OPT_KEYS.stream().filter(s -> s.toLowerCase().startsWith(search.toLowerCase().substring(1)))
                    .map(s -> s = "-" + s)
                    .forEach(matches::add);
            return new ArrayList<>(matches);
        }
        return Collections.emptyList();
    }
}
