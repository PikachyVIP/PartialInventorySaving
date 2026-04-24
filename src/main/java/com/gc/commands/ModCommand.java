package com.gc.commands;

import com.gc.Config;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = "invsave")
public class ModCommand {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final SuggestionProvider<CommandSourceStack> CHANCES_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(new String[]{"hotbar", "inventory", "armor", "offhand", "curios"}, builder);

    private static final SuggestionProvider<CommandSourceStack> PROTECTION_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(new String[]{"helmet", "chestplate", "leggings", "boots", "offhand", "hotbar", "curios"}, builder);

    private static final SuggestionProvider<CommandSourceStack> MISC_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(new String[]{"show_death_message", "nbt_override_config"}, builder);

    private static final SuggestionProvider<CommandSourceStack> CATEGORY_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(new String[]{"chances", "protection_slots", "misc"}, builder);

    private static final SuggestionProvider<CommandSourceStack> LIST_TYPE_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(new String[]{"whitelist", "blacklist"}, builder);

    private static final SuggestionProvider<CommandSourceStack> ALL_CONFIG_SUGGESTIONS = (context, builder) -> {
        List<String> allConfigs = new ArrayList<>();
        allConfigs.add("hotbar");
        allConfigs.add("inventory");
        allConfigs.add("armor");
        allConfigs.add("offhand");
        allConfigs.add("curios");
        allConfigs.add("helmet");
        allConfigs.add("chestplate");
        allConfigs.add("leggings");
        allConfigs.add("boots");
        allConfigs.add("curios");
        allConfigs.add("show_death_message");
        allConfigs.add("nbt_override_config");
        return SharedSuggestionProvider.suggest(allConfigs.stream().distinct().toArray(String[]::new), builder);
    };

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("dd")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("add")
                        .then(Commands.literal("whitelist")
                                .executes(ModCommand::addToWhitelist)
                        )
                        .then(Commands.literal("blacklist")
                                .executes(ModCommand::addToBlacklist)
                        )
                )
                .then(Commands.literal("remove")
                        .then(Commands.literal("whitelist")
                                .executes(ModCommand::removeFromWhitelist)
                        )
                        .then(Commands.literal("blacklist")
                                .executes(ModCommand::removeFromBlacklist)
                        )
                )
                .then(Commands.literal("tag")
                        .executes(ModCommand::toggleTag)
                )
                .then(Commands.literal("list")
                        .then(Commands.argument("type", StringArgumentType.word())
                                .suggests(LIST_TYPE_SUGGESTIONS)
                                .executes(ModCommand::listItems)
                        )
                )
                .then(Commands.literal("reload")
                        .executes(ModCommand::reloadConfig)
                )
                .then(Commands.literal("config")
                        .then(Commands.literal("set")
                                .then(Commands.literal("chances")
                                        .then(Commands.argument("config", StringArgumentType.word())
                                                .suggests(CHANCES_SUGGESTIONS)
                                                .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.0, 1.0))
                                                        .executes(ModCommand::setChancesConfig)
                                                )
                                        )
                                )
                                .then(Commands.literal("protection_slots")
                                        .then(Commands.argument("config", StringArgumentType.word())
                                                .suggests(PROTECTION_SUGGESTIONS)
                                                .then(Commands.argument("value", BoolArgumentType.bool())
                                                        .executes(ModCommand::setProtectionConfig)
                                                )
                                        )
                                )
                                .then(Commands.literal("misc")
                                        .then(Commands.argument("config", StringArgumentType.word())
                                                .suggests(MISC_SUGGESTIONS)
                                                .then(Commands.argument("value", BoolArgumentType.bool())
                                                        .executes(ModCommand::setMiscConfig)
                                                )
                                        )
                                )
                        )
                        .then(Commands.literal("get")
                                .then(Commands.argument("category", StringArgumentType.word())
                                        .suggests(CATEGORY_SUGGESTIONS)
                                        .then(Commands.argument("config", StringArgumentType.word())
                                                .suggests((context, builder) -> {
                                                    String category = context.getArgument("category", String.class);
                                                    return switch (category) {
                                                        case "chances" -> SharedSuggestionProvider.suggest(
                                                                new String[]{"hotbar", "inventory", "armor", "offhand", "curios"}, builder);
                                                        case "protection_slots" -> SharedSuggestionProvider.suggest(
                                                                new String[]{"helmet", "chestplate", "leggings", "boots", "offhand", "hotbar", "curios"}, builder);
                                                        case "misc" -> SharedSuggestionProvider.suggest(
                                                                new String[]{"show_death_message", "nbt_override_config"}, builder);
                                                        default -> SharedSuggestionProvider.suggest(new String[]{}, builder);
                                                    };
                                                })
                                                .executes(ModCommand::getConfig)
                                        )
                                )
                                .then(Commands.literal("all")
                                        .executes(ModCommand::getAllConfig)
                                )
                        )
                )
        );
    }

    private static int addToWhitelist(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("§cЭту команду может выполнить только игрок!"));
            return 0;
        }

        ItemStack heldItem = player.getMainHandItem();
        if (heldItem.isEmpty()) {
            source.sendFailure(Component.literal("§cВы должны держать предмет в основной руке!"));
            return 0;
        }

        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(heldItem.getItem());
        if (itemId == null) {
            source.sendFailure(Component.literal("§cОшибка получения ID предмета!"));
            return 0;
        }

        String itemIdString = itemId.toString();

        List<String> currentWhitelist = new ArrayList<>(Config.ITEM_WHITELIST.get());

        if (currentWhitelist.contains(itemIdString)) {
            source.sendFailure(Component.literal("§eПредмет §f" + itemIdString + " §eуже есть в whitelist!"));
            return 0;
        }

        currentWhitelist.add(itemIdString);
        Config.updateWhitelist(currentWhitelist);

        source.sendSuccess(() -> Component.literal("§aПредмет §f" + itemIdString + " §aдобавлен в whitelist!"), true);
        LOGGER.info("Added {} to whitelist by player {}", itemIdString, player.getName().getString());

        return 1;
    }

    private static int addToBlacklist(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("§cЭту команду может выполнить только игрок!"));
            return 0;
        }

        ItemStack heldItem = player.getMainHandItem();
        if (heldItem.isEmpty()) {
            source.sendFailure(Component.literal("§cВы должны держать предмет в основной руке!"));
            return 0;
        }

        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(heldItem.getItem());
        if (itemId == null) {
            source.sendFailure(Component.literal("§cОшибка получения ID предмета!"));
            return 0;
        }

        String itemIdString = itemId.toString();

        List<String> currentBlacklist = new ArrayList<>(Config.ITEM_BLACKLIST.get());

        if (currentBlacklist.contains(itemIdString)) {
            source.sendFailure(Component.literal("§eПредмет §f" + itemIdString + " §eуже есть в blacklist!"));
            return 0;
        }

        currentBlacklist.add(itemIdString);
        updateBlacklistConfig(currentBlacklist);

        source.sendSuccess(() -> Component.literal("§aПредмет §f" + itemIdString + " §aдобавлен в blacklist!"), true);
        LOGGER.info("Added {} to blacklist by player {}", itemIdString, player.getName().getString());

        return 1;
    }

    private static int removeFromWhitelist(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("§cЭту команду может выполнить только игрок!"));
            return 0;
        }

        ItemStack heldItem = player.getMainHandItem();
        if (heldItem.isEmpty()) {
            source.sendFailure(Component.literal("§cВы должны держать предмет в основной руке!"));
            return 0;
        }

        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(heldItem.getItem());
        if (itemId == null) {
            source.sendFailure(Component.literal("§cОшибка получения ID предмета!"));
            return 0;
        }

        String itemIdString = itemId.toString();

        List<String> currentWhitelist = new ArrayList<>(Config.ITEM_WHITELIST.get());

        if (!currentWhitelist.contains(itemIdString)) {
            source.sendFailure(Component.literal("§eПредмета §f" + itemIdString + " §eнет в whitelist!"));
            return 0;
        }

        currentWhitelist.remove(itemIdString);
        Config.updateWhitelist(currentWhitelist);

        source.sendSuccess(() -> Component.literal("§aПредмет §f" + itemIdString + " §aудалён из whitelist!"), true);
        LOGGER.info("Removed {} from whitelist by player {}", itemIdString, player.getName().getString());

        return 1;
    }

    private static int removeFromBlacklist(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("§cЭту команду может выполнить только игрок!"));
            return 0;
        }

        ItemStack heldItem = player.getMainHandItem();
        if (heldItem.isEmpty()) {
            source.sendFailure(Component.literal("§cВы должны держать предмет в основной руке!"));
            return 0;
        }

        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(heldItem.getItem());
        if (itemId == null) {
            source.sendFailure(Component.literal("§cОшибка получения ID предмета!"));
            return 0;
        }

        String itemIdString = itemId.toString();

        List<String> currentBlacklist = new ArrayList<>(Config.ITEM_BLACKLIST.get());

        if (!currentBlacklist.contains(itemIdString)) {
            source.sendFailure(Component.literal("§eПредмета §f" + itemIdString + " §eнет в blacklist!"));
            return 0;
        }

        currentBlacklist.remove(itemIdString);
        updateBlacklistConfig(currentBlacklist);

        source.sendSuccess(() -> Component.literal("§aПредмет §f" + itemIdString + " §aудалён из blacklist!"), true);
        LOGGER.info("Removed {} from blacklist by player {}", itemIdString, player.getName().getString());

        return 1;
    }

    private static int listItems(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String type = StringArgumentType.getString(context, "type");

        if (type.equals("whitelist")) {
            List<String> currentWhitelist = new ArrayList<>(Config.ITEM_WHITELIST.get());

            if (currentWhitelist.isEmpty()) {
                source.sendSuccess(() -> Component.literal("§eWhitelist пуст"), false);
                return 1;
            }

            source.sendSuccess(() -> Component.literal("§6=== §fWhitelist предметов §6==="), false);
            for (String itemId : currentWhitelist) {
                source.sendSuccess(() -> Component.literal("§f- " + itemId), false);
            }
            source.sendSuccess(() -> Component.literal("§6Всего предметов: §f" + currentWhitelist.size()), false);
        } else if (type.equals("blacklist")) {
            List<String> currentBlacklist = new ArrayList<>(Config.ITEM_BLACKLIST.get());

            if (currentBlacklist.isEmpty()) {
                source.sendSuccess(() -> Component.literal("§eBlacklist пуст"), false);
                return 1;
            }

            source.sendSuccess(() -> Component.literal("§6=== §fBlacklist предметов §6==="), false);
            for (String itemId : currentBlacklist) {
                source.sendSuccess(() -> Component.literal("§f- " + itemId), false);
            }
            source.sendSuccess(() -> Component.literal("§6Всего предметов: §f" + currentBlacklist.size()), false);
        } else {
            source.sendFailure(Component.literal("§cИспользуйте: whitelist или blacklist"));
            return 0;
        }

        return 1;
    }

    private static void updateBlacklistConfig(List<String> newBlacklist) {
        Config.ITEM_BLACKLIST.set(newBlacklist);

        if (Config.modConfig != null) {
            Config.saveConfig();
        }

        Config.updateBlacklistField();

        LOGGER.info("Blacklist updated: {}", newBlacklist);
    }

    private static int setChancesConfig(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String config = StringArgumentType.getString(context, "config");
        double value = DoubleArgumentType.getDouble(context, "value");

        try {
            switch (config) {
                case "hotbar" -> {
                    Config.HOTBAR_CHANCE.set(value);
                    Config.hotbarChance = value;
                }
                case "inventory" -> {
                    Config.INVENTORY_CHANCE.set(value);
                    Config.inventoryChance = value;
                }
                case "armor" -> {
                    Config.ARMOR_CHANCE.set(value);
                    Config.armorChance = value;
                }
                case "offhand" -> {
                    Config.OFFHAND_CHANCE.set(value);
                    Config.offhandChance = value;
                }
                case "curios" -> {
                    Config.CURIOS_CHANCE.set(value);
                    Config.curiosChance = value;
                }
                default -> {
                    source.sendFailure(Component.literal("§cНеизвестный параметр: " + config));
                    return 0;
                }
            }

            Config.saveConfig();
            source.sendSuccess(() -> Component.literal("§aПараметр §f" + config + " §aустановлен на §f" + value), true);
            LOGGER.info("Config {} set to {}", config, value);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("§cОшибка установки параметра: " + e.getMessage()));
            LOGGER.error("Error setting config", e);
            return 0;
        }
    }

    private static int setProtectionConfig(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String config = StringArgumentType.getString(context, "config");
        boolean value = BoolArgumentType.getBool(context, "value");

        try {
            switch (config) {
                case "helmet" -> {
                    Config.PROTECT_HELMET.set(value);
                    Config.protectHelmet = value;
                }
                case "chestplate" -> {
                    Config.PROTECT_CHESTPLATE.set(value);
                    Config.protectChestplate = value;
                }
                case "leggings" -> {
                    Config.PROTECT_LEGGINGS.set(value);
                    Config.protectLeggings = value;
                }
                case "boots" -> {
                    Config.PROTECT_BOOTS.set(value);
                    Config.protectBoots = value;
                }
                case "offhand" -> {
                    Config.PROTECT_OFFHAND.set(value);
                    Config.protectOffhand = value;
                }
                case "hotbar" -> {
                    Config.PROTECT_HOTBAR.set(value);
                    Config.protectHotbar = value;
                }
                case "curios" -> {
                    Config.PROTECT_CURIOS.set(value);
                    Config.protectCurios = value;
                }
                default -> {
                    source.sendFailure(Component.literal("§cНеизвестный слот: " + config));
                    return 0;
                }
            }

            Config.saveConfig();
            source.sendSuccess(() -> Component.literal("§aЗащита слота §f" + config + " §aустановлена на §f" + value), true);
            LOGGER.info("Protection {} set to {}", config, value);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("§cОшибка установки параметра: " + e.getMessage()));
            LOGGER.error("Error setting protection config", e);
            return 0;
        }
    }

    private static int setMiscConfig(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String config = StringArgumentType.getString(context, "config");
        boolean value = BoolArgumentType.getBool(context, "value");

        try {
            switch (config) {
                case "show_death_message" -> {
                    Config.SHOW_DEATH_MESSAGE.set(value);
                    Config.showDeathMessage = value;
                    Config.saveConfig();
                    source.sendSuccess(() -> Component.literal("§aПоказ сообщений о смерти установлен на §f" + value), true);
                    LOGGER.info("show_death_message set to {}", value);
                }
                case "nbt_override_config" -> {
                    Config.NBT_OVERRIDE_CONFIG.set(value);
                    Config.nbtOverrideConfig = value;
                    Config.saveConfig();
                    source.sendSuccess(() -> Component.literal("§aПриоритет NBT тега установлен на §f" + value), true);
                    LOGGER.info("nbt_override_config set to {}", value);
                }
                default -> {
                    source.sendFailure(Component.literal("§cНеизвестный параметр: " + config));
                    return 0;
                }
            }
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("§cОшибка установки параметра: " + e.getMessage()));
            LOGGER.error("Error setting misc config", e);
            return 0;
        }
    }

    private static int getConfig(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String category = StringArgumentType.getString(context, "category");
        String config = StringArgumentType.getString(context, "config");

        try {
            String value = getConfigValue(category, config);
            if (value != null) {
                source.sendSuccess(() -> Component.literal("§6" + category + "." + config + " = §f" + value), false);
                return 1;
            } else {
                source.sendFailure(Component.literal("§cНеизвестная конфигурация: " + category + "." + config));
                return 0;
            }
        } catch (Exception e) {
            source.sendFailure(Component.literal("§cОшибка получения параметра: " + e.getMessage()));
            LOGGER.error("Error getting config", e);
            return 0;
        }
    }

    private static int getAllConfig(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        source.sendSuccess(() -> Component.literal("§6=== §fТекущая конфигурация §6==="), false);

        // Chances
        source.sendSuccess(() -> Component.literal("§6[chances]"), false);
        source.sendSuccess(() -> Component.literal("§f  hotbar = " + Config.hotbarChance), false);
        source.sendSuccess(() -> Component.literal("§f  inventory = " + Config.inventoryChance), false);
        source.sendSuccess(() -> Component.literal("§f  armor = " + Config.armorChance), false);
        source.sendSuccess(() -> Component.literal("§f  offhand = " + Config.offhandChance), false);
        source.sendSuccess(() -> Component.literal("§f  curios = " + Config.curiosChance), false);

        // Protection slots
        source.sendSuccess(() -> Component.literal("§6[protection_slots]"), false);
        source.sendSuccess(() -> Component.literal("§f  helmet = " + Config.protectHelmet), false);
        source.sendSuccess(() -> Component.literal("§f  chestplate = " + Config.protectChestplate), false);
        source.sendSuccess(() -> Component.literal("§f  leggings = " + Config.protectLeggings), false);
        source.sendSuccess(() -> Component.literal("§f  boots = " + Config.protectBoots), false);
        source.sendSuccess(() -> Component.literal("§f  offhand = " + Config.protectOffhand), false);
        source.sendSuccess(() -> Component.literal("§f  hotbar = " + Config.protectHotbar), false);
        source.sendSuccess(() -> Component.literal("§f  curios = " + Config.protectCurios), false);

        // Misc
        source.sendSuccess(() -> Component.literal("§6[misc]"), false);
        source.sendSuccess(() -> Component.literal("§f  show_death_message = " + Config.showDeathMessage), false);
        source.sendSuccess(() -> Component.literal("§f  nbt_override_config = " + Config.nbtOverrideConfig), false);

        return 1;
    }

    private static String getConfigValue(String category, String config) {
        return switch (category) {
            case "chances" -> switch (config) {
                case "hotbar" -> String.valueOf(Config.hotbarChance);
                case "inventory" -> String.valueOf(Config.inventoryChance);
                case "armor" -> String.valueOf(Config.armorChance);
                case "offhand" -> String.valueOf(Config.offhandChance);
                case "curios" -> String.valueOf(Config.curiosChance);
                default -> null;
            };
            case "protection_slots" -> switch (config) {
                case "helmet" -> String.valueOf(Config.protectHelmet);
                case "chestplate" -> String.valueOf(Config.protectChestplate);
                case "leggings" -> String.valueOf(Config.protectLeggings);
                case "boots" -> String.valueOf(Config.protectBoots);
                case "offhand" -> String.valueOf(Config.protectOffhand);
                case "hotbar" -> String.valueOf(Config.protectHotbar);
                case "curios" -> String.valueOf(Config.protectCurios);
                default -> null;
            };
            case "misc" -> switch (config) {
                case "show_death_message" -> String.valueOf(Config.showDeathMessage);
                case "nbt_override_config" -> String.valueOf(Config.nbtOverrideConfig);
                default -> null;
            };
            default -> null;
        };
    }

    private static int toggleTag(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("§cЭту команду может выполнить только игрок!"));
            return 0;
        }

        ItemStack heldItem = player.getMainHandItem();
        if (heldItem.isEmpty()) {
            source.sendFailure(Component.literal("§cВы должны держать предмет в основной руке!"));
            return 0;
        }

        CompoundTag tag = heldItem.getOrCreateTag();

        if (tag.contains("Saved") && tag.getBoolean("Saved")) {
            tag.remove("Saved");
            if (tag.isEmpty()) {
                heldItem.setTag(null);
            }
            source.sendSuccess(() -> Component.literal("§eТег §f{Saved:1b} §eубран с предмета"), true);
        } else {
            tag.putBoolean("Saved", true);
            source.sendSuccess(() -> Component.literal("§aТег §f{Saved:1b} §aдобавлен на предмет"), true);
        }

        return 1;
    }

    private static int reloadConfig(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        try {
            source.getServer().getCommands().performPrefixedCommand(
                    source.getServer().createCommandSourceStack(),
                    "reload"
            );

            Config.reloadConfig();

            source.sendSuccess(() -> Component.literal("§aКонфигурация успешно перезагружена!"), true);
            LOGGER.info("Config reloaded via /dd reload command");
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("§cОшибка перезагрузки: " + e.getMessage()));
            LOGGER.error("Error reloading config", e);
            return 0;
        }
    }

    private static int listWhitelist(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        List<? extends String> currentWhitelist = Config.ITEM_WHITELIST.get();

        if (currentWhitelist.isEmpty()) {
            source.sendSuccess(() -> Component.literal("§eWhitelist пуст"), false);
            return 1;
        }

        source.sendSuccess(() -> Component.literal("§6=== §fWhitelist предметов §6==="), false);

        for (String itemId : currentWhitelist) {
            source.sendSuccess(() -> Component.literal("§f- " + itemId), false);
        }

        source.sendSuccess(() -> Component.literal("§6Всего предметов: §f" + currentWhitelist.size()), false);

        return 1;
    }
}