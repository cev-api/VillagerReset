package dev.cevapi.villagerreset.paper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class VillagerResetCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBCOMMANDS = List.of("status", "list", "set", "enable", "disable", "reload", "debug");
    private static final List<String> SETTING_KEYS = List.of(
            "enabled",
            "debug",
            "cycle-cost-emeralds",
            "profession-swap-cost-emeralds",
            "unemployed-initial-profession.enabled",
            "unemployed-initial-profession.cost-emeralds",
            "cycle-max-uses",
            "profession-swap-success-chance",
            "cure-discount.enabled",
            "cure-discount.radius-blocks",
            "cure-discount.bonus-level"
    );

    private final VillagerResetPaperPlugin plugin;

    public VillagerResetCommand(VillagerResetPaperPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("villagerreset.admin")) {
            sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "status", "list" -> {
                sendSettings(sender);
                return true;
            }
            case "enable" -> {
                setAndReload("enabled", true);
                sender.sendMessage(Component.text("VillagerReset is now ENABLED.", NamedTextColor.GREEN));
                return true;
            }
            case "debug" -> {
                if (args.length == 1 || "status".equalsIgnoreCase(args[1])) {
                    sender.sendMessage(Component.text("Debug is ", NamedTextColor.GRAY)
                            .append(Component.text(plugin.resetConfig().debug ? "ON" : "OFF", plugin.resetConfig().debug ? NamedTextColor.GREEN : NamedTextColor.RED)));
                    return true;
                }
                if ("on".equalsIgnoreCase(args[1]) || "off".equalsIgnoreCase(args[1])) {
                    boolean on = "on".equalsIgnoreCase(args[1]);
                    setAndReload("debug", on);
                    sender.sendMessage(Component.text("VillagerReset debug is now ", NamedTextColor.GRAY)
                            .append(Component.text(on ? "ON" : "OFF", on ? NamedTextColor.GREEN : NamedTextColor.RED)));
                    return true;
                }
                sender.sendMessage(Component.text("Usage: /" + label + " debug <on|off|status>", NamedTextColor.YELLOW));
                return true;
            }
            case "disable" -> {
                setAndReload("enabled", false);
                sender.sendMessage(Component.text("VillagerReset is now DISABLED.", NamedTextColor.RED));
                return true;
            }
            case "reload" -> {
                plugin.reloadLocalConfig();
                sender.sendMessage(Component.text("VillagerReset config reloaded.", NamedTextColor.GREEN));
                sendSettings(sender);
                return true;
            }
            case "set" -> {
                if (args.length < 3) {
                    sender.sendMessage(Component.text("Usage: /" + label + " set <setting> <value>", NamedTextColor.YELLOW));
                    return true;
                }
                String key = args[1];
                String value = args[2];
                if (!SETTING_KEYS.contains(key)) {
                    sender.sendMessage(Component.text("Unknown setting: " + key, NamedTextColor.RED));
                    return true;
                }

                if (!validateValue(key, value)) {
                    sender.sendMessage(Component.text("Invalid value for " + key + ": " + value, NamedTextColor.RED));
                    return true;
                }

                writeTypedValue(key, value);
                plugin.reloadLocalConfig();
                sender.sendMessage(Component.text("Updated ", NamedTextColor.GRAY)
                        .append(Component.text(key, NamedTextColor.AQUA))
                        .append(Component.text(" = ", NamedTextColor.GRAY))
                        .append(Component.text(readCurrentValue(key), NamedTextColor.GREEN)));
                return true;
            }
            default -> {
                sendHelp(sender, label);
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("villagerreset.admin")) {
            return List.of();
        }

        if (args.length == 1) {
            return prefixMatches(SUBCOMMANDS, args[0]);
        }
        if (args.length == 2 && "set".equalsIgnoreCase(args[0])) {
            return prefixMatches(SETTING_KEYS, args[1]);
        }
        if (args.length == 2 && "debug".equalsIgnoreCase(args[0])) {
            return prefixMatches(List.of("on", "off", "status"), args[1]);
        }
        if (args.length == 3 && "set".equalsIgnoreCase(args[0])) {
            String key = args[1];
            if ("enabled".equals(key) || "debug".equals(key) || "cure-discount.enabled".equals(key)) {
                return prefixMatches(List.of("true", "false"), args[2]);
            }
            return List.of();
        }
        return List.of();
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(Component.text("VillagerReset Commands", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/" + label + " status", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/" + label + " list", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/" + label + " set <setting> <value>", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/" + label + " enable", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/" + label + " disable", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/" + label + " debug <on|off|status>", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/" + label + " reload", NamedTextColor.YELLOW));
    }

    private void sendSettings(CommandSender sender) {
        PaperResetConfig cfg = plugin.resetConfig();
        sender.sendMessage(Component.text("VillagerReset Settings", NamedTextColor.GOLD));
        sender.sendMessage(settingLine("enabled", String.valueOf(cfg.enabled)));
        sender.sendMessage(settingLine("debug", String.valueOf(cfg.debug)));
        sender.sendMessage(settingLine("cycle-cost-emeralds", String.valueOf(cfg.cycleCostEmeralds)));
        sender.sendMessage(settingLine("profession-swap-cost-emeralds", String.valueOf(cfg.professionSwapCostEmeralds)));
        sender.sendMessage(settingLine("unemployed-initial-profession.enabled", String.valueOf(cfg.unemployedInitialProfessionEnabled)));
        sender.sendMessage(settingLine("unemployed-initial-profession.cost-emeralds", String.valueOf(cfg.unemployedInitialProfessionCostEmeralds)));
        sender.sendMessage(settingLine("cycle-max-uses", String.valueOf(cfg.cycleMaxUses)));
        sender.sendMessage(settingLine("profession-swap-success-chance", String.valueOf(cfg.professionSwapChance)));
        sender.sendMessage(settingLine("cure-discount.enabled", String.valueOf(cfg.cureDiscountEnabled)));
        sender.sendMessage(settingLine("cure-discount.radius-blocks", String.valueOf(cfg.cureDiscountRadius)));
        sender.sendMessage(settingLine("cure-discount.bonus-level", String.valueOf(cfg.cureDiscountBonusLevel)));
    }

    private Component settingLine(String key, String value) {
        return Component.text("- ", NamedTextColor.DARK_GRAY)
                .append(Component.text(key, NamedTextColor.AQUA))
                .append(Component.text(": ", NamedTextColor.DARK_GRAY))
                .append(Component.text(value, NamedTextColor.GREEN));
    }

    private void setAndReload(String key, Object value) {
        plugin.getConfig().set(key, value);
        plugin.saveConfig();
        plugin.reloadLocalConfig();
    }

    private void writeTypedValue(String key, String value) {
        Object out;
        if ("enabled".equals(key) || "debug".equals(key) || "cure-discount.enabled".equals(key) || "unemployed-initial-profession.enabled".equals(key)) {
            out = Boolean.parseBoolean(value);
        } else if ("profession-swap-success-chance".equals(key)) {
            out = Double.parseDouble(value);
        } else {
            out = Integer.parseInt(value);
        }
        plugin.getConfig().set(key, out);
        plugin.saveConfig();
    }

    private String readCurrentValue(String key) {
        PaperResetConfig cfg = plugin.resetConfig();
        return switch (key) {
            case "enabled" -> String.valueOf(cfg.enabled);
            case "debug" -> String.valueOf(cfg.debug);
            case "cycle-cost-emeralds" -> String.valueOf(cfg.cycleCostEmeralds);
            case "profession-swap-cost-emeralds" -> String.valueOf(cfg.professionSwapCostEmeralds);
            case "unemployed-initial-profession.enabled" -> String.valueOf(cfg.unemployedInitialProfessionEnabled);
            case "unemployed-initial-profession.cost-emeralds" -> String.valueOf(cfg.unemployedInitialProfessionCostEmeralds);
            case "cycle-max-uses" -> String.valueOf(cfg.cycleMaxUses);
            case "profession-swap-success-chance" -> String.valueOf(cfg.professionSwapChance);
            case "cure-discount.enabled" -> String.valueOf(cfg.cureDiscountEnabled);
            case "cure-discount.radius-blocks" -> String.valueOf(cfg.cureDiscountRadius);
            case "cure-discount.bonus-level" -> String.valueOf(cfg.cureDiscountBonusLevel);
            default -> "?";
        };
    }

    private boolean validateValue(String key, String value) {
        try {
            if ("enabled".equals(key) || "debug".equals(key) || "cure-discount.enabled".equals(key) || "unemployed-initial-profession.enabled".equals(key)) {
                return "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value);
            }
            if ("profession-swap-success-chance".equals(key)) {
                double d = Double.parseDouble(value);
                return d >= 0D && d <= 1D;
            }
            int i = Integer.parseInt(value);
            return i >= 1;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private List<String> prefixMatches(List<String> source, String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String entry : source) {
            if (entry.toLowerCase(Locale.ROOT).startsWith(lower)) {
                out.add(entry);
            }
        }
        return out;
    }
}
