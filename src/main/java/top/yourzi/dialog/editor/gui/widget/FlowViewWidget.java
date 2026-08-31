package top.yourzi.dialog.editor.gui.widget;

import com.google.gson.JsonElement;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import top.yourzi.dialog.editor.gui.EditorScreenState;
import top.yourzi.dialog.editor.util.ConfigLanguageCache;
import top.yourzi.dialog.editor.util.EditorTheme;
import top.yourzi.dialog.model.DialogEntry;
import top.yourzi.dialog.model.DialogOption;
import top.yourzi.dialog.model.DialogSequence;
import top.yourzi.dialog.util.ComponentJson;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Full flow view. It keeps the runtime array order visible while presenting
 * options as an inline branch list; the left tree remains the navigation view.
 */
public class FlowViewWidget extends AbstractWidget {
    private static final int HEADER_HEIGHT = 26;
    private static final int ENTRY_HEIGHT = 58;
    private static final int OPTION_HEIGHT = 18;
    private static final int SCROLLBAR_WIDTH = EditorTheme.SCROLLBAR_W;

    private final Font font;
    private DialogSequence sequence;
    private String searchText = "";
    private int scrollOffset;
    private String selectedId;
    private Consumer<DialogEntry> onSelect;
    private Consumer<DialogEntry> onDelete;
    private Consumer<DialogEntry> onAddChild;

    public FlowViewWidget(int x, int y, int width, int height, Font font) {
        super(x, y, width, height, Component.translatable("gui.vn_edit.flow"));
        this.font = font;
    }

    public void setCallbacks(Consumer<DialogEntry> onSelect, Consumer<DialogEntry> onDelete,
                             Consumer<DialogEntry> onAddChild) {
        this.onSelect = onSelect;
        this.onDelete = onDelete;
        this.onAddChild = onAddChild;
    }

    public void setSequence(DialogSequence sequence) {
        this.sequence = sequence;
        this.selectedId = EditorScreenState.get().getSelectedNodeId();
        this.scrollOffset = 0;
        this.clampScroll();
    }

    public void setSearchText(String text) {
        this.searchText = text == null ? "" : text;
        this.scrollOffset = 0;
        this.clampScroll();
    }

    public DialogEntry getSelectedEntry() {
        return this.sequence == null ? null : this.sequence.findEntryById(this.selectedId);
    }

    public void selectEntryById(String id) {
        if (id == null) {
            this.selectedId = null;
            this.scrollOffset = 0;
            return;
        }
        if (this.sequence != null && this.sequence.findEntryById(id) != null) {
            this.selectedId = id;
            this.scrollOffset = 0;
            this.clampScroll();
        }
    }

    private List<DialogEntry> visibleEntries() {
        List<DialogEntry> result = new ArrayList<>();
        if (this.sequence == null || this.sequence.getEntries() == null) {
            return result;
        }
        String q = this.searchText == null ? "" : this.searchText.trim().toLowerCase(Locale.ROOT);
        for (DialogEntry entry : this.sequence.getEntries()) {
            if (entry != null && (q.isEmpty() || searchableText(entry).contains(q))) {
                result.add(entry);
            }
        }
        return result;
    }

    private String searchableText(DialogEntry entry) {
        return (entry.getId() + " " + plain(entry.getSpeaker()) + " " + plain(entry.getText())).toLowerCase(Locale.ROOT);
    }

    /** 将选项颜色传播到其目标节点，形成从分支到对话节点的视觉追踪线索。 */
    private Map<String, Integer> incomingOptionColors() {
        Map<String, Integer> colors = new HashMap<>();
        if (this.sequence == null || this.sequence.getEntries() == null) {
            return colors;
        }
        for (DialogEntry source : this.sequence.getEntries()) {
            if (source == null || source.getOptions() == null) {
                continue;
            }
            for (int i = 0; i < source.getOptions().length; i++) {
                DialogOption option = source.getOptions()[i];
                if (option == null || option.getTargetId() == null || option.getTargetId().isBlank()) {
                    continue;
                }
                colors.putIfAbsent(option.getTargetId(), EditorTheme.OPTION_PALETTE[i % EditorTheme.OPTION_PALETTE.length]);
            }
        }
        return colors;
    }

