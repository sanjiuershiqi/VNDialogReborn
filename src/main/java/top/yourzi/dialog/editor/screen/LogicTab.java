package top.yourzi.dialog.editor.screen;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import top.yourzi.dialog.editor.ui.Button;
import top.yourzi.dialog.editor.ui.Column;
import top.yourzi.dialog.editor.ui.Icons;
import top.yourzi.dialog.editor.ui.Nodes;
import top.yourzi.dialog.editor.ui.Paragraph;
import top.yourzi.dialog.editor.ui.Row;
import top.yourzi.dialog.editor.ui.TextBox;
import top.yourzi.dialog.editor.ui.Theme;
import top.yourzi.dialog.editor.ui.UiNode;
import top.yourzi.dialog.model.DialogEntry;
import top.yourzi.dialog.model.DisplayItemInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Logic tab: what the node does besides showing text.
 *
 * <p>Commands run in order and items render in order, so every list row carries its own reorder and
 * delete controls instead of the list being edited as one block of text.
 */
final class LogicTab extends Column {
    private final EditorContext context;
    private final TextBox visibility = new TextBox("");
    private final Column commandRows = new Column().gap(2);
    private final Column itemRows = new Column().gap(2);
    private Consumer<Consumer<ItemStack>> inventoryPicker;
    private DialogEntry entry;
    private boolean binding;

    LogicTab(EditorContext context) {
        this.context = context;
        this.gap(3).padding(2);

        this.add(Nodes.section(Theme.tr("section.visibility")));
        this.add(this.visibility);
        this.add(Paragraph.of(Theme.tr("logic.visibility_help")).color(Theme.TEXT_MUTED));

        this.add(Nodes.section(Theme.tr("section.commands")));
        this.add(this.commandRows);
        this.add(Button.of(Theme.tr("logic.add_command"), this::appendCommand).fit());

        this.add(Nodes.section(Theme.tr("section.items")));
        this.add(this.itemRows);
        Row itemButtons = new Row().gap(2);
        itemButtons.add(Button.of(Theme.tr("logic.add_item"), this::appendItem).fit());
        itemButtons.add(Button.of(Theme.tr("logic.pick_item"), () -> this.pickItem(this::addItem)).fit());
        itemButtons.add(Nodes.fill());
        this.add(itemButtons);

        this.visibility.placeholder(Theme.tr("logic.visibility_hint"));
        this.visibility.onChange(value -> {
            if (this.entry != null && !this.binding) {
                this.entry.setVisibilityCommand(value.isBlank() ? null : value.trim());
                this.context.touch(true);
            }
        });
    }

    /** @param inventoryPicker opens the item grid and hands back the chosen stack */
    void setInventoryPicker(Consumer<Consumer<ItemStack>> inventoryPicker) {
        this.inventoryPicker = inventoryPicker;
    }

    /** Opens the item grid; {@code target} receives what the writer picks. */
    private void pickItem(Consumer<ItemStack> target) {
        if (this.inventoryPicker != null) {
            this.inventoryPicker.accept(target);
        }
    }

    void bind(DialogEntry entry) {
        this.binding = true;
        try {
            this.entry = entry;
            this.commandRows.clear();
            this.itemRows.clear();
            if (entry == null) {
                return;
            }
            this.visibility.setValue(entry.getVisibilityCommand() == null ? "" : entry.getVisibilityCommand());
            List<String> commands = this.commands();
            for (int i = 0; i < commands.size(); i++) {
                this.commandRows.add(new CommandRow(commands.get(i), i, commands.size()));
            }
            List<DisplayItemInfo> items = this.items();
            for (int i = 0; i < items.size(); i++) {
                this.itemRows.add(new ItemRow(items.get(i), i, items.size()));
            }
        } finally {
            this.binding = false;
        }
    }

    private List<String> commands() {
        return this.entry.getCommands() == null ? new ArrayList<>() : new ArrayList<>(this.entry.getCommands());
    }

    private List<DisplayItemInfo> items() {
        return this.entry.getDisplayItems() == null ? new ArrayList<>() : new ArrayList<>(this.entry.getDisplayItems());
    }

    private void appendCommand() {
        if (this.entry == null) {
            return;
        }
        List<String> list = this.commands();
        list.add("");
        this.entry.setCommands(list);
        this.context.touchStructure();
    }

    private void setCommand(int index, String value) {
        List<String> list = this.commands();
        if (index < 0 || index >= list.size()) {
            return;
        }
        list.set(index, value);
        this.entry.setCommands(list);
        this.context.touch(true);
    }

    private void moveCommand(int index, int delta) {
        List<String> list = this.commands();
        int target = index + delta;
        if (index < 0 || target < 0 || target >= list.size()) {
            return;
        }
        list.add(target, list.remove(index));
        this.entry.setCommands(list);
        this.context.touchStructure();
    }

    private void removeCommand(int index) {
        List<String> list = this.commands();
        if (index < 0 || index >= list.size()) {
            return;
        }
        list.remove(index);
        this.entry.setCommands(list.isEmpty() ? null : list);
        this.context.touchStructure();
    }

    private void appendItem() {
        if (this.entry == null) {
            return;
        }
        this.addItem(new DisplayItemInfo("minecraft:stone", 1, null));
    }

    private void addItem(DisplayItemInfo info) {
        if (this.entry == null || info == null) {
            return;
        }
        List<DisplayItemInfo> list = this.items();
        list.add(info);
        this.entry.setDisplayItems(list);
        this.context.touchStructure();
    }

