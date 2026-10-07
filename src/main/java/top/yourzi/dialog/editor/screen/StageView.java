package top.yourzi.dialog.editor.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;
import top.yourzi.dialog.config.ClientConfig;
import top.yourzi.dialog.editor.AssetService;
import top.yourzi.dialog.editor.TextCodec;
import top.yourzi.dialog.editor.ui.Button;
import top.yourzi.dialog.editor.ui.Column;
import top.yourzi.dialog.editor.ui.Nodes;
import top.yourzi.dialog.editor.ui.Paragraph;
import top.yourzi.dialog.editor.ui.Row;
import top.yourzi.dialog.editor.ui.ScrollView;
import top.yourzi.dialog.editor.ui.Select;
import top.yourzi.dialog.editor.ui.Theme;
import top.yourzi.dialog.editor.ui.UiNode;
import top.yourzi.dialog.model.BackgroundImageInfo;
import top.yourzi.dialog.model.DialogEntry;
import top.yourzi.dialog.model.DialogOption;
import top.yourzi.dialog.model.PortraitAnimationType;
import top.yourzi.dialog.model.PortraitInfo;
import top.yourzi.dialog.model.PortraitPosition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Stage view: the selected node exactly as the player will see it.
 *
 * <p>The scene is drawn in the coordinates of the real game screen - the GUI-scaled window size the
 * dialogue screen will open at - with the same formulas as {@code DialogScreen}: portrait height is
 * 68% of the screen times size, a 20px side margin, bottom alignment, offsets as screen fractions,
 * a dialogue box sized from the client config, and choice buttons above it. The whole scene is then
 * scaled into the panel, so what lines up here lines up in game.
 */
final class StageView extends UiNode {
    private static final int SIDE_MARGIN = 20;
    private static final float PORTRAIT_HEIGHT = 0.68f;
    private static final int PAD = 8;
    private static final int MIN_CONTROLS = 132;

    private final EditorContext context;
    private final Column portraits = new Column().gap(2);
    private final Column sliders = new Column().gap(2);
    private final Column details = new Column().gap(3);
    private final Band band = new Band(this.portraits, this.sliders, this.details);
    private final ScrollView controls = new ScrollView(this.band);
    private final Map<String, AssetService.Handle> assets = new HashMap<>();
    private StagingTab.Actions actions;

    private DialogEntry entry;
    private PortraitInfo selected;
    private int screenWidth = 480;
    private int screenHeight = 270;
    private int stageX;
    private int stageY;
    private int stageWidth;
    private int stageHeight;
    private float scale = 1.0f;
    private boolean dragging;
    private double lastMouseX;
    private double lastMouseY;
    private Slider size;
    private Slider brightness;
    private Slider offsetX;
    private Slider offsetY;

    StageView(EditorContext context) {
        this.context = context;
        this.add(this.controls);
    }

    void setActions(StagingTab.Actions actions) {
        this.actions = actions;
    }

    @Override
    protected void onLayout() {
        Minecraft minecraft = Minecraft.getInstance();
        this.screenWidth = Math.max(1, minecraft.getWindow().getGuiScaledWidth());
        this.screenHeight = Math.max(1, minecraft.getWindow().getGuiScaledHeight());
        int availableWidth = Math.max(1, this.width() - PAD * 2);
        int controlsHeight = Math.max(MIN_CONTROLS, this.band.measureHeight(availableWidth));
        int availableHeight = Math.max(60, this.height() - PAD * 3 - Math.min(controlsHeight, this.height() / 2));
        this.scale = Math.min((float) availableWidth / this.screenWidth, (float) availableHeight / this.screenHeight);
        this.stageWidth = Math.max(1, Math.round(this.screenWidth * this.scale));
        this.stageHeight = Math.max(1, Math.round(this.screenHeight * this.scale));
        this.stageX = this.x() + (this.width() - this.stageWidth) / 2;
        this.stageY = this.y() + PAD;
        int controlsY = this.stageY + this.stageHeight + PAD;
        this.controls.setBounds(this.x() + PAD, controlsY, availableWidth, Math.max(0, this.bottom() - controlsY - 4));
    }