    private String plain(JsonElement value) {
        if (value == null || value.isJsonNull()) {
            return "";
        }
        if (value.isJsonPrimitive()) {
            return value.getAsString();
        }
        if (value.isJsonObject() && value.getAsJsonObject().has("translate")) {
            String key = value.getAsJsonObject().get("translate").getAsString();
            String translated = ConfigLanguageCache.get(key);
            return translated != null ? translated : Component.translatable(key).getString();
        }
        if (value.isJsonArray()) {
            StringBuilder result = new StringBuilder();
            for (JsonElement part : value.getAsJsonArray()) {
                result.append(plain(part));
            }
            return result.toString();
        }
        if (value.isJsonObject()) {
            return ComponentJson.fromJson(value).getString();
        }
        return "";
    }

    private int rowHeight(DialogEntry entry) {
        int options = entry.getOptions() == null ? 0 : entry.getOptions().length;
        return ENTRY_HEIGHT + options * OPTION_HEIGHT;
    }

    private int contentHeight(List<DialogEntry> entries) {
        int height = HEADER_HEIGHT;
        for (DialogEntry entry : entries) {
            height += rowHeight(entry) + 2;
        }
        return height;
    }

    private int maxScroll() {
        return Math.max(0, contentHeight(visibleEntries()) - this.getHeight());
    }

    private void clampScroll() {
        this.scrollOffset = Mth.clamp(this.scrollOffset, 0, this.maxScroll());
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(this.getX(), this.getY(), this.getX() + this.getWidth(), this.getY() + this.getHeight(), EditorTheme.BG_SURFACE);
        g.fill(this.getX(), this.getY(), this.getX() + this.getWidth(), this.getY() + HEADER_HEIGHT, EditorTheme.BG_ELEVATED);
        g.drawString(this.font, Component.translatable("gui.vn_edit.flow.title"), this.getX() + 8, this.getY() + 5, EditorTheme.TEXT_WARM, true);
        List<DialogEntry> entries = visibleEntries();
        this.clampScroll();
        g.enableScissor(this.getX(), this.getY() + HEADER_HEIGHT,
                this.getX() + this.getWidth(), this.getY() + this.getHeight());
        try {
            int y = this.getY() + HEADER_HEIGHT - this.scrollOffset;
            Map<String, Integer> incomingColors = incomingOptionColors();
            for (DialogEntry entry : entries) {
                int h = rowHeight(entry);
                if (y + h >= this.getY() + HEADER_HEIGHT && y <= this.getY() + this.getHeight()) {
                    renderEntry(g, entry, y, h, mouseX, mouseY, incomingColors);
                }
                y += h + 2;
            }
            if (entries.isEmpty()) {
                Component message = this.sequence == null
                        ? Component.translatable("gui.vn_edit.flow.no_sequence")
                        : Component.translatable("gui.vn_edit.flow.no_results");
                g.drawCenteredString(this.font, message, this.getX() + this.getWidth() / 2,
                        this.getY() + this.getHeight() / 2, EditorTheme.TEXT_MUTED);
            }
        } finally {
            g.disableScissor();
        }
        int total = contentHeight(entries);
        if (total > this.getHeight()) {
            int trackX = this.getX() + this.getWidth() - SCROLLBAR_WIDTH;
            int thumbH = Math.max(14, this.getHeight() * this.getHeight() / total);
            int thumbY = this.getY() + (int) ((float) this.scrollOffset / this.maxScroll() * (this.getHeight() - thumbH));
            g.fill(trackX, this.getY(), trackX + SCROLLBAR_WIDTH, this.getY() + this.getHeight(), EditorTheme.SCROLLBAR_TRACK);
            g.fill(trackX, thumbY, trackX + SCROLLBAR_WIDTH, thumbY + thumbH, EditorTheme.SCROLLBAR_THUMB);
        }
    }

