package top.yourzi.dialog.editor.screen;

import com.google.gson.JsonPrimitive;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import top.yourzi.dialog.editor.TextCodec;
import top.yourzi.dialog.editor.ui.Button;
import top.yourzi.dialog.editor.ui.Column;
import top.yourzi.dialog.editor.ui.ContextMenu;
import top.yourzi.dialog.editor.ui.Nodes;
import top.yourzi.dialog.editor.ui.Paragraph;
import top.yourzi.dialog.editor.ui.Row;
import top.yourzi.dialog.editor.ui.ScrollView;
import top.yourzi.dialog.editor.ui.Theme;
import top.yourzi.dialog.editor.ui.UiNode;
import top.yourzi.dialog.editor.ui.Wrap;
import top.yourzi.dialog.model.DialogEntry;
import top.yourzi.dialog.model.DialogOption;
import top.yourzi.dialog.model.DialogSequence;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Writing surface: the script as a column of cards in file order.
 *
 * <p>A card reads like a screenplay line - speaker, then text with its formatting applied - followed
 * by the choices and a one-line note of where the script goes next. Clicking a choice on the
 * selected card jumps to its target; right click opens the node's actions.
 */
public final class FlowPanel extends EditorPanel {
    private static final int PAD = 8;
    private static final int LINE_H = 10;
    private static final int OPTION_ROW = 14;
    private static final int EXIT_H = 14;
    private static final int MAX_TEXT_LINES = 5;
    private static final int MAX_CARD_WIDTH = 560;

    /** Actions the flow delegates to the screen. */
    public interface Commands {
        void addNode();

        void duplicateSelected();

        void deleteSelected();

        void moveSelected(int delta);

        void renameSelected();
    }

    private final EditorContext context;
    private final Column body = new Column().gap(4).padding(8);
    private final ScrollView scroller = new ScrollView(this.body);
    private final Map<String, Integer> branchColors = new HashMap<>();
    private final Button addButton = Button.of(Theme.tr("add_node"), () -> {
    }).tone(Button.Tone.PRIMARY);
    private final Button duplicateButton = Button.of(Theme.tr("action.duplicate"), () -> {
    }).tone(Button.Tone.GHOST);
    private final Button upButton = Button.of(Theme.tr("flow.up"), () -> {
    }).tone(Button.Tone.GHOST);
    private final Button downButton = Button.of(Theme.tr("flow.down"), () -> {
    }).tone(Button.Tone.GHOST);
    private final Button deleteButton = Button.of(Theme.tr("action.delete"), () -> {
    }).tone(Button.Tone.DANGER);
    private Commands commands;
    private boolean revealPending;

    public FlowPanel(EditorContext context) {
        super(null);
        this.context = context;
        Row toolbar = this.createToolbar(Theme.ROW);
        toolbar.add(this.addButton.fit());
        toolbar.add(Nodes.fill());
        toolbar.add(this.upButton.fit());
        toolbar.add(this.downButton.fit());
        toolbar.add(this.duplicateButton.fit());
        toolbar.add(this.deleteButton.fit());
        this.upButton.withTooltip(Theme.tr("flow.move_up"));
        this.downButton.withTooltip(Theme.tr("flow.move_down"));
        this.add(this.scroller);
    }

    public void setCommands(Commands commands) {
        this.commands = commands;
        this.addButton.setAction(commands::addNode);
        this.duplicateButton.setAction(commands::duplicateSelected);
        this.upButton.setAction(() -> commands.moveSelected(-1));
        this.downButton.setAction(() -> commands.moveSelected(1));
        this.deleteButton.setAction(commands::deleteSelected);
    }

    @Override
    protected void onLayout() {
        super.onLayout();
        // Cards stop growing past a readable measure and stay centred on wide screens.
        int width = Math.min(this.content().width(), MAX_CARD_WIDTH + 24);
        int x = this.content().x() + (this.content().width() - width) / 2;
        this.scroller.setBounds(x, this.content().y(), width, this.content().height());
    }

