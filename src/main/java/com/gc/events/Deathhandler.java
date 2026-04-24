package com.gc.events;

import com.gc.Config;
import com.gc.Main;
import com.gc.compat.CuriosCompat;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.*;
import java.util.stream.Collectors;

import static com.gc.Main.MODID;

@Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class Deathhandler {

    private static final Map<UUID, List<SavedItem>> savedItemsMap = new HashMap<>();
    private static final Map<UUID, List<CuriosCompat.SavedCurioItem>> curioSavedItemsMap = new HashMap<>();


    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (event.isCanceled()) {
            return;
        }

        List<SavedItem> savedItems = new ArrayList<>();

        processArmorSlots(player, savedItems);
        processOffhandSlot(player, savedItems);
        processInventorySlots(player, savedItems);

        List<CuriosCompat.SavedCurioItem> savedCurioItems = CuriosCompat.processCuriosSlots(player);
        Main.LOGGER.info("LOOOOOOOOOOOOOOOOOOOOOOG DEEEETH");
        if (!savedCurioItems.isEmpty()) {
            CuriosCompat.SAVED_CURIOS_ITEMS.put(player.getUUID(), savedCurioItems);
            Main.LOGGER.info("Stored {} Curios items for player {}", savedCurioItems.size(), player.getName().getString());
        }

        savedItemsMap.put(player.getUUID(), savedItems);
        curioSavedItemsMap.put(player.getUUID(), savedCurioItems);

        if (Config.showDeathMessage) {
            int totalSavedItems = savedItems.size() + savedCurioItems.size();
            if (totalSavedItems > 0) {
                showDeathMessage(player, savedItems, savedCurioItems);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        UUID playerUUID = player.getUUID();

        List<SavedItem> savedItems = savedItemsMap.remove(playerUUID);
        if (savedItems != null && !savedItems.isEmpty()) {
            restoreItems(player, savedItems);
        }

        List<CuriosCompat.SavedCurioItem> savedCurioItems = CuriosCompat.SAVED_CURIOS_ITEMS.remove(player.getUUID());
        if (savedCurioItems != null && !savedCurioItems.isEmpty()) {
            Main.LOGGER.info("Found {} Curios items to restore for player {}", savedCurioItems.size(), player.getName().getString());
            CuriosCompat.restoreCuriosItems(player, savedCurioItems);
        }

    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) {
            return;
        }

        if (!(event.getEntity() instanceof ServerPlayer newPlayer)) {
            return;
        }

        List<SavedItem> savedItems = savedItemsMap.remove(newPlayer.getUUID());
        List<CuriosCompat.SavedCurioItem> savedCurioItems = curioSavedItemsMap.remove(newPlayer.getUUID());

        if (savedItems != null && !savedItems.isEmpty()) {
            restoreItems(newPlayer, savedItems);
        }

        if (savedCurioItems != null && !savedCurioItems.isEmpty()) {
            CuriosCompat.restoreCuriosItems(newPlayer, savedCurioItems);
        }
    }

    private static void processArmorSlots(ServerPlayer player, List<SavedItem> savedItems) {
        processArmorSlot(player, EquipmentSlot.HEAD, Config.protectHelmet, 39, savedItems);
        processArmorSlot(player, EquipmentSlot.CHEST, Config.protectChestplate, 38, savedItems);
        processArmorSlot(player, EquipmentSlot.LEGS, Config.protectLeggings, 37, savedItems);
        processArmorSlot(player, EquipmentSlot.FEET, Config.protectBoots, 36, savedItems);
    }

    private static void processArmorSlot(ServerPlayer player, EquipmentSlot slot, boolean isProtected,
                                         int slotIndex, List<SavedItem> savedItems) {
        ItemStack itemStack = player.getItemBySlot(slot);

        if (itemStack.isEmpty()) {
            return;
        }


        if (shouldDropItem(itemStack, isProtected, Config.armorChance)) {
            return;
        }


        ItemStack copy = itemStack.copy();
        savedItems.add(new SavedItem(copy, "armor_" + slotIndex));

        player.setItemSlot(slot, ItemStack.EMPTY);
    }

    private static void processOffhandSlot(ServerPlayer player, List<SavedItem> savedItems) {
        ItemStack offhandItem = player.getOffhandItem();

        if (offhandItem.isEmpty()) {
            return;
        }

        if (shouldDropItem(offhandItem, Config.protectOffhand, Config.offhandChance)) {
            return;
        }


        ItemStack copy = offhandItem.copy();
        savedItems.add(new SavedItem(copy, "offhand"));


        player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
    }

    private static void processInventorySlots(ServerPlayer player, List<SavedItem> savedItems) {
        Inventory inventory = player.getInventory();

        for (int i = 0; i < inventory.items.size(); i++) {
            ItemStack itemStack = inventory.items.get(i);

            if (itemStack.isEmpty()) {
                continue;
            }

            boolean isHotbar = i < 9;
            boolean isProtected = isHotbar && Config.protectHotbar;
            double dropChance = isHotbar ? Config.hotbarChance : Config.inventoryChance;

            if (shouldDropItem(itemStack, isProtected, dropChance)) {
                continue;
            }

            ItemStack copy = itemStack.copy();
            String slotType = isHotbar ? "hotbar_" : "inventory_";
            savedItems.add(new SavedItem(copy, slotType + i));


            inventory.items.set(i, ItemStack.EMPTY);
        }
    }

    private static boolean shouldDropItem(ItemStack itemStack, boolean isProtected, double dropChance) {
        if (Config.nbtOverrideConfig && hasSavedTag(itemStack)) {
            return false;
        }

        if (Config.blacklistItems.contains(itemStack.getItem())) {
            return true;
        }

        if (isProtected) {
            return false;
        }

        if (Config.whitelistItems.contains(itemStack.getItem())) {
            return false;
        }

        if (!Config.nbtOverrideConfig && hasSavedTag(itemStack)) {
            return false;
        }

        return Math.random() < dropChance;
    }

    private static boolean hasSavedTag(ItemStack itemStack) {
        CompoundTag tag = itemStack.getTag();
        return tag != null && tag.contains("Saved") && tag.getBoolean("Saved");
    }

    private static void restoreItems(ServerPlayer player, List<SavedItem> savedItems) {
        for (SavedItem savedItem : savedItems) {
            ItemStack itemStack = savedItem.itemStack;
            String slotInfo = savedItem.slot;

            if (slotInfo.startsWith("armor_")) {
                int slotIndex = Integer.parseInt(slotInfo.substring(6));
                EquipmentSlot equipmentSlot = switch (slotIndex) {
                    case 39 -> EquipmentSlot.HEAD;
                    case 38 -> EquipmentSlot.CHEST;
                    case 37 -> EquipmentSlot.LEGS;
                    case 36 -> EquipmentSlot.FEET;
                    default -> null;
                };

                if (equipmentSlot != null) {
                    player.setItemSlot(equipmentSlot, itemStack);
                }
            } else if (slotInfo.equals("offhand")) {
                player.setItemSlot(EquipmentSlot.OFFHAND, itemStack);
            } else if (slotInfo.startsWith("hotbar_")) {
                int slotIndex = Integer.parseInt(slotInfo.substring(7));
                if (slotIndex >= 0 && slotIndex < 9) {
                    player.getInventory().items.set(slotIndex, itemStack);
                }
            } else if (slotInfo.startsWith("inventory_")) {
                int slotIndex = Integer.parseInt(slotInfo.substring(10));
                if (slotIndex >= 0 && slotIndex < player.getInventory().items.size()) {
                    player.getInventory().items.set(slotIndex, itemStack);
                }
            }
        }

        player.getInventory().setChanged();
    }

    private static void showDeathMessage(ServerPlayer player, List<SavedItem> savedItems, List<CuriosCompat.SavedCurioItem> savedCurioItems) {
        player.sendSystemMessage(Component.literal("§6=== Сохранённые предметы ==="));

        Map<String, Integer> itemCounts = new LinkedHashMap<>();

        for (SavedItem savedItem : savedItems) {
            ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(savedItem.itemStack.getItem());
            if (itemId != null) {
                String itemName = itemId.toString();
                itemCounts.merge(itemName, savedItem.itemStack.getCount(), Integer::sum);
            }
        }

        for (CuriosCompat.SavedCurioItem savedItem : savedCurioItems) {
            ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(savedItem.itemStack.getItem());
            if (itemId != null) {
                String itemName = itemId.toString() + " §7[Curios: " + savedItem.slotId + "]";
                itemCounts.merge(itemName, savedItem.itemStack.getCount(), Integer::sum);
            }
        }

        for (Map.Entry<String, Integer> entry : itemCounts.entrySet()) {
            player.sendSystemMessage(Component.literal(
                    "§f- " + entry.getKey() + " §7x" + entry.getValue()
            ));
        }

        int totalItems = savedItems.size() + savedCurioItems.size();
        player.sendSystemMessage(Component.literal(
                "§6Всего сохранено: §f" + totalItems + " §6предметов"
        ));
    }

    private static class SavedItem {
        final ItemStack itemStack;
        final String slot;

        SavedItem(ItemStack itemStack, String slot) {
            this.itemStack = itemStack;
            this.slot = slot;
        }
    }
}