    void bind(DialogEntry entry) {
        PortraitInfo previous = this.selected;
        this.entry = entry;
        List<PortraitInfo> list = this.portraitList();
        this.selected = list.contains(previous) ? previous : list.isEmpty() ? null : list.get(0);
        this.rebuildControls();
    }

    void focus(PortraitInfo portrait) {
        if (portrait != null && this.portraitList().contains(portrait)) {
            this.selected = portrait;
            this.rebuildControls();
        }
    }

    private List<PortraitInfo> portraitList() {
        List<PortraitInfo> result = new ArrayList<>();
        if (this.entry != null && this.entry.getPortraits() != null) {
            for (PortraitInfo portrait : this.entry.getPortraits()) {
                if (portrait != null && portrait.getPath() != null && !portrait.getPath().isEmpty()) {
                    result.add(portrait);
                }
            }
        }
        return result;
    }

    // ----- controls -----

    private void rebuildControls() {
        this.portraits.clear();
        this.sliders.clear();
        this.details.clear();
        this.size = null;
        if (this.entry == null) {
            return;
        }
        this.portraits.add(Nodes.section(Theme.tr("section.portraits")));
        for (PortraitInfo portrait : this.portraitList()) {
            this.portraits.add(Button.of(Component.literal(portrait.getPath()), () -> {
                this.selected = portrait;
                this.rebuildControls();
            }).tone(Button.Tone.GHOST).selected(portrait == this.selected));
        }
        Row add = new Row().gap(3);
        add.add(Button.of(Theme.tr("stage.add_portrait"), () -> this.actions.pickPortraitFile()).fit());
        add.add(Button.of(Theme.tr("staging.builtin"), () -> this.actions.pickBuiltinPortrait()).tone(Button.Tone.GHOST).fit());
        add.add(Nodes.fill());
        this.portraits.add(add);
        this.portraits.add(Nodes.section(Theme.tr("section.background")));
        Row background = new Row().gap(3);
        background.add(Button.of(Theme.tr("stage.background"), () -> this.actions.pickBackgroundFile()).fit());
        background.add(Button.of(Theme.tr("staging.builtin"), () -> this.actions.pickBuiltinBackground()).tone(Button.Tone.GHOST).fit());
        background.add(Nodes.fill());
        this.portraits.add(background);

        if (this.selected == null) {
            this.sliders.add(Paragraph.of(Theme.tr("stage.no_portraits")).color(Theme.TEXT_MUTED));
            this.details.add(Paragraph.of(Theme.tr("stage.help")).color(Theme.TEXT_MUTED));
            return;
        }
        PortraitInfo target = this.selected;
        this.sliders.add(Nodes.section(Component.literal(target.getPath())));
        this.size = new Slider(Theme.tr("stage.size"), 0.1f, 3.0f, target.getSize(), value -> this.edit(() -> target.setSize(value)));
        this.brightness = new Slider(Theme.tr("stage.brightness"), 0.0f, 1.0f, target.getBrightness(),
                value -> this.edit(() -> target.setBrightness(value)));
        this.offsetX = new Slider(Theme.tr("stage.offset_x"), -1.0f, 1.0f, target.getOffsetX(),
                value -> this.edit(() -> target.setOffsetX(value)));
        this.offsetY = new Slider(Theme.tr("stage.offset_y"), -1.0f, 1.0f, target.getOffsetY(),
                value -> this.edit(() -> target.setOffsetY(value)));
        this.sliders.add(this.size);
        this.sliders.add(this.brightness);
        this.sliders.add(this.offsetX);
        this.sliders.add(this.offsetY);

        this.details.add(Nodes.section(Theme.tr("stage.position")));
        this.details.add(new Select<>(List.of(PortraitPosition.values()), target.getPosition(),
                position -> Theme.tr("position." + position.name().toLowerCase(Locale.ROOT)),
                position -> this.edit(() -> target.setPosition(position))));
        this.details.add(Nodes.caption(Theme.tr("stage.animation")));
        this.details.add(new Select<>(List.of(PortraitAnimationType.values()),
                target.getAnimationType() == null ? PortraitAnimationType.NONE : target.getAnimationType(),
                animation -> Theme.tr("animation." + animation.name().toLowerCase(Locale.ROOT)),
                animation -> this.edit(() -> target.setAnimationType(animation))));
        Row actionsRow = new Row().gap(3);
        actionsRow.add(Button.of(Theme.tr("stage.reset"), this::resetSelected).fit());
        actionsRow.add(Nodes.fill());
        actionsRow.add(Button.of(Theme.tr("stage.remove"), this::removeSelected).tone(Button.Tone.DANGER).fit());
        this.details.add(actionsRow);
        this.details.add(Paragraph.of(Theme.tr("stage.help")).color(Theme.TEXT_MUTED));
    }

