package top.yourzi.dialog.editor.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import top.yourzi.dialog.editor.gui.widget.EditorButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import top.yourzi.dialog.editor.util.EditorTheme;
import top.yourzi.dialog.editor.util.PageLayout;
import top.yourzi.dialog.model.DialogSequence;

import java.util.function.Consumer;

/**
 * 序列属性编辑屏幕：编辑对话序列的 title/description/effect/start 等序列级字段。
 * 这些字段在 test_dialog.json 中使用，但之前的编辑器没有界面编辑它们。
 *
 * 使用 PageLayout 游标布局统一 init 与 render 的 Y 推进，消除手写 y+=38 双处同步负担。
 * 子屏（NodePicker）返回时 init() 重入，靠临时变量保留用户未保存的编辑内容。
 */
public class SequencePropertiesScreen extends Screen {
    private static final int FIELD_WIDTH = 240;

    private final DialogSequence sequence;
    private final Consumer<DialogSequence> onSave;
    private final Screen parent;
    private EditBox titleBox;
    private EditBox descriptionBox;
    private EditBox effectBox;
    private EditBox startIdBox;
    private EditorButton startPickerBtn;
    /** 删除对话文件回调：由 VNDialogEditorScreen 注入，未注入则不显示删除按钮。 */
    private Consumer<DialogSequence> onDelete = null;
    /** 布局游标：init 与 render 共用同一实例，确保标签 Y 与输入框 Y 自动对齐。 */
    private PageLayout layout;
    /** 各字段标签的 Y 坐标（由 layout 推进得出，render 时取用）。 */
    private int titleLabelY;
    private int descLabelY;
    private int effectLabelY;
    private int startLabelY;

    public SequencePropertiesScreen(DialogSequence sequence, Consumer<DialogSequence> onSave, Screen parent) {
        super(Component.translatable("gui.vn_edit.sequence_props.title"));
        this.sequence = sequence;
        this.onSave = onSave;
        this.parent = parent;
    }

    /**
     * 注入删除回调：设置后序列属性屏底部显示"删除对话文件"按钮（危险操作，二次确认）。
     * 将"删除文件"入口从标签右键迁移至此，避免与"关闭标签"语义混淆。
     */
    public void setOnDelete(Consumer<DialogSequence> onDelete) {
        this.onDelete = onDelete;
    }

    @Override
    protected void init() {
        super.init();
        // 暂存当前输入框值：子屏（如 NodePickerScreen）返回时 init() 会被重新调用，
        // 此时需优先保留用户已输入的内容与子屏选择结果，而不是用 sequence 的旧值覆盖。
        String curTitle = this.titleBox != null ? this.titleBox.getValue() : null;
        String curDesc = this.descriptionBox != null ? this.descriptionBox.getValue() : null;
        String curEffect = this.effectBox != null ? this.effectBox.getValue() : null;
        String curStart = this.startIdBox != null ? this.startIdBox.getValue() : null;
        int fieldX = (this.width - FIELD_WIDTH) / 2;
        // 布局原点对齐字段左侧（SequenceProperties 无标签列，标签直接画在字段上方）。
        // 总宽用 FIELD_WIDTH，layout 内部游标从 originY=30 起向下推进。
        this.layout = new PageLayout(fieldX, 30, FIELD_WIDTH);
        // 每个字段：标签行（14px）+ 间距 + 输入框行（16px）+ 段间距，与原 y+=38 视觉等价。
        // 用 fieldRow 推进输入框 Y，标签 Y 取输入框 Y - 12（标签在输入框上方）。
        this.titleLabelY = this.layout.currentY();
        int titleY = this.layout.customRow(EditorTheme.FIELD_HEIGHT + 12);
        // customRow 返回行顶部 Y（= 标签 Y），输入框在标签下方 12px，否则标签文字画进输入框里
        this.titleBox = new EditBox(this.font, fieldX, titleY + 12, FIELD_WIDTH, 16, Component.translatable("gui.vn_edit.sequence_title"));
        this.titleBox.setMaxLength(999999999);
        this.titleBox.setValue(curTitle != null ? curTitle : (this.sequence.getTitle() != null ? this.sequence.getTitle() : ""));
        this.addRenderableWidget(this.titleBox);
        this.layout.spacer(EditorTheme.ROW_GAP + 6);

        this.descLabelY = this.layout.currentY();
        int descY = this.layout.customRow(EditorTheme.FIELD_HEIGHT + 12);
        this.descriptionBox = new EditBox(this.font, fieldX, descY + 12, FIELD_WIDTH, 16, Component.translatable("gui.vn_edit.sequence_description"));
        this.descriptionBox.setMaxLength(999999999);
        this.descriptionBox.setValue(curDesc != null ? curDesc : (this.sequence.getDescription() != null ? this.sequence.getDescription() : ""));
        this.addRenderableWidget(this.descriptionBox);
        this.layout.spacer(EditorTheme.ROW_GAP + 6);

        this.effectLabelY = this.layout.currentY();
        int effectY = this.layout.customRow(EditorTheme.FIELD_HEIGHT + 12);
        this.effectBox = new EditBox(this.font, fieldX, effectY + 12, FIELD_WIDTH, 16, Component.translatable("gui.vn_edit.sequence_effect"));
        this.effectBox.setMaxLength(999999999);
        this.effectBox.setValue(curEffect != null ? curEffect : (this.sequence.getEffect() != null ? this.sequence.getEffect() : ""));
        this.addRenderableWidget(this.effectBox);
        this.layout.spacer(EditorTheme.ROW_GAP + 6);

        this.startLabelY = this.layout.currentY();
        int startY = this.layout.customRow(EditorTheme.FIELD_HEIGHT + 12);
        this.startIdBox = new EditBox(this.font, fieldX, startY + 12, FIELD_WIDTH - 60, 16, Component.translatable("gui.vn_edit.sequence_start"));
        this.startIdBox.setMaxLength(999999999);
        this.startIdBox.setValue(curStart != null ? curStart : (this.sequence.getStartId() != null ? this.sequence.getStartId() : ""));
        this.startIdBox.setResponder(s -> {});
        this.addRenderableWidget(this.startIdBox);
        this.startPickerBtn = EditorButton.builder(Component.translatable("gui.vn_edit.pick"), btn -> this.openStartNodePicker())
                .bounds(fieldX + FIELD_WIDTH - 55, startY + 12, 50, 16).build();
        this.addRenderableWidget(this.startPickerBtn);

        int bottomY = this.height - 30;
        EditorButton saveBtn = EditorButton.builder(Component.translatable("gui.vn_edit.save"), btn -> this.saveAndClose())
                .bounds(this.width / 2 - 55, bottomY, 110, 20).build();
        this.addRenderableWidget(saveBtn);
        EditorButton cancelBtn = EditorButton.builder(Component.translatable("gui.vn_edit.cancel"), btn -> this.onClose())
                .bounds(this.width / 2 + 65, bottomY, 50, 20).build();
        this.addRenderableWidget(cancelBtn);
        // 删除对话文件按钮：仅当注入了 onDelete 回调时显示。放在左下角与保存/取消区分，危险操作用 DANGER 色。
        if (this.onDelete != null) {
            EditorButton deleteBtn = EditorButton.builder(Component.translatable("gui.vn_edit.delete_dialog.title"), btn -> this.confirmDelete())
                    .bounds(10, bottomY, 80, 20).build();
            this.addRenderableWidget(deleteBtn);
        }
    }

