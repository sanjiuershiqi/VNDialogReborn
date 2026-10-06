package top.yourzi.dialog.editor.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import top.yourzi.dialog.editor.TextCodec;
import top.yourzi.dialog.editor.ui.Button;
import top.yourzi.dialog.editor.ui.ItemList;
import top.yourzi.dialog.editor.ui.Nodes;
import top.yourzi.dialog.editor.ui.Row;
import top.yourzi.dialog.editor.ui.TextBox;
import top.yourzi.dialog.editor.ui.Theme;
import top.yourzi.dialog.model.DialogEntry;
import top.yourzi.dialog.model.DialogSequence;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Structure navigator: every node in file order, filtered by a full-text search.
 *
 * <p>The list follows the authored order rather than a computed tree, because that order is what
 * the runtime falls through when a node has no explicit {@code next}. Each row shows the node's own
 * facts instead - kind stripe, start / unreachable marker, branch count and how often it is
 * referenced - so problems are visible without opening anything.
 */
public final class OutlinePanel extends EditorPanel {
    private final EditorContext context;
    private final ItemList<DialogEntry> list = new ItemList<>();
    private final TextBox search = new TextBox("");
    private final Button add = Button.of(Theme.tr("add_node"), () -> {
    }).tone(Button.Tone.PRIMARY);
    private final Set<String> unreachable = new HashSet<>();
    private final Map<String, Integer> references = new HashMap<>();

    public OutlinePanel(EditorContext context) {
        super("01", Theme.tr("panel.structure"));
        this.context = context;
        Row toolbar = this.createToolbar(Theme.ROW);
        toolbar.add(this.add.fit());
        toolbar.add(Nodes.fill());
        this.add(this.search);
        this.add(this.list);
        this.search.placeholder(Theme.tr("search_hint"));
        this.search.onChange(text -> this.refresh());
        this.list.labeler(entry -> Component.empty());
        this.list.decorator(this::decorateRow);
        this.list.emptyText(Theme.tr("outline.empty"));
        this.list.onSelect(entry -> this.context.select(entry.getId()));
    }

    public void setAddAction(Runnable action) {
        this.add.setAction(action);
    }

    @Override
    protected void onLayout() {
        super.onLayout();
        int x = this.content().x() + 3;
        int width = this.content().width() - 6;
        this.search.setBounds(x, this.content().y(), width, Theme.ROW);
        int listY = this.content().y() + Theme.ROW + 3;
        this.list.setBounds(x, listY, width, Math.max(0, this.content().bottom() - listY - 2));
    }

    public void refresh() {
        DialogSequence sequence = this.context.sequence();
        this.references.clear();
        this.references.putAll(NodeGraph.referenceCounts(sequence));
        this.unreachable.clear();
        this.unreachable.addAll(NodeGraph.unreachable(sequence));
        this.list.setItems(NodeGraph.search(sequence, this.search.value()));
        this.list.selectIndex(-1);
        DialogEntry selected = NodeGraph.byId(sequence, this.context.selectedId());
        if (selected != null) {
            this.list.selectQuietly(selected);
        }
    }

    public void revealSelected() {
        this.list.scrollToSelected();
    }

    private void decorateRow(GuiGraphics graphics, DialogEntry entry, int rowY, int x, int width, int height,
                             boolean selected) {
        String id = entry.getId() == null ? "?" : entry.getId();
        graphics.fill(x, rowY + 1, x + 2, rowY + height - 1, kindColor(entry));
        int textY = rowY + (height - 8) / 2;
        int cursor = x + 5;
        if (NodeGraph.isStart(this.context.sequence(), entry)) {
            Theme.text(graphics, "▶", cursor, textY, Theme.SUCCESS);
        } else if (this.unreachable.contains(id)) {
            Theme.text(graphics, "!", cursor + 2, textY, Theme.WARNING);
        }
        cursor += 9;

        StringBuilder badge = new StringBuilder();
        int options = entry.getOptions() == null ? 0 : entry.getOptions().length;
        if (options > 0) {
            badge.append('⑂').append(options);
        }
        int refs = this.references.getOrDefault(id, 0);
        if (refs > 1) {
            badge.append(" ×").append(refs);
        }
        String badgeText = badge.toString().trim();
        int badgeWidth = badgeText.isEmpty() ? 0 : Theme.font().width(badgeText) + 6;
        int available = width - (cursor - x) - badgeWidth - 4;

        String idText = Theme.ellipsize(id, Math.max(20, available / 2));
        Theme.text(graphics, idText, cursor, textY, selected ? Theme.TEXT : Theme.TEXT_DIM);
        int previewX = cursor + Theme.font().width(idText) + 6;
        String preview = TextCodec.preview(entry.getText()).replace('\u00a7', ' ');
        if (preview.isEmpty()) {
            preview = Theme.tr("outline.no_text").getString();
        }
        Theme.textIn(graphics, preview, previewX, rowY, x + width - badgeWidth - 4 - previewX, height, Theme.TEXT_MUTED);
        if (!badgeText.isEmpty()) {
            Theme.text(graphics, badgeText, x + width - badgeWidth, textY, options > 0 ? Theme.CYAN : Theme.TEXT_MUTED);
        }
    }

    static int kindColor(DialogEntry entry) {
        if (entry.isEndDialog()) {
            return Theme.DANGER;
        }
        if (NodeGraph.hasOptions(entry)) {
            return Theme.WARNING;
        }
        return Theme.CYAN;
    }
}