    private void renderEntry(GuiGraphics g, DialogEntry entry, int y, int h, int mouseX, int mouseY,
                             Map<String, Integer> incomingColors) {
        boolean selected = entry.getId() != null && entry.getId().equals(this.selectedId);
        boolean hovered = mouseX >= this.getX() && mouseX <= this.getX() + this.getWidth()
                && mouseY >= y && mouseY < y + h;
        int bg = selected ? EditorTheme.BG_SELECTED : hovered ? EditorTheme.BG_HOVER : EditorTheme.BG_DEEPEST;
        g.fill(this.getX() + 4, y, this.getX() + this.getWidth() - 6, y + ENTRY_HEIGHT, bg);
        Integer incomingColor = incomingColors.get(entry.getId());
        if (incomingColor != null) {
            g.fill(this.getX() + 4, y, this.getX() + 7, y + ENTRY_HEIGHT, incomingColor);
        }
        if (selected) {
            g.fill(this.getX() + 4, y, this.getX() + 6, y + ENTRY_HEIGHT, EditorTheme.ACCENT);
        }
        String marker = entry.getId() != null && entry.getId().equals(this.sequence.getStartId()) ? "START " : "";
        String type = entry.isEndDialog() ? "END" : entry.hasOptions() ? "CHOICE" : "LINE";
        g.drawString(this.font, marker + type, this.getX() + 12, y + 5, EditorTheme.ACCENT, selected);
        int idColor = incomingColor == null ? EditorTheme.TEXT_PRIMARY : incomingColor;
        g.drawString(this.font, entry.getId() == null ? "untitled" : entry.getId(), this.getX() + 78, y + 5, idColor, selected);
        String speaker = plain(entry.getSpeaker());
        String text = plain(entry.getText()).replace('\n', ' ');
        if (speaker.isEmpty()) speaker = "-";
        if (text.isEmpty()) text = "(empty text)";
        g.drawString(this.font, this.font.plainSubstrByWidth(speaker, this.getWidth() - 24), this.getX() + 12, y + 20, EditorTheme.TEXT_WARM);
        g.drawString(this.font, this.font.plainSubstrByWidth(text, this.getWidth() - 24), this.getX() + 12, y + 35, EditorTheme.TEXT_SECONDARY);
        int optionY = y + ENTRY_HEIGHT;
        if (entry.getOptions() != null) {
            for (int i = 0; i < entry.getOptions().length; i++) {
                DialogOption option = entry.getOptions()[i];
                String optionText = option == null ? "(null option)" : plain(option.getText());
                String target = option == null || option.getTargetId() == null || option.getTargetId().isBlank()
                        ? "END" : option.getTargetId();
                int color = EditorTheme.OPTION_PALETTE[i % EditorTheme.OPTION_PALETTE.length];
                g.fill(this.getX() + 16, optionY + 3, this.getX() + 20, optionY + 13, color);
                g.drawString(this.font, this.font.plainSubstrByWidth((i + 1) + ". " + optionText,
                        Math.max(40, this.getWidth() - 100)), this.getX() + 25, optionY + 4, EditorTheme.TEXT_SECONDARY);
                g.drawString(this.font, this.font.plainSubstrByWidth("-> " + target, 70),
                        this.getX() + this.getWidth() - 80, optionY + 4, color);
                optionY += OPTION_HEIGHT;
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.isMouseOver(mouseX, mouseY)) return false;
        int total = contentHeight(visibleEntries());
        if (button == 0 && total > this.getHeight() && mouseX >= this.getX() + this.getWidth() - SCROLLBAR_WIDTH) {
            this.scrollOffset = Mth.clamp((int) ((mouseY - this.getY()) / (double) this.getHeight() * this.maxScroll()), 0, this.maxScroll());
            return true;
        }
        if (button != 0 && button != 1) return true;
        int y = this.getY() + HEADER_HEIGHT - this.scrollOffset;
        for (DialogEntry entry : visibleEntries()) {
            int h = rowHeight(entry);
            if (mouseY >= y && mouseY < y + h) {
                if (button == 1 && this.onDelete != null) this.onDelete.accept(entry);
                if (button == 0) {
                    this.selectedId = entry.getId();
                    EditorScreenState.get().setSelectedNodeId(this.selectedId);
                    if (this.onSelect != null) this.onSelect.accept(entry);
                }
                return true;
            }
            y += h + 2;
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!this.isMouseOver(mouseX, mouseY)) return false;
        this.scrollOffset = Mth.clamp(this.scrollOffset - (int) scrollY * 24, 0, this.maxScroll());
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        return false;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narration) {
        narration.add(NarratedElementType.TITLE, Component.translatable("gui.vn_edit.flow"));
    }
}
