package top.yourzi.dialog.editor.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import top.yourzi.dialog.editor.ui.Button;
import top.yourzi.dialog.editor.ui.Column;
import top.yourzi.dialog.editor.ui.Modal;
import top.yourzi.dialog.editor.ui.Nodes;
import top.yourzi.dialog.editor.ui.Row;
import top.yourzi.dialog.editor.ui.ScrollView;
import top.yourzi.dialog.editor.ui.TextBox;
import top.yourzi.dialog.editor.ui.Theme;
import top.yourzi.dialog.editor.ui.UiNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Item picker: a grid of item icons rather than a list of item ids.
 *
 * <p>Two sources are offered because they answer different questions: the backpack holds what the
 * writer is actually carrying, and everything holds any registered item. Both are filtered by
 * search, and a click picks that item at that stack size.
 */
public final class ItemPickerSheet extends Modal {
    private static final int CELL = 42;

    /** One entry: what to draw, which id it maps to and how many to suggest. */
    private record Entry(ItemStack stack, String id, String name, int count, String search) {
        String detail() {
            return this.id + (this.count > 1 ? " ×" + this.count : "");
        }
    }

    private final Consumer<ItemStack> onChosen;
    private final TextBox filter = new TextBox("");
    private final Grid grid = new Grid();
    private final Button backpackTab = Button.of(Theme.tr("picker.tab_inventory"), () -> this.setSource(false))
            .tone(Button.Tone.TAB).fit();
    private final Button allTab = Button.of(Theme.tr("picker.tab_all"), () -> this.setSource(true))
            .tone(Button.Tone.TAB).fit();
    private final List<Entry> backpack = new ArrayList<>();
    private final List<Entry> everything = new ArrayList<>();
    private boolean all;

    private ItemPickerSheet(Consumer<ItemStack> onChosen) {
        super(Theme.tr("picker.item"));
        this.onChosen = onChosen;
        this.setCardWidth(420);
        this.collect();
    }

    /** Opens the picker; {@code onChosen} receives the clicked stack, never empty. */
    public static void open(top.yourzi.dialog.editor.ui.UiHost host, Consumer<ItemStack> onChosen) {
        ItemPickerSheet sheet = new ItemPickerSheet(onChosen);
        sheet.build();
        host.open(sheet, true, false);
        host.focus(sheet.filter);
    }

    private void setSource(boolean all) {
        this.all = all;
        this.backpackTab.selected(!all);
        this.allTab.selected(all);
        this.refresh();
    }

