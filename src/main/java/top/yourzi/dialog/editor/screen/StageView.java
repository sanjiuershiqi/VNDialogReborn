package top.yourzi.dialog.editor.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
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
import top.yourzi.dialog.editor.ui.Wrap;
import top.yourzi.dialog.model.BackgroundImageInfo;
import top.yourzi.dialog.model.DialogEntry;
import top.yourzi.dialog.model.PortraitAnimationType;
import top.yourzi.dialog.model.PortraitInfo;
import top.yourzi.dialog.model.PortraitPosition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Stage view: the selected node's scene as the player will see it, edited by direct manipulation.
 *
 * <p>A fixed 16:9 virtual screen is scaled into the available space, and portraits use the runtime's
 * sizing and anchoring rules (height = 68% of the screen times size, 20px side margin, bottom
 * aligned, offsets as a fraction of the screen). Drag moves a portrait, the wheel resizes it and the
 * arrow keys nudge it; the side column holds the exact values.
 */
final class StageView extends UiNode {
    private static final int VIRTUAL_WIDTH = 480;
    private static final int VIRTUAL_HEIGHT = 270;
    private static final int SIDE_MARGIN = 20;
    private static final float PORTRAIT_HEIGHT = 0.68f;

    private final EditorContext context;
    private final Column side = new Column().gap(3).padding(4);
    private final ScrollView sideScroller = new ScrollView(this.side);
    private final Column portraitList = new Column().gap(2);
    private final Column details = new Column().gap(3);
    private final Map<String, AssetService.Handle> assets = new HashMap<>();
    private StagingTab.Actions actions;