    /**
     * Rebuilds the cards from the document.
     *
     * @param reveal scroll the selected card into view once it has been laid out
     */
    public void refresh(boolean reveal) {
        DialogSequence sequence = this.context.sequence();
        this.body.clear();
        this.assignBranchColors(sequence);
        this.revealPending |= reveal;
        if (sequence == null) {
            this.body.add(Paragraph.of(Theme.tr("flow.no_sequence")).color(Theme.TEXT_MUTED));
        } else {
            List<DialogEntry> entries = NodeGraph.entries(sequence);
            if (entries.isEmpty()) {
                this.body.add(Paragraph.of(Theme.tr("flow.empty")).color(Theme.TEXT_MUTED));
            }
            for (int i = 0; i < entries.size(); i++) {
                this.body.add(new Card(entries.get(i), i));
            }
        }
        DialogEntry selected = NodeGraph.byId(sequence, this.context.selectedId());
        int index = selected == null ? -1 : NodeGraph.indexOf(sequence, selected);
        int count = NodeGraph.entries(sequence).size();
        this.addButton.active(sequence != null);
        this.duplicateButton.active(selected != null);
        this.deleteButton.active(selected != null);
        this.upButton.active(index > 0);
        this.downButton.active(index >= 0 && index < count - 1);
    }

    /** Stable colour per branch target so a target keeps one colour across the whole script. */
    private void assignBranchColors(DialogSequence sequence) {
        this.branchColors.clear();
        int counter = 0;
        for (DialogEntry entry : NodeGraph.entries(sequence)) {
            if (entry.getOptions() == null) {
                continue;
            }
            for (DialogOption option : entry.getOptions()) {
                String target = option == null ? null : option.getTargetId();
                if (target != null && !target.isBlank() && !this.branchColors.containsKey(target)) {
                    float hue = (0.58f + counter++ * 0.61803398875f) % 1.0f;
                    this.branchColors.put(target, 0xFF000000 | (java.awt.Color.HSBtoRGB(hue, 0.45f, 0.95f) & 0xFFFFFF));
                }
            }
        }
    }

