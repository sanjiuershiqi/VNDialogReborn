package top.yourzi.dialog.editor.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import top.yourzi.dialog.editor.ui.Column;
import top.yourzi.dialog.editor.ui.Modal;
import top.yourzi.dialog.editor.ui.Nodes;
import top.yourzi.dialog.editor.ui.Theme;
import top.yourzi.dialog.editor.ui.UiHost;
import top.yourzi.dialog.editor.ui.UiNode;

/** Keyboard reference, opened with F1 or the help button. */
final class HelpSheet extends Modal {
    private static final String[][] GROUPS = {
            {"help.group_file", "Ctrl+N", "help.new", "Ctrl+O", "help.open", "Ctrl+S", "help.save",
                    "Ctrl+Tab", "help.next_document", "Ctrl+Enter", "help.playtest"},
            {"help.group_edit", "Ctrl+Z", "help.undo", "Ctrl+Y / Ctrl+Shift+Z", "help.redo",
                    "Insert", "help.add_node", "Ctrl+D", "help.duplicate", "Delete", "help.delete", "F2", "help.rename",
                    "Ctrl+C / Ctrl+V", "help.copy_paste"},
            {"help.group_navigate", "↑ / ↓", "help.select", "Alt+↑ / Alt+↓", "help.move", "F1", "help.toggle",
                    "Esc", "help.escape"},
            {"help.group_stage", "help.stage_drag_key", "help.stage_drag", "help.stage_wheel_key", "help.stage_wheel",
                    "← ↑ → ↓", "help.stage_nudge", "R", "help.stage_reset"},
            {"help.group_graph", "help.graph_drag_key", "help.graph_drag", "help.graph_link_key", "help.graph_link",
                    "help.graph_new_key", "help.graph_new"}
    };

    private HelpSheet() {
        super(Theme.tr("help.title"));
        this.setCardWidth(380);
    }

    static void open(UiHost host) {
        HelpSheet sheet = new HelpSheet();
        sheet.build();
        host.open(sheet, true, false);
    }

    @Override
    protected void buildBody(Column body) {
        for (String[] group : GROUPS) {
            body.add(Nodes.section(Theme.tr(group[0])));
            for (int i = 1; i + 1 < group.length; i += 2) {
                String key = group[i];
                Component keyText = key.startsWith("help.") ? Theme.tr(key) : Component.literal(key);
                body.add(new Shortcut(keyText, Theme.tr(group[i + 1])));
            }
        }
    }

    private static final class Shortcut extends UiNode {
        private final Component key;
        private final Component action;

        Shortcut(Component key, Component action) {
            this.key = key;
            this.action = action;
            this.prefHeight(11);
        }

        @Override
        protected void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int keyWidth = Math.min(130, this.width() / 2);
            Theme.textIn(graphics, this.key.getString(), this.x() + 4, this.y(), keyWidth - 6, this.height(), Theme.ACCENT);
            Theme.textIn(graphics, this.action.getString(), this.x() + keyWidth, this.y(), this.width() - keyWidth,
                    this.height(), Theme.TEXT_DIM);
        }
    }
}