    private void edit(Runnable change) {
        change.run();
        this.syncSliders();
        this.context.touch(true);
    }

    private void syncSliders() {
        if (this.selected == null || this.size == null) {
            return;
        }
        this.size.setValue(this.selected.getSize());
        this.brightness.setValue(this.selected.getBrightness());
        this.offsetX.setValue(this.selected.getOffsetX());
        this.offsetY.setValue(this.selected.getOffsetY());
    }

    private void resetSelected() {
        if (this.selected == null) {
            return;
        }
        this.selected.setSize(1.0f);
        this.selected.setBrightness(1.0f);
        this.selected.setOffsetX(0.0f);
        this.selected.setOffsetY(0.0f);
        this.syncSliders();
        this.context.touch(false);
    }

    private void removeSelected() {
        if (this.entry == null || this.selected == null) {
            return;
        }
        List<PortraitInfo> list = new ArrayList<>(this.entry.getPortraits());
        list.remove(this.selected);
        this.entry.setPortraits(list.isEmpty() ? null : list);
        this.selected = null;
        this.context.touchStructure();
    }

    /** Adds a portrait chosen in a picker, or selects it when the node already shows it. */
    void addPortrait(String path) {
        if (this.entry == null || path == null || path.isBlank()) {
            return;
        }
        String clean = path.toLowerCase(Locale.ROOT);
        for (PortraitInfo portrait : this.portraitList()) {
            if (clean.equalsIgnoreCase(portrait.getPath())) {
                this.selected = portrait;
                this.rebuildControls();
                this.context.status(Theme.tr("stage.portrait_exists"), EditorContext.StatusKind.WARNING);
                return;
            }
        }
        List<PortraitInfo> list = this.entry.getPortraits() == null ? new ArrayList<>() : new ArrayList<>(this.entry.getPortraits());
        PortraitInfo portrait = new PortraitInfo(clean, PortraitPosition.RIGHT, 1.0f, PortraitAnimationType.NONE);
        list.add(portrait);
        this.entry.setPortraits(list);
        this.selected = portrait;
        this.context.touchStructure();
    }

    // ----- runtime geometry, in game-screen pixels -----

    private AssetService.Handle asset(PortraitInfo portrait) {
        return this.assets.computeIfAbsent(portrait.getPath(), AssetService::portrait);
    }

    /** {x, y, width, height} as DialogScreen computes them, or null when the image is missing. */
    private int[] portraitBox(PortraitInfo portrait) {
        AssetService.Handle handle = this.asset(portrait);
        if (!handle.present()) {
            return null;
        }
        int height = (int) (this.screenHeight * PORTRAIT_HEIGHT * Mth.clamp(portrait.getSize(), 0.1f, 5.0f));
        int width = Math.max(1, (int) (height * handle.aspect()));
        int x = switch (portrait.getPosition() == null ? PortraitPosition.RIGHT : portrait.getPosition()) {
            case LEFT -> SIDE_MARGIN;
            case CENTER -> (this.screenWidth - width) / 2;
            case RIGHT -> this.screenWidth - width - SIDE_MARGIN;
        };
        boolean topAligned = portrait.getAnimationType() == PortraitAnimationType.REVERSE;
        int y = topAligned ? 0 : this.screenHeight - height;
        return new int[]{x + (int) (Mth.clamp(portrait.getOffsetX(), -1.0f, 1.0f) * this.screenWidth),
                y + (int) (Mth.clamp(portrait.getOffsetY(), -1.0f, 1.0f) * this.screenHeight), width, height};
    }