    /** Reads the backpack, and the registry plus its names, once per opening. */
    private void collect() {
        Map<String, Integer> carried = new LinkedHashMap<>();
        if (Minecraft.getInstance().player != null) {
            Inventory inventory = Minecraft.getInstance().player.getInventory();
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (stack.isEmpty()) {
                    continue;
                }
                String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                carried.merge(id, stack.getCount(), Integer::sum);
            }
        }
        for (Map.Entry<String, Integer> entry : carried.entrySet()) {
            Entry built = this.entry(entry.getKey(), entry.getValue());
            if (built != null) {
                this.backpack.add(built);
            }
        }
        for (ResourceLocation key : BuiltInRegistries.ITEM.keySet()) {
            if (this.everything.size() >= 4096) {
                break;
            }
            Entry built = this.entry(key.toString(), 1);
            if (built != null) {
                this.everything.add(built);
            }
        }
        this.everything.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.name, b.name));
    }

    private Entry entry(String id, int count) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null) {
            return null;
        }
        var item = BuiltInRegistries.ITEM.get(key);
        if (item == null || item == Items.AIR) {
            return null;
        }
        ItemStack stack = new ItemStack(item, Math.max(1, Math.min(64, count)));
        String name = stack.getHoverName().getString();
        if (name.isBlank() || name.equals(id)) {
            name = key.getPath();
        }
        String search = (name + " " + id).toLowerCase(Locale.ROOT);
        return new Entry(stack, id, name, count, search);
    }

    @Override
    protected void buildBody(Column body) {
        this.filter.placeholder(Theme.tr("picker.filter"));
        this.filter.onChange(text -> this.refresh());
        Row tabs = new Row().gap(2);
        tabs.add(this.backpackTab);
        tabs.add(this.allTab);
        tabs.add(Nodes.fill());
        body.add(this.filter);
        body.add(tabs);
        body.add(this.grid);
        this.setSource(false);
    }

    private void refresh() {
        String needle = this.filter.value().trim().toLowerCase(Locale.ROOT);
        List<Entry> source = this.all ? this.everything : this.backpack;
        List<Entry> visible = new ArrayList<>();
        for (Entry entry : source) {
            if (needle.isEmpty() || entry.search.contains(needle)) {
                visible.add(entry);
            }
        }
        this.grid.setItems(visible);
        this.grid.emptyText(Theme.tr(source.isEmpty() ? "picker.empty_inventory" : "picker.empty"));
    }

    private void choose(Entry entry) {
        if (entry == null) {
            return;
        }
        this.dismissLayer();
        this.onChosen.accept(entry.stack.copyWithCount(Math.max(1, Math.min(64, entry.count))));
    }


    /**
     * Icon grid. Painted directly instead of one widget per item, so filtering a thousand entries
     * costs only the rows on screen.
     */
    private final class Grid extends UiNode {
        private List<Entry> items = List.of();
        private Component empty = Component.empty();
        private int hovered = -1;
        private int columns = 1;
        private int cell = CELL;

        void setItems(List<Entry> items) {
            this.items = items;
            this.hovered = -1;
            this.invalidateLayout();
        }

        void emptyText(Component empty) {
            this.empty = empty;
        }

        private int cellWidth() {
            return this.columns <= 0 ? this.cell : Math.max(20, this.width() / this.columns);
        }

        private int rows() {
            return this.columns <= 0 ? 0 : (this.items.size() + this.columns - 1) / this.columns;
        }

        @Override
        public int measureHeight(int width) {
            this.columns = Math.max(1, width / CELL);
            this.cell = Math.max(28, width / this.columns);
            return Math.max(this.cell, this.rows() * this.cell) + 2;
        }

        private int indexAt(double mouseX, double mouseY) {
            if (this.columns <= 0 || this.items.isEmpty()) {
                return -1;
            }
            int column = (int) ((mouseX - this.x()) / this.cellWidth());
            int row = (int) ((mouseY - this.y()) / this.cell);
            if (column < 0 || column >= this.columns || row < 0) {
                return -1;
            }
            int index = row * this.columns + column;
            return index >= 0 && index < this.items.size() ? index : -1;
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button != 0) {
                return true;
            }
            int index = this.indexAt(mouseX, mouseY);
            if (index >= 0) {
                ItemPickerSheet.this.choose(this.items.get(index));
            }
            return true;
        }

        @Override
        public void onMouseMoved(double mouseX, double mouseY) {
            this.hovered = this.indexAt(mouseX, mouseY);
        }

        @Override
        public Component tooltip() {
            if (this.hovered < 0 || this.hovered >= this.items.size()) {
                return null;
            }
            Entry entry = this.items.get(this.hovered);
            return Component.literal(entry.name + "  ·  " + entry.detail());
        }

        @Override
        protected void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            this.hovered = this.indexAt(mouseX, mouseY);
            if (this.items.isEmpty()) {
                Theme.centered(graphics, this.empty.getString(), this.x() + this.width() / 2,
                        this.y() + 12, Theme.TEXT_MUTED);
                return;
            }
            int cellWidth = this.cellWidth();
            // Only the rows inside the scrolling viewport are drawn.
            int viewTop = this.y();
            int viewBottom = this.bottom();
            for (UiNode node = this.parent(); node != null; node = node.parent()) {
                if (node instanceof ScrollView view) {
                    viewTop = view.y();
                    viewBottom = view.bottom();
                    break;
                }
            }
            int firstRow = Math.max(0, (viewTop - this.y()) / this.cell);
            int lastRow = Math.min(this.rows() - 1, (viewBottom - this.y()) / this.cell);
            for (int index = firstRow * this.columns; index < Math.min(this.items.size(), (lastRow + 1) * this.columns); index++) {
                int column = index % this.columns;
                int row = index / this.columns;
                int cellX = this.x() + column * cellWidth;
                int cellY = this.y() + row * this.cell;
                Entry entry = this.items.get(index);
                boolean hovered = index == this.hovered;
                if (hovered) {
                    graphics.fill(cellX + 1, cellY + 1, cellX + cellWidth - 1, cellY + this.cell - 1, Theme.HOVER);
                    Theme.border(graphics, cellX + 1, cellY + 1, cellWidth - 2, this.cell - 2, Theme.ACCENT);
                }
                int icon = Math.min(28, Math.max(16, this.cell - 14));
                int iconX = cellX + (cellWidth - icon) / 2;
                int iconY = cellY + 3;
                graphics.pose().pushPose();
                graphics.pose().translate(iconX, iconY, 0.0f);
                graphics.pose().scale(icon / 16.0f, icon / 16.0f, 1.0f);
                graphics.renderItem(entry.stack, 0, 0);
                graphics.pose().popPose();
                Theme.textIn(graphics, entry.name, cellX + 2, cellY + icon + 4, cellWidth - 4, 10,
                        hovered ? Theme.TEXT : Theme.TEXT_DIM);
            }
        }
    }
}
