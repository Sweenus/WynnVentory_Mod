package com.wynnventory.mixin;

import com.wynntils.core.components.Models;
import com.wynntils.models.gear.type.GearInfo;
import com.wynntils.models.items.WynnItem;
import com.wynntils.models.items.items.game.GearBoxItem;
import com.wynntils.utils.render.FontRenderer;
import com.wynnventory.api.WynnventoryAPI;

import com.wynnventory.model.item.trademarket.TradeMarketItemPriceInfo;
import com.wynnventory.util.ItemStackUtils;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.*;


@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {

    private static final Map<String, Double> priceCache = new HashMap<>();
    private static final WynnventoryAPI wynnventoryAPI = new WynnventoryAPI();
    private static final ItemStackUtils itemStackUtils = new ItemStackUtils();
    private static final double priceStx = 262_144;
    private static final double priceLiquidEmerald = 4_096;
    private static final double priceEmeraldBlock = 64;
    private static boolean debugMode = true;


    private double getItemPrice(ItemStack stack) {
        if (!Screen.hasAltDown())
            return 0.0;

        Optional<WynnItem> maybeItem = Models.Item.getWynnItem(stack);
        if (maybeItem.isEmpty())
            return 0.0;

        WynnItem item = maybeItem.get();

        String cacheKey;

        // ─────────────────────────────
        // GearBox handling
        // ─────────────────────────────
        if (item instanceof GearBoxItem gearBoxItem) {
            String gearboxId = String.valueOf(gearBoxItem.hashCode());
            cacheKey = "GEARBOX:" + gearboxId;

            if (debugMode) System.out.println("gearboxId = " + gearboxId + " for box: " + cacheKey);

            if (priceCache.containsKey(cacheKey)) {
                if (debugMode) System.out.println("Fetching cached price for " + cacheKey); // Debug
                return priceCache.get(cacheKey);
            }

            if (debugMode) System.out.println("Attempting to fetch highest value in " + cacheKey); // Debug
            double highestPrice = processItems(maybeItem);
            priceCache.put(cacheKey, highestPrice);
            if (debugMode) System.out.println("Fetched price of " + highestPrice + " for " + cacheKey); // Debug
            return highestPrice;
        }

        // ─────────────────────────────
        // Normal item handling
        // ─────────────────────────────
        cacheKey = ItemStackUtils.getWynntilsOriginalNameAsString(item);

        if (priceCache.containsKey(cacheKey)) {
            if (debugMode) System.out.println("Fetching cached price for item: " + cacheKey); // Debug
            return priceCache.get(cacheKey);
        }

        if (debugMode) System.out.println("Attempting to fetch price for item: " + cacheKey); // Debug
        TradeMarketItemPriceInfo info = ItemStackUtils.getInfo(stack);
        if (info == null)
            return 0.0;

        double price = info.getUnidentifiedAverage80Price();
        priceCache.put(cacheKey, price);
        if (debugMode) System.out.println("Fetched price of " + price + " for item: " + cacheKey); // Debug
        return price;
    }


    @Inject(
            method = "renderSlot",
            at = @At("TAIL")
    )
    private void wynnventory$renderPriceOverlay(GuiGraphics guiGraphics, Slot slot, CallbackInfo ci) {
        if (!Screen.hasAltDown())
            return;

        ItemStack stack = slot.getItem();
        if (stack.isEmpty()) return;

        double price = getItemPrice(stack);
        if (price <= priceLiquidEmerald) return;

        String text = truncate(price);

        int squareSize = 16;
        int squareX = slot.x;
        int squareY = slot.y;
        int valueColorGreen  = 0xFF00FF00;
        int valueColorOrange = 0xFFFF8000;
        int valueColorRed    = 0xFFFF4000;

        int fillColor = 0;

        if (price >= priceStx) {
            fillColor = valueColorRed;
        } else if (price >= priceLiquidEmerald * 10) {
            fillColor = valueColorOrange;
        } else if (price >= priceLiquidEmerald * 2) {
            fillColor = valueColorGreen;
        }

        if (price >= priceLiquidEmerald * 2) {
            guiGraphics.fill(
                    squareX,
                    squareY,
                    squareX + squareSize,
                    squareY + squareSize,
                    fillColor
            );
        }

        if (Screen.hasControlDown()) {
            guiGraphics.drawString(
                    FontRenderer.getInstance().getFont(),
                    text,
                    slot.x + 18,
                    slot.y + 16 - 7,
                    fillColor,
                    true
            );
        }
    }

    private static String truncate(double value) {
        if (value >= priceStx) {
            long result = (long) (value / priceStx);
            return result + "stx";
        } else if (value >= priceLiquidEmerald) {
            long result = (long) (value / priceLiquidEmerald);
            return result + "le";
        } else if (value >= priceEmeraldBlock) {
            long result = (long) (value / priceEmeraldBlock);
            return result + "eb";
        } else {
            return Long.toString((long) value);
        }
    }

    private static int processItems(Optional<WynnItem> maybeItem) {

        // Process GearBox
        if (maybeItem.get() instanceof GearBoxItem gearBoxItem) {
            boolean valuable = false;
            int highestValue = 0;

            List<GearInfo> possibleGears = Models.Gear.getPossibleGears(gearBoxItem);
            if (possibleGears.isEmpty()) {
                return 0;
            }

            for (GearInfo gearInfo : possibleGears) {
                String gearName = gearInfo.name();

                TradeMarketItemPriceInfo info = ItemStackUtils.getNameInfo(gearName);
                if (info == null) continue;

                int price = info.getUnidentifiedAverage80Price();
                if (price > highestValue)
                    highestValue = price;
                if (price >= priceLiquidEmerald * 2)
                    valuable = true;
            }
            return highestValue;
        }
        return 0;
    }
}