    private double gameX(double mouseX) {
        return (mouseX - this.stageX) / this.scale;
    }

    private double gameY(double mouseY) {
        return (mouseY - this.stageY) / this.scale;
    }

    private boolean onStage(double mouseX, double mouseY) {
        return mouseX >= this.stageX && mouseX < this.stageX + this.stageWidth
                && mouseY >= this.stageY && mouseY < this.stageY + this.stageHeight;
    }

    private PortraitInfo portraitAt(double mouseX, double mouseY) {
        double x = this.gameX(mouseX);
        double y = this.gameY(mouseY);
        List<PortraitInfo> list = this.portraitList();
        for (int i = list.size() - 1; i >= 0; i--) {
            int[] box = this.portraitBox(list.get(i));
            if (box != null && x >= box[0] && x < box[0] + box[2] && y >= box[1] && y < box[1] + box[3]) {
                return list.get(i);
            }
        }
        return null;
    }

    // ----- drawing -----

    @Override
    protected void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(this.x(), this.y(), this.right(), this.bottom(), Theme.SURFACE);
        if (this.entry == null) {
            graphics.fill(this.stageX, this.stageY, this.stageX + this.stageWidth, this.stageY + this.stageHeight, Theme.FIELD);
            Theme.centered(graphics, Theme.tr("inspector.hint").getString(), this.stageX + this.stageWidth / 2,
                    this.stageY + this.stageHeight / 2 - 4, Theme.TEXT_MUTED);
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(this.stageX, this.stageY, 0.0f);
        graphics.pose().scale(this.scale, this.scale, 1.0f);
        Theme.clip(graphics, 0, 0, this.screenWidth, this.screenHeight);
        this.renderBackground(graphics);
        this.renderPortraits(graphics);
        this.renderDialogBox(graphics);
        this.renderChoices(graphics);
        this.renderSelection(graphics);
        Theme.unclip(graphics);
        graphics.pose().popPose();
        Theme.border(graphics, this.stageX - 1, this.stageY - 1, this.stageWidth + 2, this.stageHeight + 2, Theme.BORDER_STRONG);
        String size = this.screenWidth + " × " + this.screenHeight;
        Theme.text(graphics, size, this.stageX + this.stageWidth - Theme.font().width(size), this.stageY + this.stageHeight + 2,
                Theme.TEXT_MUTED);
    }

    private void renderBackground(GuiGraphics graphics) {
        BackgroundImageInfo background = this.entry.getBackgroundImage();
        String path = background == null ? null : background.getPath();
        if (path == null || path.isBlank()) {
            // In game the world shows through a light dim; a flat tone stands in for it here.
            graphics.fill(0, 0, this.screenWidth, this.screenHeight, 0xFF3A4046);
            graphics.fill(0, 0, this.screenWidth, this.screenHeight, 0x66000000);
            return;
        }
        AssetService.Handle handle = this.assets.computeIfAbsent("bg:" + path, key -> AssetService.background(path));
        if (handle.present()) {
            AssetService.blitStretched(graphics, handle, 0, 0, this.screenWidth, this.screenHeight);
        } else {
            graphics.fill(0, 0, this.screenWidth, this.screenHeight, 0xFF2A2B30);
            Theme.centered(graphics, Theme.tr("stage.missing", path).getString(), this.screenWidth / 2, 8, Theme.WARNING);
        }
    }

