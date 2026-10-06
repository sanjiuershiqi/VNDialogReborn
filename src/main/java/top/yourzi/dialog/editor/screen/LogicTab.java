package top.yourzi.dialog.editor.screen;

import net.minecraft.network.chat.Component;
import top.yourzi.dialog.editor.ui.Button;
import top.yourzi.dialog.editor.ui.Column;
import top.yourzi.dialog.editor.ui.Nodes;
import top.yourzi.dialog.editor.ui.Paragraph;
import top.yourzi.dialog.editor.ui.Row;
import top.yourzi.dialog.editor.ui.TextBox;
import top.yourzi.dialog.editor.ui.Theme;
import top.yourzi.dialog.model.DialogEntry;
import top.yourzi.dialog.model.DisplayItemInfo;

import java.util.ArrayList;
import java.util.List;

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
    private Runnable inventoryPicker;
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
        itemButtons.add(Button.of(Theme.tr("logic.pick_item"), () -> {
            if (this.inventoryPicker != null) {
                this.inventoryPicker.run();
            }
        }).fit());
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

    void setInventoryPicker(Runnable inventoryPicker) {
        this.inventoryPicker = inventoryPicker;
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

    /** Also used by the screen after the inventory picker returns. */
    void addItem(DisplayItemInfo info) {
        if (this.entry == null || info == null) {
            return;
        }
        List<DisplayItemInfo> list = this.items();
        list.add(info);
        this.entry.setDisplayItems(list);
        this.context.touchStructure();
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
        ItemRow(DisplayItemInfo item, int index, int count) {
            this.gap(2);
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
            this.add(small("▲", () -> LogicTab.this.moveItem(index, -1), index > 0));
            this.add(small("▼", () -> LogicTab.this.moveItem(index, 1), index < count - 1));
            this.add(small("✕", () -> LogicTab.this.removeItem(index), true).tone(Button.Tone.GHOST));
        }

        private void update(Runnable change) {
            if (LogicTab.this.binding) {
                return;
            }
            change.run();
            LogicTab.this.context.touch(true);
        }
    }
}
