package top.yourzi.dialog.editor.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import top.yourzi.dialog.model.DialogEntry;
import top.yourzi.dialog.model.DialogSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 节点选择屏幕：在对话序列中挑选下一个节点 ID。融合自 visual_mod_edit_vndialog。
 */
public class NodePickerScreen extends Screen {
    private static final int ROW_HEIGHT = 14;
    private static final int LIST_TOP = 30;
    private static final int LIST_BOTTOM = 40;

    private final DialogSequence sequence;
    private final Consumer<String> onSelected;
    private final Screen parent;
    private final List<String> nodeIds = new ArrayList<>();
    private int scrollOffset = 0;

    public NodePickerScreen(DialogSequence sequence, Consumer<String> onSelected, Screen parent) {
        super(Component.translatable("gui.vn_edit.node_picker.title"));
        this.sequence = sequence;
        this.onSelected = onSelected;
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        this.nodeIds.clear();
        if (this.sequence != null && this.sequence.getEntries() != null) {
            for (DialogEntry e : this.sequence.getEntries()) {
                this.nodeIds.add(e.getId());
            }
        }
        this.addRenderableWidget(Button.builder(Component.translatable("gui.vn_edit.cancel"), btn -> this.onClose())
                .bounds(this.width / 2 - 50, this.height - 30, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);
        int listHeight = this.height - LIST_TOP - LIST_BOTTOM;
        graphics.enableScissor(0, LIST_TOP, this.width, LIST_TOP + listHeight);
        int yOffset = LIST_TOP - this.scrollOffset;
        for (int i = 0; i < this.nodeIds.size(); i++) {
            int rowY = yOffset + i * ROW_HEIGHT;
            if (rowY + ROW_HEIGHT < LIST_TOP || rowY > LIST_TOP + listHeight) {
                continue;
            }
            boolean hovered = mouseX >= 20 && mouseX <= this.width - 20 && mouseY >= rowY && mouseY <= rowY + ROW_HEIGHT;
            int color = hovered ? -256 : -1;
            if (hovered) {
                graphics.fill(20, rowY, this.width - 20, rowY + ROW_HEIGHT, 0x44FFFFFF);
            }
            graphics.drawString(this.font, this.nodeIds.get(i), 25, rowY + 2, color);
        }
        graphics.disableScissor();
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int listHeight = this.height - LIST_TOP - LIST_BOTTOM;
            if (mouseY >= LIST_TOP && mouseY <= LIST_TOP + listHeight) {
                int index = (int) ((mouseY - LIST_TOP + this.scrollOffset) / ROW_HEIGHT);
                if (index >= 0 && index < this.nodeIds.size()) {
                    this.onSelected.accept(this.nodeIds.get(index));
                    this.onClose();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        this.scrollOffset = Math.max(0, this.scrollOffset - (int) scrollY * ROW_HEIGHT);
        int maxScroll = Math.max(0, this.nodeIds.size() * ROW_HEIGHT - (this.height - LIST_TOP - LIST_BOTTOM));
        if (this.scrollOffset > maxScroll) {
            this.scrollOffset = maxScroll;
        }
        return true;
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }
}
