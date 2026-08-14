package top.yourzi.dialog.editor.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import top.yourzi.dialog.editor.gui.widget.EditorButton;
import top.yourzi.dialog.editor.gui.EditorRenderHelper;
import top.yourzi.dialog.editor.util.EditorTheme;
import top.yourzi.dialog.model.DisplayItemInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 物品栏选择屏幕：显示玩家当前物品栏（主物品栏 + 快捷栏 + 盔甲 + 副手），
 * 点击任意非空格子即将该物品的 ID 和数量通过回调返回，用于在逻辑页快速添加物品。
 */
public class InventoryItemPickerScreen extends Screen {
    private static final int SLOT_SIZE = 18;
    private static final int COLS = 9;
    private static final int TITLE_Y = 10;
    private static final int GRID_TOP = 28;
    private static final int GAP_BETWEEN_MAIN_AND_HOTBAR = 4;
    private static final int GAP_BETWEEN_HOTBAR_AND_ARMOR = 8;

    private final Consumer<DisplayItemInfo> onItemSelected;
    private final Screen parent;
    private int gridX;
    private int mainGridY;
    private int hotbarY;
    private int armorY;

    public InventoryItemPickerScreen(Consumer<DisplayItemInfo> onItemSelected, Screen parent) {
        super(Component.translatable("gui.vn_edit.inventory_picker.title"));
        this.onItemSelected = onItemSelected;
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        int gridWidth = COLS * SLOT_SIZE;
        this.gridX = (this.width - gridWidth) / 2;
        this.mainGridY = GRID_TOP;
        this.hotbarY = this.mainGridY + 3 * SLOT_SIZE + GAP_BETWEEN_MAIN_AND_HOTBAR;
        this.armorY = this.hotbarY + SLOT_SIZE + GAP_BETWEEN_HOTBAR_AND_ARMOR;
        this.addRenderableWidget(EditorButton.builder(Component.translatable("gui.vn_edit.cancel"), b -> this.onClose())
                .bounds(this.width / 2 - 50, this.height - 30, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        this.renderBackground(g, mx, my, pt);
        g.drawCenteredString(this.font, this.title, this.width / 2, TITLE_Y, EditorTheme.TEXT_PRIMARY);

        Inventory inv = Minecraft.getInstance().player.getInventory();

        // 主物品栏 3 行（slots 9-35）
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < COLS; col++) {
                int slot = 9 + row * COLS + col;
                renderSlot(g, inv.getItem(slot), this.gridX + col * SLOT_SIZE, this.mainGridY + row * SLOT_SIZE, mx, my);
            }
        }
        // 快捷栏 1 行（slots 0-8）
        for (int col = 0; col < COLS; col++) {
            renderSlot(g, inv.getItem(col), this.gridX + col * SLOT_SIZE, this.hotbarY, mx, my);
        }
        // 盔甲 + 副手（1 行）：头/胸/腿/脚 + 间隔 + 副手
        // Inventory 盔甲槽位 36=靴, 37=护腿, 38=胸甲, 39=头盔；反序显示头到脚
        for (int i = 0; i < 4; i++) {
            int slot = 39 - i;
            renderSlot(g, inv.getItem(slot), this.gridX + i * SLOT_SIZE, this.armorY, mx, my);
        }
        // 副手槽位 40，放在第 5 列位置
        renderSlot(g, inv.getItem(40), this.gridX + 4 * SLOT_SIZE, this.armorY, mx, my);

        // 提示文字
        g.drawCenteredString(this.font, Component.translatable("gui.vn_edit.inventory_picker.hint"),
                this.width / 2, this.height - 45, EditorTheme.TEXT_SECONDARY);

        super.render(g, mx, my, pt);

        // Tooltip 在最上层渲染，避免被格子遮挡
        ItemStack hovered = getHoveredStack(inv, mx, my);
        if (hovered != null && !hovered.isEmpty()) {
            List<net.minecraft.util.FormattedCharSequence> lines = new ArrayList<>();
            lines.add(hovered.getHoverName().getVisualOrderText());
            ResourceLocation key = BuiltInRegistries.ITEM.getKey(hovered.getItem());
            if (key != null) {
                lines.add(Component.literal(key.toString())
                        .withStyle(s -> s.withColor(EditorTheme.TEXT_MUTED)).getVisualOrderText());
                lines.add(Component.translatable("gui.vn_edit.inventory_picker.count", hovered.getCount())
                        .withStyle(s -> s.withColor(EditorTheme.TEXT_SECONDARY)).getVisualOrderText());
            }
            g.renderTooltip(this.font, lines, mx, my);
        }
    }

    private void renderSlot(GuiGraphics g, ItemStack stack, int x, int y, int mx, int my) {
        // 格子边框（暗色）
        g.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, EditorTheme.BG_ELEVATED);
        g.fill(x, y, x + SLOT_SIZE, y + 1, EditorTheme.BORDER);
        g.fill(x, y, x + 1, y + SLOT_SIZE, EditorTheme.BORDER);
        g.fill(x + SLOT_SIZE - 1, y, x + SLOT_SIZE, y + SLOT_SIZE, EditorTheme.BORDER);
        g.fill(x, y + SLOT_SIZE - 1, x + SLOT_SIZE, y + SLOT_SIZE, EditorTheme.BORDER);

        boolean hovered = mx >= x + 1 && mx < x + SLOT_SIZE - 1 && my >= y + 1 && my < y + SLOT_SIZE - 1;
        if (hovered && !stack.isEmpty()) {
            g.fill(x + 1, y + 1, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1, EditorRenderHelper.withAlphaRatio(EditorTheme.TEXT_PRIMARY, 0.33f));
        }

        if (!stack.isEmpty()) {
            g.renderItem(stack, x + 1, y + 1);
            g.renderItemDecorations(this.font, stack, x + 1, y + 1);
        }
    }

    private ItemStack getHoveredStack(Inventory inv, int mx, int my) {
        // 主物品栏
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < COLS; col++) {
                int x = this.gridX + col * SLOT_SIZE;
                int y = this.mainGridY + row * SLOT_SIZE;
                if (mx >= x + 1 && mx < x + SLOT_SIZE - 1 && my >= y + 1 && my < y + SLOT_SIZE - 1) {
                    return inv.getItem(9 + row * COLS + col);
                }
            }
        }
        // 快捷栏
        for (int col = 0; col < COLS; col++) {
            int x = this.gridX + col * SLOT_SIZE;
            int y = this.hotbarY;
            if (mx >= x + 1 && mx < x + SLOT_SIZE - 1 && my >= y + 1 && my < y + SLOT_SIZE - 1) {
                return inv.getItem(col);
            }
        }
        // 盔甲 + 副手
        for (int i = 0; i < 5; i++) {
            int x = this.gridX + i * SLOT_SIZE;
            int y = this.armorY;
            if (mx >= x + 1 && mx < x + SLOT_SIZE - 1 && my >= y + 1 && my < y + SLOT_SIZE - 1) {
                return inv.getItem(i < 4 ? 39 - i : 40);
            }
        }
        return null;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            Inventory inv = Minecraft.getInstance().player.getInventory();
            ItemStack clicked = getHoveredStack(inv, (int) mx, (int) my);
            if (clicked != null && !clicked.isEmpty()) {
                ResourceLocation key = BuiltInRegistries.ITEM.getKey(clicked.getItem());
                if (key != null) {
                    DisplayItemInfo info = new DisplayItemInfo(key.toString(), clicked.getCount(), "");
                    this.onItemSelected.accept(info);
                }
                this.onClose();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(this.parent);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, this.width, this.height, EditorTheme.BG_DEEPEST);
    }
}
