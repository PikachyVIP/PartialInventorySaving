package com.gc.compat;

import com.gc.Config;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CuriosCompat {
    private static final Logger LOGGER = LogUtils.getLogger();

    // Карта для хранения предметов между смертью и респавном
    public static final Map<UUID, List<SavedCurioItem>> SAVED_CURIOS_ITEMS = new ConcurrentHashMap<>();

    public static boolean isCuriosLoaded() {
        return ModList.get().isLoaded("curios");
    }

    public static List<SavedCurioItem> processCuriosSlots(ServerPlayer player) {
        List<SavedCurioItem> savedItems = new ArrayList<>();

        if (!isCuriosLoaded()) {
            return savedItems;
        }

        try {
            LazyOptional<ICuriosItemHandler> optional = CuriosApi.getCuriosInventory(player);

            if (optional.resolve().isPresent()) {
                ICuriosItemHandler handler = optional.resolve().get();
                Map<String, ICurioStacksHandler> curiosInventory = handler.getCurios();

                LOGGER.info("Processing Curios slots for player {}. Found {} slot types",
                        player.getName().getString(), curiosInventory.size());

                for (Map.Entry<String, ICurioStacksHandler> entry : curiosInventory.entrySet()) {
                    String slotId = entry.getKey();
                    ICurioStacksHandler stacksHandler = entry.getValue();

                    LOGGER.info("Processing Curios slot type: {} ({} slots)", slotId, stacksHandler.getSlots());

                    for (int i = 0; i < stacksHandler.getSlots(); i++) {
                        ItemStack stack = stacksHandler.getStacks().getStackInSlot(i);

                        if (!stack.isEmpty()) {
                            LOGGER.info("Found item in Curios slot {}[{}]: {}", slotId, i, stack.getDisplayName().getString());

                            if (!shouldDropCurioItem(stack)) {
                                ItemStack copy = stack.copy();
                                savedItems.add(new SavedCurioItem(copy, slotId, i));

                                // Очищаем слот
                                stacksHandler.getStacks().setStackInSlot(i, ItemStack.EMPTY);

                                LOGGER.info("Saved Curios item: {} from slot {}[{}]",
                                        copy.getDisplayName().getString(), slotId, i);
                            } else {
                                LOGGER.info("Curios item {} will drop (chance check)", stack.getDisplayName().getString());
                            }
                        }
                    }
                }
            } else {
                LOGGER.warn("Curios inventory not present for player {}", player.getName().getString());
            }
        } catch (Exception e) {
            LOGGER.error("Error processing Curios slots", e);
        }

        LOGGER.info("Total saved Curios items: {}", savedItems.size());
        return savedItems;
    }

    public static void restoreCuriosItems(ServerPlayer player, List<SavedCurioItem> savedItems) {
        if (!isCuriosLoaded() || savedItems == null || savedItems.isEmpty()) {
            return;
        }

        LOGGER.info("Restoring {} Curios items for player {}", savedItems.size(), player.getName().getString());

        // Пробуем восстановить с задержкой
        player.getServer().execute(() -> {
            try {
                LazyOptional<ICuriosItemHandler> optional = CuriosApi.getCuriosInventory(player);

                if (optional.resolve().isPresent()) {
                    ICuriosItemHandler handler = optional.resolve().get();

                    for (SavedCurioItem savedItem : savedItems) {
                        Map<String, ICurioStacksHandler> curiosInventory = handler.getCurios();
                        ICurioStacksHandler stacksHandler = curiosInventory.get(savedItem.slotId);

                        if (stacksHandler != null && savedItem.slotIndex < stacksHandler.getSlots()) {
                            ItemStack currentStack = stacksHandler.getStacks().getStackInSlot(savedItem.slotIndex);

                            if (currentStack.isEmpty()) {
                                stacksHandler.getStacks().setStackInSlot(savedItem.slotIndex, savedItem.itemStack.copy());
                                LOGGER.info("Restored Curios item '{}' to slot '{}' index {}",
                                        savedItem.itemStack.getDisplayName().getString(),
                                        savedItem.slotId,
                                        savedItem.slotIndex);
                            } else {
                                LOGGER.warn("Curios slot {}[{}] is not empty during restore", savedItem.slotId, savedItem.slotIndex);
                            }
                        } else {
                            LOGGER.warn("Curios slot {} not found or index {} out of bounds", savedItem.slotId, savedItem.slotIndex);
                        }
                    }
                } else {
                    LOGGER.warn("Curios inventory not available for restore");
                }
            } catch (Exception e) {
                LOGGER.error("Error restoring Curios items", e);
            }
        });
    }

    private static boolean shouldDropCurioItem(ItemStack stack) {
        if (Config.nbtOverrideConfig && hasSavedTag(stack)) {
            return false;
        }

        if (Config.blacklistItems.contains(stack.getItem())) {
            return true;
        }

        if (Config.protectCurios) {
            return false;
        }

        if (Config.whitelistItems.contains(stack.getItem())) {
            return false;
        }

        return Math.random() < Config.curiosChance;
    }

    private static boolean hasSavedTag(ItemStack stack) {
        var tag = stack.getTag();
        return tag != null && tag.contains("Saved") && tag.getBoolean("Saved");
    }

    public static class SavedCurioItem {
        public final ItemStack itemStack;
        public final String slotId;
        public final int slotIndex;

        public SavedCurioItem(ItemStack itemStack, String slotId, int slotIndex) {
            this.itemStack = itemStack;
            this.slotId = slotId;
            this.slotIndex = slotIndex;
        }
    }
}