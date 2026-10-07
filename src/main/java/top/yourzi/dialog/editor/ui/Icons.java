package top.yourzi.dialog.editor.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Item icons for the editor: an id becomes the same icon the dialogue will show in game, so a
 * writer recognises an item by sight instead of reading its registry name.
 */
public final class Icons {
    private Icons() {
    }

    public static ItemStack stack(String id) {
        if (id == null || id.isBlank()) {
            return ItemStack.EMPTY;
        }
        ResourceLocation key = ResourceLocation.tryParse(id.trim());
        if (key == null) {
            return ItemStack.EMPTY;
        }
        Item item = BuiltInRegistries.ITEM.get(key);
        if (item == null || item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(item);
    }

    /** Draws the icon for {@code id} in a {@code size}-pixel box; an unknown id draws a "?" box. */
    public static void draw(GuiGraphics graphics, String id, int x, int y, int size) {
        ItemStack stack = stack(id);
        if (stack.isEmpty()) {
            graphics.fill(x, y, x + size, y + size, Theme.FIELD);
            Theme.border(graphics, x, y, size, size, Theme.BORDER);
            Theme.centered(graphics, "?", x + size / 2, y + size / 2 - 4, Theme.TEXT_MUTED);
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0f);
        float scale = size / 16.0f;
        graphics.pose().scale(scale, scale, 1.0f);
        graphics.renderItem(stack, 0, 0);
        graphics.pose().popPose();
    }
}