    private void renderPortraits(GuiGraphics graphics) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        for (PortraitInfo portrait : this.portraitList()) {
            int[] box = this.portraitBox(portrait);
            if (box == null) {
                continue;
            }
            float light = Mth.clamp(portrait.getBrightness(), 0.0f, 1.0f);
            RenderSystem.setShaderColor(light, light, light, 1.0f);
            boolean upsideDown = portrait.getAnimationType() == PortraitAnimationType.REVERSE;
            if (upsideDown) {
                graphics.pose().pushPose();
                graphics.pose().translate(box[0] + box[2] / 2.0f, box[1] + box[3] / 2.0f, 0.0f);
                graphics.pose().mulPose(Axis.ZP.rotationDegrees(180.0f));
                graphics.pose().translate(-(box[0] + box[2] / 2.0f), -(box[1] + box[3] / 2.0f), 0.0f);
            }
            graphics.blit(this.asset(portrait).location(), box[0], box[1], box[2], box[3], 0.0f, 0.0f,
                    box[2], box[3], box[2], box[3]);
            if (upsideDown) {
                graphics.pose().popPose();
            }
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        }
        RenderSystem.disableBlend();
    }

    private int boxWidth() {
        return Math.min(ClientConfig.DIALOG_BOX_WIDTH.get(), this.screenWidth - 20);
    }

    private int boxY() {
        return this.screenHeight - ClientConfig.DIALOG_BOX_HEIGHT.get() - 20;
    }

    private void renderDialogBox(GuiGraphics graphics) {
        int width = this.boxWidth();
        int height = ClientConfig.DIALOG_BOX_HEIGHT.get();
        int x = (this.screenWidth - width) / 2;
        int y = this.boxY();
        int color = (ClientConfig.DIALOG_BACKGROUND_OPACITY.get() << 24) | (ClientConfig.DIALOG_BACKGROUND_COLOR.get() & 0xFFFFFF);
        graphics.fill(x, y, x + width, y + height, color);
        int padding = ClientConfig.DIALOG_BOX_PADDING.get();
        int textX = x + padding;
        int textY = y + padding;
        String speaker = TextCodec.preview(this.entry.getSpeaker());
        if (ClientConfig.SHOW_SPEAKER_NAME.get() && !speaker.isEmpty()) {
            graphics.drawString(Theme.font(), TextCodec.styled(this.entry.getSpeaker()), textX, textY, 0xFFFFFF);
            textY += Theme.font().lineHeight + 5;
        }
        for (FormattedCharSequence line : Theme.font().split(TextCodec.styled(this.entry.getText()), width - padding * 2)) {
            graphics.drawString(Theme.font(), line, textX, textY, ClientConfig.DIALOG_TEXT_COLOR.get());
            textY += Theme.font().lineHeight + 2;
        }
    }

    /** Choice buttons where DialogScreen places them, above the box. */
    private void renderChoices(GuiGraphics graphics) {
        DialogOption[] options = this.entry.getOptions();
        if (options == null || options.length == 0) {
            return;
        }
        int width = Math.min(240, this.screenWidth - 40);
        int startY = this.boxY() - options.length * 25 - 10;
        if (this.entry.getDisplayItems() != null && !this.entry.getDisplayItems().isEmpty()) {
            startY -= 30;
        }
        int x = (this.screenWidth - width) / 2;
        for (int i = 0; i < options.length; i++) {
            int y = startY + i * 25;
            graphics.fill(x, y, x + width, y + 20, 0xFF000000);
            graphics.fill(x + 1, y + 1, x + width - 1, y + 19, 0xFF6F6F6F);
            graphics.fill(x + 1, y + 18, x + width - 1, y + 19, 0xFF3C3C3C);
            String label = options[i] == null ? "" : TextCodec.preview(options[i].getText());
            Theme.centered(graphics, Theme.ellipsize(label, width - 8), x + width / 2, y + 6, 0xFFFFFFFF);
        }
    }

    private void renderSelection(GuiGraphics graphics) {
        if (this.selected == null) {
            return;
        }
        int[] box = this.portraitBox(this.selected);
        if (box == null) {
            Theme.centered(graphics, Theme.tr("stage.missing", this.selected.getPath()).getString(),
                    this.screenWidth / 2, this.screenHeight / 2, Theme.WARNING);
            return;
        }
        int line = Math.max(1, Math.round(1.0f / this.scale));
        int color = this.dragging ? Theme.ACCENT : 0xCCFFFFFF;
        graphics.fill(box[0], box[1], box[0] + box[2], box[1] + line, color);
        graphics.fill(box[0], box[1] + box[3] - line, box[0] + box[2], box[1] + box[3], color);
        graphics.fill(box[0], box[1], box[0] + line, box[1] + box[3], color);
        graphics.fill(box[0] + box[2] - line, box[1], box[0] + box[2], box[1] + box[3], color);
    }

    // ----- interaction -----

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.onStage(mouseX, mouseY) || this.entry == null) {
            return false;
        }
        this.host().focus(this);
        PortraitInfo hit = this.portraitAt(mouseX, mouseY);
        if (hit != null && hit != this.selected) {
            this.selected = hit;
            this.rebuildControls();
        }
        if (hit != null && button == 0) {
            this.dragging = true;
            this.lastMouseX = mouseX;
            this.lastMouseY = mouseY;
            this.host().claimPointer(this);
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!this.dragging || this.selected == null) {
            return false;
        }
        PortraitInfo target = this.selected;
        float dx = (float) ((mouseX - this.lastMouseX) / this.stageWidth);
        float dy = (float) ((mouseY - this.lastMouseY) / this.stageHeight);
        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;
        this.edit(() -> {
            target.setOffsetX(target.getOffsetX() + dx);
            target.setOffsetY(target.getOffsetY() + dy);
        });
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.dragging = false;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!this.onStage(mouseX, mouseY)) {
            return false;
        }
        PortraitInfo target = this.portraitAt(mouseX, mouseY);
        if (target == null) {
            target = this.selected;
        }
        if (target == null) {
            return true;
        }
        if (target != this.selected) {
            this.selected = target;
            this.rebuildControls();
        }
        float step = this.context.shiftDown() ? 0.02f : 0.08f;
        PortraitInfo portrait = target;
        this.edit(() -> portrait.setSize(Mth.clamp(portrait.getSize() + (float) Math.signum(scrollY) * step, 0.1f, 3.0f)));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.selected == null) {
            return false;
        }
        // One step is one game-screen pixel; Shift moves ten.
        float stepX = (this.context.shiftDown() ? 10.0f : 1.0f) / this.screenWidth;
        float stepY = (this.context.shiftDown() ? 10.0f : 1.0f) / this.screenHeight;
        PortraitInfo portrait = this.selected;
        switch (keyCode) {
            case GLFW.GLFW_KEY_LEFT -> this.edit(() -> portrait.setOffsetX(portrait.getOffsetX() - stepX));
            case GLFW.GLFW_KEY_RIGHT -> this.edit(() -> portrait.setOffsetX(portrait.getOffsetX() + stepX));
            case GLFW.GLFW_KEY_UP -> this.edit(() -> portrait.setOffsetY(portrait.getOffsetY() - stepY));
            case GLFW.GLFW_KEY_DOWN -> this.edit(() -> portrait.setOffsetY(portrait.getOffsetY() + stepY));
            case GLFW.GLFW_KEY_R -> this.resetSelected();
            case GLFW.GLFW_KEY_DELETE -> this.removeSelected();
            default -> {
                return false;
            }
        }
        return true;
    }

    /** Three control columns side by side when there is room, stacked when there is not. */
    private static final class Band extends UiNode {
        private static final int GAP = 12;
        private static final int STACK_BELOW = 420;
        private final UiNode[] columns;

        Band(UiNode... columns) {
            this.columns = columns;
            for (UiNode column : columns) {
                this.add(column);
            }
        }

        @Override
        public int measureHeight(int width) {
            if (width < STACK_BELOW) {
                int total = 0;
                for (UiNode column : this.columns) {
                    total += column.measureHeight(width) + GAP;
                }
                return total;
            }
            int columnWidth = (width - GAP * 2) / 3;
            int max = 0;
            for (UiNode column : this.columns) {
                max = Math.max(max, column.measureHeight(columnWidth));
            }
            return max;
        }

        @Override
        protected void onLayout() {
            if (this.width() < STACK_BELOW) {
                int y = this.y();
                for (UiNode column : this.columns) {
                    int height = column.measureHeight(this.width());
                    column.setBounds(this.x(), y, this.width(), height);
                    y += height + GAP;
                }
                return;
            }
            int columnWidth = (this.width() - GAP * 2) / 3;
            for (int i = 0; i < this.columns.length; i++) {
                UiNode column = this.columns[i];
                column.setBounds(this.x() + i * (columnWidth + GAP), this.y(), columnWidth, column.measureHeight(columnWidth));
            }
        }
    }
}
