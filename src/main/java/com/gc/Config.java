package com.gc;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.IConfigEvent;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Mod.EventBusSubscriber(modid = Main.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public  static final ForgeConfigSpec.DoubleValue HOTBAR_CHANCE = BUILDER
            .comment("Шанс выпадения для хотбара")
            .defineInRange("chances.hotbar", 0.5, 0.0, 1.0);

    public  static final ForgeConfigSpec.DoubleValue INVENTORY_CHANCE = BUILDER
            .comment("Шанс выпадения для остального инвентаря")
            .defineInRange("chances.inventory", 0.7, 0.0, 1.0);

    public  static final ForgeConfigSpec.DoubleValue ARMOR_CHANCE = BUILDER
            .comment("Шанс выпадения брони")
            .defineInRange("chances.armor", 0.2, 0.0, 1.0);

    public  static final ForgeConfigSpec.DoubleValue OFFHAND_CHANCE = BUILDER
            .comment("Шанс выпадения из левой руки")
            .defineInRange("chances.offhand", 0.3, 0.0, 1.0);

    public  static final ForgeConfigSpec.BooleanValue PROTECT_HELMET = BUILDER
            .comment("Если true - предметы не выпадают")
            .define("protection_slots.helmet", false);

    public  static final ForgeConfigSpec.BooleanValue PROTECT_CHESTPLATE = BUILDER
            .comment("Если true - предметы не выпадают")
            .define("protection_slots.chestplate", false);

    public  static final ForgeConfigSpec.BooleanValue PROTECT_LEGGINGS = BUILDER
            .comment("Если true - предметы не выпадают")
            .define("protection_slots.leggings", false);

    public  static final ForgeConfigSpec.BooleanValue PROTECT_BOOTS = BUILDER
            .comment("Если true - предметы не выпадают")
            .define("protection_slots.boots", false);

    public  static final ForgeConfigSpec.BooleanValue PROTECT_OFFHAND = BUILDER
            .comment("Если true - предметы не выпадают")
            .define("protection_slots.offhand", false);

    public  static final ForgeConfigSpec.BooleanValue PROTECT_HOTBAR = BUILDER
            .comment("Если true - весь хотбар не выпадает")
            .define("protection_slots.hotbar", false);

    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> ITEM_WHITELIST = BUILDER
            .comment("Список ID предметов, не выпадающие при смерти")
            .defineListAllowEmpty("protection_items.whitelist",
                    List.of("minecraft:netherite_ingot", "minecraft:elytra"),
                    Config::validateItemName
            );

    public static final ForgeConfigSpec.DoubleValue CURIOS_CHANCE = BUILDER
            .comment("Шанс выпадения для слотов Curios")
            .defineInRange("chances.curios", 0.7, 0.0, 1.0);


    public static final ForgeConfigSpec.BooleanValue PROTECT_CURIOS = BUILDER
            .comment("Если true - все слоты Curios не выпадают")
            .define("protection_slots.curios", false);

    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> ITEM_BLACKLIST = BUILDER
            .comment("Список ID предметов, которые всегда выпадают при смерти (исключение: предметы с тегом Saved при nbt_override_config = true)")
            .defineListAllowEmpty("protection_items.blacklist",
                    List.of("minecraft:dirt", "minecraft:cobblestone"),
                    Config::validateItemName
            );

    public static final ForgeConfigSpec.BooleanValue SHOW_DEATH_MESSAGE = BUILDER
            .comment("Показывать ли сообщение в чате после смерти с информацией о сохранённых предметах")
            .define("misc.show_death_message", true);
    public static final ForgeConfigSpec.BooleanValue NBT_OVERRIDE_CONFIG = BUILDER
            .comment("Приоритет тега Saved над конфигом (рекомендуется true)")
            .define("misc.nbt_override_config", true);

    static final ForgeConfigSpec SPEC = BUILDER.build();

    public static double hotbarChance;
    public static double inventoryChance;
    public static double armorChance;
    public static double offhandChance;
    public static double curiosChance;

    public static boolean protectHelmet;
    public static boolean protectChestplate;
    public static boolean protectLeggings;
    public static boolean protectBoots;
    public static boolean protectOffhand;
    public static boolean protectHotbar;
    public static boolean protectCurios;

    public static Set<Item> whitelistItems;
    public static Set<Item> blacklistItems;
    public static boolean showDeathMessage;
    public static boolean nbtOverrideConfig;

    public  static ModConfig modConfig;

    private static boolean validateItemName(final Object obj) {
        if (obj instanceof final String itemName) {
            ResourceLocation resourceLocation = new ResourceLocation(itemName);
            return ForgeRegistries.ITEMS.containsKey(resourceLocation);
        }
        return false;
    }

    public static void init() {
        // Не требуется
    }

    public static void updateWhitelist(List<String> newWhitelist) {
        ITEM_WHITELIST.set(newWhitelist);

        if (modConfig != null) {
            modConfig.save();
        }

        updateWhitelistField();

        LOGGER.info("Whitelist updated: {}", newWhitelist);
    }

    public static void reloadConfig() {
        loadAllValues();
        LOGGER.info("Config values reloaded from SPEC");
    }

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        modConfig = event.getConfig();
        loadAllValues();
    }

    @SubscribeEvent
    static void onReloading(final ModConfigEvent.Reloading event) {
        modConfig = event.getConfig();
        loadAllValues();

    }

    public static void saveConfig() {
        if (modConfig != null) {
            modConfig.save();
        }
    }

    private static void loadAllValues() {
        hotbarChance = HOTBAR_CHANCE.get();
        inventoryChance = INVENTORY_CHANCE.get();
        armorChance = ARMOR_CHANCE.get();
        offhandChance = OFFHAND_CHANCE.get();

        protectHelmet = PROTECT_HELMET.get();
        protectChestplate = PROTECT_CHESTPLATE.get();
        protectLeggings = PROTECT_LEGGINGS.get();
        protectBoots = PROTECT_BOOTS.get();
        protectOffhand = PROTECT_OFFHAND.get();
        protectHotbar = PROTECT_HOTBAR.get();

        curiosChance = CURIOS_CHANCE.get();
        protectCurios = PROTECT_CURIOS.get();

        updateWhitelistField();
        updateBlacklistField();

        showDeathMessage = SHOW_DEATH_MESSAGE.get();
        nbtOverrideConfig = NBT_OVERRIDE_CONFIG.get();
    }

    public  static void updateWhitelistField() {
        whitelistItems = ITEM_WHITELIST.get().stream()
                .map(itemName -> ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemName)))
                .filter(item -> item != null)
                .collect(Collectors.toSet());
    }
    public  static void updateBlacklistField() {
        blacklistItems = ITEM_BLACKLIST.get().stream()
                .map(itemName -> ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemName)))
                .filter(item -> item != null)
                .collect(Collectors.toSet());
    }
}