package top.yourzi.dialog.editor.screen;

import com.google.gson.JsonPrimitive;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
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
 * Writing surface: the script as a vertical list of cards in file order.
 *
 * <p>File order is the script, so cards are never rearranged by a layout algorithm. Each card shows
 * who speaks, what they say and where the node goes next; choices are listed inside the card and
 * clicking one jumps to its target, which is how branches are followed without a graph canvas.
 * Right click opens the node's actions.
 */
public final class FlowPanel extends EditorPanel {
    private static final int HEAD = 14;
    private static final int OPTION_ROW = 13;
    private static final int PAD = 6;
    private static final int MAX_TEXT_LINES = 4;

    /** Actions the flow delegates to the screen. */
    public interface Commands {
        void addNode();

        void duplicateSelected();

        void deleteSelected();

        void moveSelected(int delta);

        void renameSelected();
    }

    private final EditorContext context;
    private final Column body = new Column().gap(3).padding(4);
    private final ScrollView scroller = new ScrollView(this.body);
    private final Map<String, Integer> branchColors = new HashMap<>();
    private final Button addButton = Button.of(Theme.tr("add_node"), () -> {
    }).tone(Button.Tone.PRIMARY);
    private final Button duplicateButton = Button.of(Theme.tr("action.duplicate"), () -> {
    });
    private final Button upButton = Button.of(Component.literal("▲"), () -> {
    });
    private final Button downButton = Button.of(Component.literal("▼"), () -> {
    });
    private final Button deleteButton = Button.of(Theme.tr("action.delete"), () -> {
    }).tone(Button.Tone.GHOST);
    private Commands commands;
    private boolean revealPending;