    private void addItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        this.addItem(new DisplayItemInfo(net.minecraft.core.registries.BuiltInRegistries.ITEM
                .getKey(stack.getItem()).toString(), Math.max(1, stack.getCount()), componentsNbt(stack)));
    }

    /** {@code {components:{...}}} for an item that differs from its default (name, lore, ...), else null. */
    private static String componentsNbt(ItemStack stack) {
        net.minecraft.client.multiplayer.ClientLevel level = net.minecraft.client.Minecraft.getInstance().level;
        if (level == null || stack.getComponentsPatch().isEmpty()) {
            return null;
        }
        try {
            if (stack.save(level.registryAccess()) instanceof net.minecraft.nbt.CompoundTag saved
                    && saved.contains("components", 10)) {
                net.minecraft.nbt.CompoundTag result = new net.minecraft.nbt.CompoundTag();
                result.put("components", saved.getCompound("components"));
                return result.toString();
            }
        } catch (Exception ignored) {
            // An item that cannot be serialized is still usable by id alone.
        }
        return null;
    }

    private void moveItem(int index, int delta) {
        List<DisplayItemInfo> list = this.items();
        int target = index + delta;
        if (index < 0 || target < 0 || target >= list.size()) {
            return;
        }
        list.add(target, list.remove(index));
        this.entry.setDisplayItems(list);
        this.context.touchStructure();
    }

    private void removeItem(int index) {
        List<DisplayItemInfo> list = this.items();
        if (index < 0 || index >= list.size()) {
            return;
        }
        list.remove(index);
        this.entry.setDisplayItems(list.isEmpty() ? null : list);
        this.context.touchStructure();
    }

    private static Button small(String label, Runnable action, boolean active) {
        Button button = Button.of(Component.literal(label), action).active(active);
        button.prefWidth(15);
        return button;
    }

    private final class CommandRow extends Row {
        CommandRow(String command, int index, int count) {
            this.gap(2);
            TextBox field = new TextBox(command == null ? "" : command);
            field.placeholder(Theme.tr("logic.command_hint"));
            field.flex(1);
            field.onChange(value -> {
                if (!LogicTab.this.binding) {
                    LogicTab.this.setCommand(index, value);
                }
            });
            this.add(field);
            this.add(small("▲", () -> LogicTab.this.moveCommand(index, -1), index > 0));
            this.add(small("▼", () -> LogicTab.this.moveCommand(index, 1), index < count - 1));
            this.add(small("✕", () -> LogicTab.this.removeCommand(index), true).tone(Button.Tone.GHOST));
        }
    }

    private final class ItemRow extends Row {
        private final DisplayItemInfo item;

        ItemRow(DisplayItemInfo item, int index, int count) {
            this.item = item;
            this.gap(2);
            this.add(new Icon(this));
            TextBox id = new TextBox(item.getItemId() == null ? "" : item.getItemId());
            id.placeholder(Theme.tr("logic.item_id_hint"));
            id.flex(3);
            id.onChange(value -> this.update(() -> item.setItemId(value.trim())));
            TextBox amount = new TextBox(String.valueOf(item.getCount()));
            amount.prefWidth(26);
            amount.onChange(value -> this.update(() -> {
                try {
                    item.setCount(Math.max(1, Integer.parseInt(value.trim())));
                } catch (NumberFormatException ignored) {
                    // A half-typed number is not an error worth reporting.
                }
            }));
            TextBox nbt = new TextBox(item.getNbt() == null ? "" : item.getNbt());
            nbt.placeholder(Theme.tr("logic.nbt_hint"));
            nbt.flex(2);
            nbt.onChange(value -> this.update(() -> item.setNbt(value.isBlank() ? null : value)));
            this.add(id);
            this.add(amount);
            this.add(nbt);
            this.add(small("✎", () -> ItemEditSheet.open(LogicTab.this.host(), item,
                    LogicTab.this.context::touchStructure), true).withTooltip(Theme.tr("logic.customize_item")));
            this.add(small("▲", () -> LogicTab.this.moveItem(index, -1), index > 0));
            this.add(small("▼", () -> LogicTab.this.moveItem(index, 1), index < count - 1));
            this.add(small("✕", () -> LogicTab.this.removeItem(index), true).tone(Button.Tone.GHOST));
        }

        void update(Runnable change) {
            if (LogicTab.this.binding) {
                return;
            }
            change.run();
            LogicTab.this.context.touch(true);
        }
    }

    /** The row's item id as its in-game icon; clicking it replaces the item. */
    private final class Icon extends UiNode {
        private final ItemRow row;

        Icon(ItemRow row) {
            this.row = row;
            this.prefWidth(20);
        }

        @Override
        public int measureHeight(int width) {
            return Theme.ROW;
        }

        @Override
        public Component tooltip() {
            return Theme.tr("logic.pick_item");
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button != 0) {
                return false;
            }
            LogicTab.this.pickItem(stack -> {
                if (!stack.isEmpty()) {
                    this.row.item.setItemId(
                            net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
                    this.row.item.setNbt(componentsNbt(stack));
                    // Rebuild the rows so the id and NBT fields show the new item.
                    LogicTab.this.context.touchStructure();
                }
            });
            return true;
        }

        @Override
        protected void render(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int size = Theme.ROW - 2;
            Icons.draw(graphics, this.row.item.getItemId(), this.x() + 2, this.y() + 1, size);
            if (this.isHovered()) {
                Theme.border(graphics, this.x() + 2, this.y() + 1, size, size, Theme.ACCENT);
            }
        }
    }
}