    private DialogEntry entry;
    private PortraitInfo selected;
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
        this.add(this.sideScroller);
        Row assetButtons = new Row().gap(2);
        assetButtons.add(Button.of(Theme.tr("stage.add_portrait"), () -> this.actions.pickPortraitFile()).fit());
        assetButtons.add(Button.of(Theme.tr("staging.builtin"), () -> this.actions.pickBuiltinPortrait()).fit());
        assetButtons.add(Nodes.fill());
        Row backgroundButtons = new Row().gap(2);
        backgroundButtons.add(Button.of(Theme.tr("stage.background"), () -> this.actions.pickBackgroundFile()).fit());
        backgroundButtons.add(Button.of(Theme.tr("staging.builtin"), () -> this.actions.pickBuiltinBackground()).fit());
        backgroundButtons.add(Nodes.fill());
        this.side.add(Nodes.section(Theme.tr("section.portraits")));
        this.side.add(assetButtons);
        this.side.add(this.portraitList);
        this.side.add(this.details);
        this.side.add(Nodes.section(Theme.tr("section.background")));
        this.side.add(backgroundButtons);
        this.side.add(Paragraph.of(Theme.tr("stage.help")).color(Theme.TEXT_MUTED));
    }

    void setActions(StagingTab.Actions actions) {
        this.actions = actions;
    }

    @Override
    protected void onLayout() {
        int sideWidth = Mth.clamp(this.width() / 3, 150, 230);
        this.sideScroller.setBounds(this.x(), this.y(), sideWidth, this.height());
        int available = Math.max(1, this.width() - sideWidth - 8);
        int availableHeight = Math.max(1, this.height() - 8);
        this.scale = Math.min((float) available / VIRTUAL_WIDTH, (float) availableHeight / VIRTUAL_HEIGHT);
        this.stageWidth = Math.max(1, (int) (VIRTUAL_WIDTH * this.scale));
        this.stageHeight = Math.max(1, (int) (VIRTUAL_HEIGHT * this.scale));
        this.stageX = this.x() + sideWidth + 4 + (available - this.stageWidth) / 2;
        this.stageY = this.y() + 4 + (availableHeight - this.stageHeight) / 2;
    }

    void bind(DialogEntry entry) {
        PortraitInfo previous = this.selected;
        this.entry = entry;
        this.selected = null;
        List<PortraitInfo> portraits = this.portraits();
        if (portraits.contains(previous)) {
            this.selected = previous;
        } else if (!portraits.isEmpty()) {
            this.selected = portraits.get(0);
        }
        this.rebuildSide();
    }

    /** Selects a portrait from outside, e.g. a row in the staging tab. */
    void focus(PortraitInfo portrait) {
        if (portrait != null && this.portraits().contains(portrait)) {
            this.selected = portrait;
            this.rebuildSide();
        }
    }

    private List<PortraitInfo> portraits() {
        List<PortraitInfo> result = new ArrayList<>();
        if (this.entry != null && this.entry.getPortraits() != null) {
            for (PortraitInfo portrait : this.entry.getPortraits()) {
                if (portrait != null) {
                    result.add(portrait);
                }
            }
        }
        return result;
    }

    private void rebuildSide() {
        this.portraitList.clear();
        this.details.clear();
        List<PortraitInfo> portraits = this.portraits();
        if (portraits.isEmpty()) {
            this.portraitList.add(Paragraph.of(Theme.tr(this.entry == null ? "inspector.hint" : "stage.no_portraits"))
                    .color(Theme.TEXT_MUTED));
        }
        for (PortraitInfo portrait : portraits) {
            String path = portrait.getPath() == null ? "—" : portrait.getPath();
            Button chip = Button.of(Component.literal(path), () -> {
                this.selected = portrait;
                this.rebuildSide();
            }).selected(portrait == this.selected);
            this.portraitList.add(chip);
        }
        if (this.selected == null) {
            return;
        }
        PortraitInfo target = this.selected;
        this.details.add(Nodes.caption(Theme.tr("stage.position")));
        this.details.add(new Select<>(List.of(PortraitPosition.values()), target.getPosition(),
                position -> Theme.tr("position." + position.name().toLowerCase(Locale.ROOT)),
                position -> this.edit(() -> target.setPosition(position))));
        this.details.add(Nodes.caption(Theme.tr("stage.animation")));
        this.details.add(new Select<>(List.of(PortraitAnimationType.values()),
                target.getAnimationType() == null ? PortraitAnimationType.NONE : target.getAnimationType(),
                animation -> Theme.tr("animation." + animation.name().toLowerCase(Locale.ROOT)),
                animation -> this.edit(() -> target.setAnimationType(animation))));
        this.size = new Slider(Theme.tr("stage.size"), 0.1f, 3.0f, target.getSize(),
                value -> this.edit(() -> target.setSize(value)));
        this.brightness = new Slider(Theme.tr("stage.brightness"), 0.0f, 1.0f, target.getBrightness(),
                value -> this.edit(() -> target.setBrightness(value)));
        this.offsetX = new Slider(Theme.tr("stage.offset_x"), -1.0f, 1.0f, target.getOffsetX(),
                value -> this.edit(() -> target.setOffsetX(value)));
        this.offsetY = new Slider(Theme.tr("stage.offset_y"), -1.0f, 1.0f, target.getOffsetY(),
                value -> this.edit(() -> target.setOffsetY(value)));
        this.details.add(this.size);
        this.details.add(this.brightness);
        this.details.add(this.offsetX);
        this.details.add(this.offsetY);
        Row actionsRow = new Row().gap(2);
        actionsRow.add(Button.of(Theme.tr("stage.reset"), this::resetSelected).fit());
        actionsRow.add(Button.of(Theme.tr("stage.remove"), this::removeSelected).tone(Button.Tone.GHOST).fit());
        actionsRow.add(Nodes.fill());
        this.details.add(actionsRow);
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
        List<PortraitInfo> portraits = this.portraits();
        portraits.remove(this.selected);
        this.entry.setPortraits(portraits.isEmpty() ? null : portraits);
        this.selected = null;
        this.context.touchStructure();
    }

    /** Adds a portrait chosen in a picker, or selects it when the node already shows it. */
    void addPortrait(String path) {
        if (this.entry == null || path == null || path.isBlank()) {
            return;
        }
        String clean = path.toLowerCase(Locale.ROOT);
        List<PortraitInfo> portraits = this.portraits();
        for (PortraitInfo portrait : portraits) {
            if (clean.equalsIgnoreCase(portrait.getPath())) {
                this.selected = portrait;
                this.rebuildSide();
                this.context.status(Theme.tr("stage.portrait_exists"), EditorContext.StatusKind.WARNING);
                return;
            }
        }
        PortraitInfo portrait = new PortraitInfo(clean, PortraitPosition.RIGHT, 1.0f, PortraitAnimationType.NONE);
        portraits.add(portrait);
        this.entry.setPortraits(portraits);
        this.selected = portrait;
        this.context.touchStructure();
    }

    // ----- geometry, shared by drawing and hit testing -----

    private int scaled(int virtualPixels) {
        return (int) (virtualPixels * this.scale);
    }

    private AssetService.Handle asset(PortraitInfo portrait) {
        String path = portrait.getPath();
        if (path == null || path.isBlank()) {
            return AssetService.MISSING;
        }
        return this.assets.computeIfAbsent(path, AssetService::portrait);
    }

    /** {x, y, width, height} of a portrait on the stage, or null when its texture is missing. */
    private int[] portraitBox(PortraitInfo portrait) {
        AssetService.Handle handle = this.asset(portrait);
        if (!handle.present()) {
            return null;
        }
        int height = Math.max(1, (int) (this.stageHeight * PORTRAIT_HEIGHT * Mth.clamp(portrait.getSize(), 0.1f, 5.0f)));
        int width = Math.max(1, (int) (height * handle.aspect()));
        int x = switch (portrait.getPosition()) {
            case LEFT -> this.stageX + this.scaled(SIDE_MARGIN);
            case CENTER -> this.stageX + (this.stageWidth - width) / 2;
            case RIGHT -> this.stageX + this.stageWidth - width - this.scaled(SIDE_MARGIN);
        };
        int y = this.stageY + this.stageHeight - height;
        return new int[]{x + (int) (portrait.getOffsetX() * this.stageWidth),
                y + (int) (portrait.getOffsetY() * this.stageHeight), width, height};
    }

    private boolean onStage(double mouseX, double mouseY) {
        return mouseX >= this.stageX && mouseX < this.stageX + this.stageWidth
                && mouseY >= this.stageY && mouseY < this.stageY + this.stageHeight;
    }

    private PortraitInfo portraitAt(double mouseX, double mouseY) {
        List<PortraitInfo> portraits = this.portraits();
        for (int i = portraits.size() - 1; i >= 0; i--) {
            int[] box = this.portraitBox(portraits.get(i));
            if (box != null && mouseX >= box[0] && mouseX < box[0] + box[2] && mouseY >= box[1] && mouseY < box[1] + box[3]) {
                return portraits.get(i);
            }
        }
        return null;
    }

    // ----- drawing -----

    @Override
    protected void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(this.x(), this.y(), this.right(), this.bottom(), Theme.BG);
        graphics.fill(this.stageX, this.stageY, this.stageX + this.stageWidth, this.stageY + this.stageHeight, 0xFF06090A);
        if (this.entry == null) {
            Theme.centered(graphics, Theme.tr("inspector.hint").getString(), this.stageX + this.stageWidth / 2,
                    this.stageY + this.stageHeight / 2 - 4, Theme.TEXT_MUTED);
            return;
        }
        graphics.enableScissor(this.stageX, this.stageY, this.stageX + this.stageWidth, this.stageY + this.stageHeight);
        this.renderBackground(graphics);
        this.renderThirds(graphics);
        this.renderPortraits(graphics);
        this.renderDialogBox(graphics);
        graphics.disableScissor();
        Theme.border(graphics, this.stageX - 1, this.stageY - 1, this.stageWidth + 2, this.stageHeight + 2,
                Theme.BORDER_STRONG);
    }

    private void renderBackground(GuiGraphics graphics) {
        BackgroundImageInfo background = this.entry.getBackgroundImage();
        if (background == null || background.getPath() == null || background.getPath().isBlank()) {
            return;
        }
        AssetService.Handle handle = this.assets.computeIfAbsent("bg:" + background.getPath(),
                key -> AssetService.background(background.getPath()));
        if (handle.present()) {
            // The runtime stretches the background over the whole screen; the preview does the same.
            AssetService.blitStretched(graphics, handle, this.stageX, this.stageY, this.stageWidth, this.stageHeight);
        } else {
            Theme.centered(graphics, Theme.tr("stage.missing", background.getPath()).getString(),
                    this.stageX + this.stageWidth / 2, this.stageY + 6, Theme.WARNING);
        }
    }

    private void renderThirds(GuiGraphics graphics) {
        int color = 0x22FFFFFF;
        for (int i = 1; i <= 2; i++) {
            int x = this.stageX + this.stageWidth * i / 3;
            int y = this.stageY + this.stageHeight * i / 3;
            graphics.fill(x, this.stageY, x + 1, this.stageY + this.stageHeight, color);
            graphics.fill(this.stageX, y, this.stageX + this.stageWidth, y + 1, color);
        }
    }

    private void renderPortraits(GuiGraphics graphics) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        for (PortraitInfo portrait : this.portraits()) {
            int[] box = this.portraitBox(portrait);
            if (box == null) {
                continue;
            }
            float light = Mth.clamp(portrait.getBrightness(), 0.0f, 1.0f);
            RenderSystem.setShaderColor(light, light, light, 1.0f);
            graphics.blit(this.asset(portrait).location(), box[0], box[1], box[2], box[3], 0.0f, 0.0f,
                    box[2], box[3], box[2], box[3]);
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        }
        RenderSystem.disableBlend();
        if (this.selected != null) {
            int[] box = this.portraitBox(this.selected);
            if (box != null) {
                Theme.border(graphics, box[0], box[1], box[2], box[3], this.dragging ? Theme.ACCENT : Theme.CYAN);
                String info = String.format(Locale.ROOT, "%.2f  x%.2f y%.2f", this.selected.getSize(),
                        this.selected.getOffsetX(), this.selected.getOffsetY());
                Theme.text(graphics, info, box[0] + 2, Math.max(this.stageY + 2, box[1] + 2), Theme.ACCENT);
            } else {
                Theme.centered(graphics, Theme.tr("stage.missing", this.selected.getPath()).getString(),
                        this.stageX + this.stageWidth / 2, this.stageY + this.stageHeight / 2, Theme.WARNING);
            }
        }
    }

    /** Mirrors the runtime dialogue box so placement can be judged against it. */
    private void renderDialogBox(GuiGraphics graphics) {
        int boxWidth = Math.min(ClientConfig.DIALOG_BOX_WIDTH.get(), VIRTUAL_WIDTH - 20);
        int boxHeight = ClientConfig.DIALOG_BOX_HEIGHT.get();
        int x = this.stageX + this.scaled((VIRTUAL_WIDTH - boxWidth) / 2);
        int y = this.stageY + this.scaled(VIRTUAL_HEIGHT - boxHeight - 20);
        int width = this.scaled(boxWidth);
        int height = this.scaled(boxHeight);
        graphics.fill(x, y, x + width, y + height, 0xA0000000);
        Theme.border(graphics, x, y, width, height, Theme.ACCENT_DIM);
        String speaker = TextCodec.preview(this.entry.getSpeaker());
        int textY = y + 4;
        if (!speaker.isEmpty()) {
            Theme.text(graphics, Theme.ellipsize(speaker, width - 8), x + 4, textY, Theme.ACCENT);
            textY += 11;
        }
        for (String line : Wrap.dialogue(this.entry.getText(), Math.max(20, width - 8), 4)) {
            if (textY + 9 > y + height) {
                break;
            }
            Theme.text(graphics, line, x + 4, textY, Theme.TEXT);
            textY += 10;
        }
    }

    // ----- interaction -----

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.onStage(mouseX, mouseY) || this.entry == null) {
            return false;
        }
        this.host().focus(this);
        PortraitInfo hit = this.portraitAt(mouseX, mouseY);
        if (hit != this.selected) {
            this.selected = hit;
            this.rebuildSide();
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
        // Offsets are fractions of the screen, so a drag converts back through the stage size and
        // stays correct whatever the window or GUI scale is.
        this.selected.setOffsetX(this.selected.getOffsetX() + (float) ((mouseX - this.lastMouseX) / this.stageWidth));
        this.selected.setOffsetY(this.selected.getOffsetY() + (float) ((mouseY - this.lastMouseY) / this.stageHeight));
        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;
        this.syncSliders();
        this.context.touch(true);
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
            this.rebuildSide();
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
        float step = this.context.shiftDown() ? 0.005f : 0.02f;
        PortraitInfo portrait = this.selected;
        switch (keyCode) {
            case GLFW.GLFW_KEY_LEFT -> this.edit(() -> portrait.setOffsetX(portrait.getOffsetX() - step));
            case GLFW.GLFW_KEY_RIGHT -> this.edit(() -> portrait.setOffsetX(portrait.getOffsetX() + step));
            case GLFW.GLFW_KEY_UP -> this.edit(() -> portrait.setOffsetY(portrait.getOffsetY() - step));
            case GLFW.GLFW_KEY_DOWN -> this.edit(() -> portrait.setOffsetY(portrait.getOffsetY() + step));
            case GLFW.GLFW_KEY_R -> this.resetSelected();
            case GLFW.GLFW_KEY_DELETE -> this.removeSelected();
            default -> {
                return false;
            }
        }
        return true;
    }
}