    public FlowPanel(EditorContext context) {
        super("02", Theme.tr("panel.flow"));
        this.context = context;
        Row toolbar = this.createToolbar(Theme.ROW);
        toolbar.add(this.addButton.fit());
        toolbar.add(this.duplicateButton.fit());
        toolbar.add(this.upButton.prefWidth(18));
        toolbar.add(this.downButton.prefWidth(18));
        toolbar.add(Nodes.fill());
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
        this.scroller.setBounds(this.content().x(), this.content().y(), this.content().width(), this.content().height());
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
                    this.branchColors.put(target, goldenColor(counter++));
                }
            }
        }
    }

    private static int goldenColor(int index) {
        float hue = (index * 0.61803398875f) % 1.0f;
        int rgb = java.awt.Color.HSBtoRGB(hue, 0.55f, 0.95f);
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }

    @Override
    protected void renderAfterChildren(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!this.revealPending) {
            return;
        }
        this.revealPending = false;
        for (UiNode child : this.body.children()) {
            if (child instanceof Card card && card.isSelected()) {
                this.scroller.reveal(card.y() - 4, card.bottom() + 4);
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

        private List<String> lines(int width) {
            return Wrap.dialogue(this.entry.getText(), Math.max(20, width - PAD * 2), MAX_TEXT_LINES);
        }

        private int optionCount() {
            return this.entry.getOptions() == null ? 0 : this.entry.getOptions().length;
        }

        private int optionsTop() {
            return this.y() + HEAD + 4 + this.lines(this.width()).size() * 10 + 3;
        }

        @Override
        public int measureHeight(int width) {
            int textLines = Math.max(1, this.lines(width).size());
            return HEAD + 4 + textLines * 10 + 3 + this.optionCount() * OPTION_ROW + 12;
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
            List<ContextMenu.Item> items = new ArrayList<>();
            items.add(ContextMenu.Item.of(Theme.tr("menu.insert_after"), FlowPanel.this.commands::addNode));
            items.add(ContextMenu.Item.of(Theme.tr("menu.rename"), FlowPanel.this.commands::renameSelected));
            items.add(ContextMenu.Item.of(Theme.tr("menu.duplicate"), FlowPanel.this.commands::duplicateSelected));
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
            items.add(ContextMenu.Item.of(Theme.tr("menu.move_up"), () -> FlowPanel.this.commands.moveSelected(-1),
                    this.position > 0));
            items.add(ContextMenu.Item.of(Theme.tr("menu.move_down"), () -> FlowPanel.this.commands.moveSelected(1),
                    this.position < NodeGraph.entries(sequence).size() - 1));
            items.add(ContextMenu.Item.separator());
            items.add(ContextMenu.Item.of(Theme.tr("menu.delete"), FlowPanel.this.commands::deleteSelected));
            ContextMenu.open(FlowPanel.this.host(), menuX, menuY, Component.literal(this.entry.getId()), items);
        }

        @Override
        protected void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            DialogSequence sequence = FlowPanel.this.context.sequence();
            boolean selected = this.isSelected();
            boolean hovered = this.isHovered();
            int bottomOfCard = this.bottom() - 12;
            graphics.fill(this.x(), this.y(), this.right(), bottomOfCard,
                    selected ? Theme.SELECTED : hovered ? Theme.HOVER : Theme.RAISED);
            Theme.border(graphics, this.x(), this.y(), this.width(), bottomOfCard - this.y(),
                    selected ? Theme.ACCENT : Theme.BORDER);
            graphics.fill(this.x(), this.y(), this.x() + 3, bottomOfCard, OutlinePanel.kindColor(this.entry));

            // Header: ordinal, id, start / end markers and the speaker on the right.
            int headY = this.y() + 4;
            String ordinal = String.format("%02d", this.position + 1);
            Theme.text(graphics, ordinal, this.x() + PAD, headY, Theme.TEXT_MUTED);
            int idX = this.x() + PAD + Theme.font().width(ordinal) + 5;
            String id = this.entry.getId() == null ? "?" : this.entry.getId();
            Theme.text(graphics, id, idX, headY, Theme.ACCENT);
            int tagX = idX + Theme.font().width(id) + 6;
            if (NodeGraph.isStart(sequence, this.entry)) {
                Theme.text(graphics, Theme.tr("flow.start").getString(), tagX, headY, Theme.SUCCESS);
                tagX += Theme.font().width(Theme.tr("flow.start").getString()) + 6;
            }
            if (!this.entry.isSkipAllowed()) {
                Theme.text(graphics, Theme.tr("flow.no_skip").getString(), tagX, headY, Theme.TEXT_MUTED);
            }
            String speaker = TextCodec.preview(this.entry.getSpeaker());
            if (!speaker.isEmpty()) {
                String shown = Theme.ellipsize(speaker, this.width() / 3);
                Theme.text(graphics, shown, this.right() - PAD - Theme.font().width(shown), headY, Theme.TEXT_DIM);
            }

            // Dialogue text.
            int textY = this.y() + HEAD + 4;
            List<String> lines = this.lines(this.width());
            if (lines.isEmpty() || (lines.size() == 1 && lines.get(0).isEmpty())) {
                Theme.text(graphics, Theme.tr("flow.no_text").getString(), this.x() + PAD, textY, Theme.TEXT_MUTED);
            }
            for (String line : lines) {
                Theme.text(graphics, line, this.x() + PAD, textY, Theme.TEXT);
                textY += 10;
            }

            // Choices.
            int optionY = this.optionsTop();
            for (int i = 0; i < this.optionCount(); i++) {
                DialogOption option = this.entry.getOptions()[i];
                String target = option == null ? null : option.getTargetId();
                boolean missing = target != null && !target.isBlank() && NodeGraph.byId(sequence, target) == null;
                int color = target == null || target.isBlank() ? Theme.TEXT_MUTED
                        : missing ? Theme.DANGER : FlowPanel.this.branchColors.getOrDefault(target, Theme.CYAN);
                if (hovered && mouseY >= optionY && mouseY < optionY + OPTION_ROW) {
                    graphics.fill(this.x() + 4, optionY, this.right() - 4, optionY + OPTION_ROW, 0x22FFFFFF);
                }
                graphics.fill(this.x() + PAD, optionY + 3, this.x() + PAD + 3, optionY + OPTION_ROW - 3, color);
                String targetLabel = target == null || target.isBlank() ? Theme.tr("flow.ends").getString()
                        : (missing ? "! " : "→ ") + target;
                int targetWidth = Math.min(this.width() / 3, Theme.font().width(targetLabel));
                String label = (i + 1) + ". " + (option == null ? "" : TextCodec.preview(option.getText()));
                Theme.textIn(graphics, label, this.x() + PAD + 7, optionY, this.width() - PAD * 2 - targetWidth - 12,
                        OPTION_ROW, Theme.TEXT_DIM);
                Theme.textIn(graphics, targetLabel, this.right() - PAD - targetWidth, optionY, targetWidth, OPTION_ROW,
                        color);
                optionY += OPTION_ROW;
            }

            // Connector to whatever runs next, drawn in the gap below the card.
            this.renderExit(graphics, sequence, bottomOfCard);
        }

        private void renderExit(GuiGraphics graphics, DialogSequence sequence, int top) {
            int x = this.x() + 14;
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
                    label = "! " + this.entry.getNextId();
                    color = Theme.DANGER;
                } else if (next == null) {
                    label = Theme.tr("flow.exit_end").getString();
                    color = Theme.TEXT_MUTED;
                } else if (explicit) {
                    label = "↳ " + next.getId();
                    color = Theme.CYAN;
                } else {
                    graphics.fill(x, top, x + 1, top + 12, Theme.BORDER_STRONG);
                    return;
                }
            }
            graphics.fill(x, top, x + 1, top + 5, color);
            Theme.text(graphics, label, x + 5, top + 2, color);
        }
    }
}