    @Override
    protected void renderAfterChildren(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!this.revealPending) {
            return;
        }
        this.revealPending = false;
        for (UiNode child : this.body.children()) {
            if (child instanceof Card card && card.isSelected()) {
                this.scroller.reveal(card.y() - 6, card.bottom() + 6);
                return;
            }
        }
    }

    /** One node of the script. */
    private final class Card extends UiNode {
        private final DialogEntry entry;
        private final int position;

        Card(DialogEntry entry, int position) {
            this.entry = entry;
            this.position = position;
        }

        boolean isSelected() {
            return this.entry.getId() != null && this.entry.getId().equals(FlowPanel.this.context.selectedId());
        }

        private List<FormattedCharSequence> lines(int width) {
            return Wrap.lines(TextCodec.styled(this.entry.getText()), width - PAD * 2, MAX_TEXT_LINES);
        }

        private int optionCount() {
            return this.entry.getOptions() == null ? 0 : this.entry.getOptions().length;
        }

        private int textTop() {
            return this.y() + PAD + LINE_H + 3;
        }

        private int optionsTop() {
            return this.textTop() + this.lines(this.width()).size() * LINE_H + 4;
        }

        @Override
        public int measureHeight(int width) {
            int options = this.optionCount() == 0 ? 0 : this.optionCount() * OPTION_ROW + 2;
            return PAD + LINE_H + 3 + this.lines(width).size() * LINE_H + 4 + options + PAD - 4 + EXIT_H;
        }

        private int cardBottom() {
            return this.bottom() - EXIT_H;
        }

        private int optionAt(double mouseY) {
            if (this.optionCount() == 0 || mouseY < this.optionsTop()) {
                return -1;
            }
            int index = (int) ((mouseY - this.optionsTop()) / OPTION_ROW);
            return index < this.optionCount() ? index : -1;
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            FlowPanel.this.host().focus(null);
            int option = this.optionAt(mouseY);
            if (button == 0) {
                if (option >= 0 && this.isSelected()) {
                    DialogOption choice = this.entry.getOptions()[option];
                    DialogEntry target = choice == null ? null
                            : NodeGraph.byId(FlowPanel.this.context.sequence(), choice.getTargetId());
                    if (target != null) {
                        FlowPanel.this.revealPending = true;
                        FlowPanel.this.context.select(target.getId());
                        return true;
                    }
                }
                FlowPanel.this.context.select(this.entry.getId());
                return true;
            }
            if (button == 1) {
                FlowPanel.this.context.select(this.entry.getId());
                this.openMenu((int) mouseX, (int) mouseY);
                return true;
            }
            return false;
        }

        private void openMenu(int menuX, int menuY) {
            EditorContext context = FlowPanel.this.context;
            DialogSequence sequence = context.sequence();
            Commands commands = FlowPanel.this.commands;
            List<ContextMenu.Item> items = new ArrayList<>();
            items.add(ContextMenu.Item.of(Theme.tr("menu.insert_after"), commands::addNode));
            items.add(ContextMenu.Item.of(Theme.tr("menu.rename"), commands::renameSelected));
            items.add(ContextMenu.Item.of(Theme.tr("menu.duplicate"), commands::duplicateSelected));
            items.add(ContextMenu.Item.of(Theme.tr("menu.set_start"), () -> {
                sequence.setStartId(this.entry.getId());
                context.touchStructure();
            }, !NodeGraph.isStart(sequence, this.entry)));
            items.add(ContextMenu.Item.of(Theme.tr("menu.add_branch"), () -> {
                List<DialogOption> options = new ArrayList<>();
                if (this.entry.getOptions() != null) {
                    options.addAll(List.of(this.entry.getOptions()));
                }
                options.add(DialogOption.builder().text(new JsonPrimitive(Theme.tr("branch.new").getString())).build());
                this.entry.setOptions(options.toArray(new DialogOption[0]));
                this.entry.setNextId(null);
                context.touchStructure();
            }));
            items.add(ContextMenu.Item.of(Theme.tr("menu.toggle_end"), () -> {
                this.entry.setEndDialog(this.entry.isEndDialog() ? null : Boolean.TRUE);
                context.touchStructure();
            }));
            items.add(ContextMenu.Item.separator());
            items.add(ContextMenu.Item.of(Theme.tr("menu.move_up"), () -> commands.moveSelected(-1), this.position > 0));
            items.add(ContextMenu.Item.of(Theme.tr("menu.move_down"), () -> commands.moveSelected(1),
                    this.position < NodeGraph.entries(sequence).size() - 1));
            items.add(ContextMenu.Item.separator());
            items.add(ContextMenu.Item.of(Theme.tr("menu.delete"), commands::deleteSelected));
            ContextMenu.open(FlowPanel.this.host(), menuX, menuY, Component.literal(this.entry.getId()), items);
        }

        @Override
        protected void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            DialogSequence sequence = FlowPanel.this.context.sequence();
            boolean selected = this.isSelected();
            boolean hovered = this.isHovered();
            int bottom = this.cardBottom();
            graphics.fill(this.x(), this.y(), this.right(), bottom, selected ? Theme.SELECTED : hovered ? Theme.HOVER : Theme.RAISED);
            graphics.fill(this.x(), this.y(), this.x() + 2, bottom, OutlinePanel.kindColor(this.entry));
            if (selected) {
                graphics.fill(this.x(), bottom - 1, this.right(), bottom, Theme.ACCENT);
            }

            // Lead line: speaker on the left, ordinal and id on the right.
            int headY = this.y() + PAD;
            String meta = String.format("%02d  %s", this.position + 1, this.entry.getId() == null ? "?" : this.entry.getId());
            if (NodeGraph.isStart(sequence, this.entry)) {
                meta = Theme.tr("flow.start").getString() + "  " + meta;
            }
            int metaWidth = Math.min(Theme.font().width(meta), this.width() / 2);
            Theme.textIn(graphics, meta, this.right() - PAD - metaWidth, headY - 1, metaWidth, LINE_H,
                    NodeGraph.isStart(sequence, this.entry) ? Theme.SUCCESS : Theme.TEXT_MUTED);
            String speaker = TextCodec.preview(this.entry.getSpeaker());
            int speakerWidth = this.width() - PAD * 3 - metaWidth;
            if (speaker.isEmpty()) {
                Theme.textIn(graphics, Theme.tr("flow.narration").getString(), this.x() + PAD, headY - 1, speakerWidth,
                        LINE_H, Theme.TEXT_MUTED);
            } else {
                Theme.textIn(graphics, speaker, this.x() + PAD, headY - 1, speakerWidth, LINE_H, Theme.ACCENT);
            }

            // Text, with the writer's formatting applied.
            int textY = this.textTop();
            List<FormattedCharSequence> lines = this.lines(this.width());
            if (TextCodec.preview(this.entry.getText()).isEmpty()) {
                Theme.text(graphics, Theme.tr("flow.no_text").getString(), this.x() + PAD, textY, Theme.TEXT_MUTED);
            } else {
                for (FormattedCharSequence line : lines) {
                    graphics.drawString(Theme.font(), line, this.x() + PAD, textY, Theme.TEXT, false);
                    textY += LINE_H;
                }
            }

            // Choices.
            int optionY = this.optionsTop();
            for (int i = 0; i < this.optionCount(); i++) {
                DialogOption option = this.entry.getOptions()[i];
                String target = option == null ? null : option.getTargetId();
                boolean missing = target != null && !target.isBlank() && NodeGraph.byId(sequence, target) == null;
                int color = target == null || target.isBlank() ? Theme.TEXT_MUTED
                        : missing ? Theme.DANGER : FlowPanel.this.branchColors.getOrDefault(target, Theme.CYAN);
                if (hovered && selected && mouseY >= optionY && mouseY < optionY + OPTION_ROW) {
                    graphics.fill(this.x() + PAD - 3, optionY, this.right() - PAD + 3, optionY + OPTION_ROW, 0x18FFFFFF);
                }
                String targetLabel = target == null || target.isBlank() ? Theme.tr("flow.ends").getString()
                        : (missing ? "! " : "→ ") + target;
                int targetWidth = Math.min(this.width() / 3, Theme.font().width(targetLabel));
                Theme.textIn(graphics, "› " + (option == null ? "" : TextCodec.preview(option.getText())),
                        this.x() + PAD, optionY, this.width() - PAD * 3 - targetWidth, OPTION_ROW, Theme.TEXT_DIM);
                Theme.textIn(graphics, targetLabel, this.right() - PAD - targetWidth, optionY, targetWidth, OPTION_ROW, color);
                optionY += OPTION_ROW;
            }

            this.renderExit(graphics, sequence, bottom);
        }

        /** One quiet line under the card saying where the script continues. */
        private void renderExit(GuiGraphics graphics, DialogSequence sequence, int top) {
            int x = this.x() + 12;
            String label;
            int color;
            if (this.entry.isEndDialog()) {
                label = Theme.tr("flow.exit_end").getString();
                color = Theme.DANGER;
            } else if (this.optionCount() > 0) {
                return;
            } else {
                DialogEntry next = NodeGraph.implicitNext(sequence, this.entry);
                boolean explicit = this.entry.getNextId() != null && !this.entry.getNextId().isBlank();
                if (explicit && next == null) {
                    label = Theme.tr("flow.exit_missing", this.entry.getNextId()).getString();
                    color = Theme.DANGER;
                } else if (next == null) {
                    label = Theme.tr("flow.exit_end").getString();
                    color = Theme.TEXT_MUTED;
                } else if (explicit) {
                    label = Theme.tr("flow.exit_jump", next.getId()).getString();
                    color = Theme.CYAN;
                } else {
                    graphics.fill(x, top, x + 1, top + EXIT_H, Theme.BORDER_STRONG);
                    return;
                }
            }
            graphics.fill(x, top, x + 1, top + 5, color);
            Theme.text(graphics, label, x + 6, top + 3, color);
        }
    }
}