    private void openStartNodePicker() {
        Minecraft.getInstance().setScreen(new NodePickerScreen(this.sequence, selectedId -> {
            this.startIdBox.setValue(selectedId);
        }, Minecraft.getInstance().screen));
    }

    /**
     * 删除对话文件二次确认：删除是危险操作，弹 EditorConfirmScreen 明确提示"将删除 JSON 文件"。
     * 确认后回调 VNDialogEditorScreen 执行实际删除（删文件 + 关闭标签）。
     */
    private void confirmDelete() {
        Minecraft.getInstance().setScreen(new EditorConfirmScreen(
                Component.translatable("gui.vn_edit.delete_dialog.title"),
                Component.translatable("gui.vn_edit.delete_dialog.message", this.sequence.getId()),
                confirmed -> {
                    if (confirmed && this.onDelete != null) {
                        this.onDelete.accept(this.sequence);
                    }
                }, Minecraft.getInstance().screen));
    }

    private void saveAndClose() {
        this.sequence.setTitle(this.titleBox.getValue().isEmpty() ? null : this.titleBox.getValue());
        this.sequence.setDescription(this.descriptionBox.getValue().isEmpty() ? null : this.descriptionBox.getValue());
        this.sequence.setEffect(this.effectBox.getValue().isEmpty() ? null : this.effectBox.getValue());
        this.sequence.setStartId(this.startIdBox.getValue().isEmpty() ? null : this.startIdBox.getValue());
        if (this.onSave != null) {
            this.onSave.accept(this.sequence);
        }
        this.onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        int panelW = Math.min(FIELD_WIDTH + 40, this.width - 24);
        EditorTheme.drawPanelHeader(graphics, this.font, (this.width - panelW) / 2, 4, panelW, "SQ", this.title);
        int fieldX = (this.width - FIELD_WIDTH) / 2;
        // 标签 Y 由 layout 推进得出，与 init 中输入框 Y 自动对齐，无需手写双处 y+=38。
        graphics.drawString(this.font, Component.translatable("gui.vn_edit.sequence_title"), fieldX, this.titleLabelY, EditorTheme.TEXT_SECONDARY);
        graphics.drawString(this.font, Component.translatable("gui.vn_edit.sequence_description"), fieldX, this.descLabelY, EditorTheme.TEXT_SECONDARY);
        graphics.drawString(this.font, Component.translatable("gui.vn_edit.sequence_effect"), fieldX, this.effectLabelY, EditorTheme.TEXT_SECONDARY);
        graphics.drawString(this.font, Component.translatable("gui.vn_edit.sequence_start"), fieldX, this.startLabelY, EditorTheme.TEXT_SECONDARY);
        super.render(graphics, mouseX, mouseY, partialTick);
        EditorRenderHelper.drawFocusedEditBoxBorders(graphics, this.children());
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, EditorTheme.BG_DEEPEST);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }
}
