package top.yourzi.dialog.editor.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import top.yourzi.dialog.editor.TextCodec;
import top.yourzi.dialog.editor.ui.ItemList;
import top.yourzi.dialog.editor.ui.TextBox;
import top.yourzi.dialog.editor.ui.Theme;
import top.yourzi.dialog.model.DialogEntry;
import top.yourzi.dialog.model.DialogSequence;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Node list in file order with full-text search.
 *
 * <p>File order is what the runtime falls through when a node has no explicit {@code next}, so the
 * list keeps it. Each row is one line: a kind dot, the id, the first words of the text, and a small
 * marker only when something deserves attention (start, unreachable, choices, shared target).
 */
public final class OutlinePanel extends EditorPanel {
    private static final int ROW_PAD = 6;

    private final EditorContext context;
    private final ItemList<DialogEntry> list = new ItemList<>();
    private final TextBox search = new TextBox("");
    private final Set<String> unreachable = new HashSet<>();
    private final Map<String, Integer> references = new HashMap<>();

    public OutlinePanel(EditorContext context) {
        super(Theme.tr("panel.structure"));
        this.context = context;
        this.add(this.search);
        this.add(this.list);
        this.search.placeholder(Theme.tr("search_hint"));
        this.search.onChange(text -> this.refresh());
        this.list.labeler(entry -> Component.empty());
        this.list.decorator(this::decorateRow);
        this.list.emptyText(Theme.tr("outline.empty"));
        this.list.onSelect(entry -> this.context.select(entry.getId()));
    }

    @Override
    protected String headerNote() {
        return String.valueOf(NodeGraph.entries(this.context.sequence()).size());
    }

    @Override
    protected void onLayout() {
        super.onLayout();
        int x = this.content().x() + 6;
        int width = this.content().width() - 12;
        this.search.setBounds(x, this.content().y(), width, Theme.ROW);
        int listY = this.content().y() + Theme.ROW + 6;
        this.list.setBounds(this.content().x(), listY, this.content().width(),
                Math.max(0, this.content().bottom() - listY));
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
        int textY = rowY + (height - 8) / 2;
        int dotY = rowY + height / 2 - 2;
        graphics.fill(x + ROW_PAD, dotY, x + ROW_PAD + 4, dotY + 4, kindColor(entry));

        String marker = "";
        int markerColor = Theme.TEXT_MUTED;
        int options = entry.getOptions() == null ? 0 : entry.getOptions().length;
        if (NodeGraph.isStart(this.context.sequence(), entry)) {
            marker = Theme.tr("outline.start").getString();
            markerColor = Theme.SUCCESS;
        } else if (this.unreachable.contains(id)) {
            marker = Theme.tr("outline.unreachable").getString();
            markerColor = Theme.WARNING;
        } else if (options > 0) {
            marker = Theme.tr("outline.choices", options).getString();
        } else if (this.references.getOrDefault(id, 0) > 1) {
            marker = "×" + this.references.get(id);
        }
        int markerWidth = marker.isEmpty() ? 0 : Theme.font().width(marker) + ROW_PAD;

        int textX = x + ROW_PAD + 10;
        int available = width - (textX - x) - markerWidth - ROW_PAD;
        String shownId = Theme.ellipsize(id, Math.max(24, available * 2 / 5));
        Theme.text(graphics, shownId, textX, textY, selected ? Theme.TEXT : Theme.TEXT_DIM);
        int previewX = textX + Theme.font().width(shownId) + 6;
        String preview = TextCodec.preview(entry.getText());
        Theme.textIn(graphics, preview.isEmpty() ? Theme.tr("outline.no_text").getString() : preview, previewX, rowY,
                x + width - markerWidth - ROW_PAD - previewX, height, Theme.TEXT_MUTED);
        if (!marker.isEmpty()) {
            Theme.text(graphics, marker, x + width - markerWidth, textY, markerColor);
        }
    }

    static int kindColor(DialogEntry entry) {
        if (entry.isEndDialog()) {
            return Theme.DANGER;
        }
        if (NodeGraph.hasOptions(entry)) {
            return Theme.WARNING;
        }
        return Theme.ACCENT;
    }
}
