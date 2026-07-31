package top.yourzi.dialog.editor.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import top.yourzi.dialog.editor.gui.widget.EditorButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import top.yourzi.dialog.Dialog;
import top.yourzi.dialog.editor.gui.widget.DropdownWidget;
import top.yourzi.dialog.editor.gui.BuiltInTextureBrowserScreen;
import top.yourzi.dialog.editor.util.EditorConfig;
import top.yourzi.dialog.editor.util.EditorTheme;
import top.yourzi.dialog.editor.util.TextureCacheService;
import top.yourzi.dialog.model.PortraitAnimationType;
import top.yourzi.dialog.model.PortraitInfo;
import top.yourzi.dialog.model.PortraitPosition;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * 立绘列编辑屏幕：管理立绘路径、位置、动画类型、顺序。
 * 融合自 visual_mod_edit_vndialog，并适配 VNDialogReborn 新增的立绘动画类型（IMPACT/IMPACT_MAX/ROTATE/REVERSE/FLASH）。
 */
public class PortraitListScreen extends Screen {
    private static final int HEADER = 30;
    private static final int FOOTER = 50;
    private static final int LEFT_W = 130;
    private static final int ROW_H = 12;
    private static final int STAGE_X = 330;

    // 预览缩放参数：滚轮/按钮控制，范围 0.25-5.0（< 1.0 可缩小查看整体，> 1.0 可放大查看细节）。
    private static final float ZOOM_MIN = 0.25f;
    private static final float ZOOM_MAX = 5.0f;
    private static final float ZOOM_WHEEL_STEP = 0.2f;   // 滚轮每次滚动步长
    private static final float ZOOM_BTN_STEP = 0.25f;    // +/- 按钮步长
    // 缩放控件 UI 尺寸（舞台左上角的 [-] 1.50x [+] 控件条，避开右上角的文件夹按钮）
    private static final int ZOOM_CTRL_H = 14;
    private static final int ZOOM_CTRL_BTN_W = 16;       // +/- 按钮宽
    private static final int ZOOM_CTRL_LABEL_W = 40;     // 中间倍率文字宽（容纳 "1.50x" 等）

    /**
     * 舞台视口：把实际屏幕（this.width x this.height）等比缩放到舞台区域内。
     * 保持宽高比，让立绘、对话框、网格的相对位置与实际演出完全一致。
     * 立绘渲染、对话框参考框、九宫格全部基于此视口映射，杜绝 X/Y 缩放不一致导致的位置错位。
     */
    private static final class StageViewport {
        final int screenW;      // 实际屏幕宽
        final int screenH;      // 实际屏幕高
        final int viewX;        // 视口在舞台内的左上 X
        final int viewY;        // 视口在舞台内的左上 Y
        final int viewW;        // 视口宽（等比缩放后的屏幕宽）
        final int viewH;        // 视口高（等比缩放后的屏幕高）
        final float scale;      // 统一缩放比 = viewW / screenW = viewH / screenH

        StageViewport(int screenW, int screenH, int stageX, int stageY, int stageW, int stageH) {
            this.screenW = screenW;
            this.screenH = screenH;
            // 等比缩放：取宽高两个方向较小的缩放比，保证屏幕完整放入舞台
            float sx = (float) stageW / screenW;
            float sy = (float) stageH / screenH;
            this.scale = Math.min(sx, sy);
            this.viewW = (int) (screenW * this.scale);
            this.viewH = (int) (screenH * this.scale);
            // 视口在舞台内居中
            this.viewX = stageX + (stageW - this.viewW) / 2;
            this.viewY = stageY + (stageH - this.viewH) / 2;
        }

        /** 把实际屏幕 X 坐标映射到视口 X。 */
        int mapX(int screenX) {
            return viewX + (int) (screenX * scale);
        }

        /** 把实际屏幕 Y 坐标映射到视口 Y。 */
        int mapY(int screenY) {
            return viewY + (int) (screenY * scale);
        }

        /** 把实际屏幕尺寸（宽或高）映射到视口尺寸。 */
        int mapSize(int screenSize) {
            return (int) (screenSize * scale);
        }
    }

    /** 当前帧的舞台视口，render 开头计算一次，所有渲染共用。 */
    private StageViewport viewport;

    private final List<PortraitInfo> portraits;
    /** 原始立绘列表的深拷贝，取消时用于恢复未修改状态。 */
    private final List<PortraitInfo> originalPortraits;
    private final Consumer<List<PortraitInfo>> onSave;
    private final Screen parent;
    private int selectedIndex = -1;
    private boolean needsLayoutRefresh = true;
    private int scrollOffset = 0;
    /** 左列表滚动条拖拽 + 平滑滚动状态（借鉴 Sparkle OptionScreen）。 */
    private final top.yourzi.dialog.editor.gui.EditorRenderHelper.ScrollState scrollState = new top.yourzi.dialog.editor.gui.EditorRenderHelper.ScrollState();
    /** 上一帧纳秒时间戳，用于计算 dt 驱动平滑滚动。 */
    private long lastFrameNanos = 0L;
    private ResourceLocation previewTex = null;
    private String previewPath = null;
    private int previewW;
    private int previewH;
    private boolean draggingPortrait = false;
    private double lastDragX = 0;
    private double lastDragY = 0;
    /**
     * 预览缩放倍率，仅影响预览显示不影响实际 size。
     * 滚轮或 +/- 按钮调节，范围 ZOOM_MIN~ZOOM_MAX，默认 1.0。
     * 仅当 != 1.0f 时应用缩放变换（底部中心对齐放大）。
     *
     * 双模式缩放（用户可选用）：
     * - 默认（无 Ctrl）：纯预览缩放，类似 Photoshop 放大镜，不改变立绘实际 size。
     * - 按住 Ctrl：缩放变化量直接应用到立绘实际 size（info.setSize），
     *   实际对话中立绘会跟着变大/变小。此模式下 previewZoom 保持 1.0。
     */
    private float previewZoom = 1.0f;
    private DropdownWidget posDropdown;
    private DropdownWidget animDropdown;
    private EditBox sizeBox;
    private EditBox brightnessBox;
    private EditBox offsetXBox;
    private EditBox offsetYBox;
    private EditorButton delBtn;
    private EditorButton upBtn;
    private EditorButton downBtn;
    private EditorButton folderBtn;
    private EditorButton resetOffsetBtn;
    // 注意：POS_ITEMS 顺序必须与 PortraitPosition 枚举顺序一致（LEFT, CENTER, RIGHT），
    // 否则 POS_ITEMS.indexOf(selected) 映射到 POS_VALUES[idx] 会错位，导致选"右"实际设成 CENTER 等 bug。
    private static final List<String> POS_ITEMS = List.of(
            Component.translatable("gui.vn_edit.position.left").getString(),
            Component.translatable("gui.vn_edit.position.center").getString(),
            Component.translatable("gui.vn_edit.position.right").getString()
    );
    private static final PortraitPosition[] POS_VALUES = PortraitPosition.values();
    private static final List<String> ANIM_ITEMS = List.of(
            Component.translatable("gui.vn_edit.animation.none").getString(),
            Component.translatable("gui.vn_edit.animation.fade_in").getString(),
            Component.translatable("gui.vn_edit.animation.slide_in_from_bottom").getString(),
            Component.translatable("gui.vn_edit.animation.bounce").getString(),
            Component.translatable("gui.vn_edit.animation.impact").getString(),
            Component.translatable("gui.vn_edit.animation.impact_max").getString(),
            Component.translatable("gui.vn_edit.animation.rotate").getString(),
            Component.translatable("gui.vn_edit.animation.reverse").getString(),
            Component.translatable("gui.vn_edit.animation.flash").getString()
    );
    private static final PortraitAnimationType[] ANIM_VALUES = PortraitAnimationType.values();

    public PortraitListScreen(List<PortraitInfo> portraits, Consumer<List<PortraitInfo>> onSave, Screen parent) {
        super(Component.translatable("gui.vn_edit.portrait_list.title"));
        this.portraits = new ArrayList<>(portraits);
        // 深拷贝原始列表，取消编辑时用于恢复调用方的数据
        this.originalPortraits = deepCopyPortraits(portraits);
        this.onSave = onSave;
        this.parent = parent;
    }

    /** 深拷贝立绘列表，保证编辑过程中的修改不影响原始副本。 */
    private static List<PortraitInfo> deepCopyPortraits(List<PortraitInfo> src) {
        List<PortraitInfo> copy = new ArrayList<>();
        if (src == null) {
            return copy;
        }
        for (PortraitInfo p : src) {
            if (p == null) {
                continue;
            }
            copy.add(new PortraitInfo(p.getPath(), p.getPosition(), p.getBrightness(),
                    p.getAnimationType(), p.getSize(), p.getOffsetX(), p.getOffsetY()));
        }
        return copy;
    }

    @Override
    protected void init() {
        super.init();
        // 子屏（FileBrowserScreen/BuiltInTextureBrowserScreen）返回时 init() 会被重新调用，
        // 此时控件全部重建位置归零，必须强制刷新布局，否则取消返回后控件停在 (0,0)。
        // 输入框值由 PortraitInfo 实时同步（responder），重建时用 info 值设值不会丢数据。
        this.needsLayoutRefresh = true;
        this.addRenderableWidget(EditorButton.builder(Component.translatable("gui.vn_edit.add_portrait"), b -> this.openFileBrowser())
                .bounds(10, this.height - 25, 80, 20).build());
        this.addRenderableWidget(EditorButton.builder(Component.translatable("gui.vn_edit.builtin_portrait"), b -> this.openBuiltInBrowser())
                .bounds(95, this.height - 25, 60, 20).build());
        this.addRenderableWidget(EditorButton.builder(Component.translatable("gui.vn_edit.save"), b -> this.onSaveAndClose())
                .bounds(this.width / 2 - 105, this.height - 25, 100, 20).build());
        this.addRenderableWidget(EditorButton.builder(Component.translatable("gui.vn_edit.cancel"), b -> this.onCancelAndClose())
                .bounds(this.width / 2 + 5, this.height - 25, 100, 20).build());
        // 下拉框先创建但不加入渲染列表，最后再加入以确保弹出浮层渲染在输入框之上
        // （MC Screen.render 按 addRenderableWidget 顺序绘制，后添加的组件渲染在顶层）
        this.posDropdown = new DropdownWidget(this.font, 0, 0, 100, 16, new ArrayList<>(POS_ITEMS), selected -> {
            PortraitInfo info = this.getSelected();
            if (info != null) {
                int idx = POS_ITEMS.indexOf(selected);
                if (idx >= 0 && idx < POS_VALUES.length) {
                    info.setPosition(POS_VALUES[idx]);
                }
            }
        });
        this.animDropdown = new DropdownWidget(this.font, 0, 0, 100, 16, new ArrayList<>(ANIM_ITEMS), selected -> {
            PortraitInfo info = this.getSelected();
            if (info != null) {
                int idx = ANIM_ITEMS.indexOf(selected);
                if (idx >= 0 && idx < ANIM_VALUES.length) {
                    info.setAnimationType(ANIM_VALUES[idx]);
                }
            }
        });
        // 动画类型有 9 项，超过默认 MAX_VISIBLE=8，需显示全部避免闪光选项被滚动隐藏
        this.animDropdown.setMaxVisible(ANIM_ITEMS.size());
        this.sizeBox = this.addRenderableWidget(new EditBox(this.font, 0, 0, 80, 16, Component.translatable("gui.vn_edit.size")));
        this.sizeBox.setMaxLength(10);
        this.sizeBox.setResponder(s -> {
            PortraitInfo info = this.getSelected();
            if (info != null && !s.isEmpty()) {
                try {
                    float v = Float.parseFloat(s.trim());
                    info.setSize(Math.max(0.0f, Math.min(5.0f, v)));
                } catch (NumberFormatException ignored) {
                }
            }
        });
        this.brightnessBox = this.addRenderableWidget(new EditBox(this.font, 0, 0, 80, 16, Component.translatable("gui.vn_edit.brightness")));
        this.brightnessBox.setMaxLength(10);
        this.brightnessBox.setResponder(s -> {
            PortraitInfo info = this.getSelected();
            if (info != null && !s.isEmpty()) {
                try {
                    float v = Float.parseFloat(s.trim());
                    info.setBrightness(Math.max(0.0f, Math.min(1.0f, v)));
                } catch (NumberFormatException ignored) {
                }
            }
        });
        this.offsetXBox = this.addRenderableWidget(new EditBox(this.font, 0, 0, 80, 16, Component.translatable("gui.vn_edit.offset_x")));
        this.offsetXBox.setMaxLength(10);
        this.offsetXBox.setResponder(s -> {
            PortraitInfo info = this.getSelected();
            if (info != null && !s.isEmpty()) {
                try {
                    float v = Float.parseFloat(s.trim());
                    info.setOffsetX(v);
                } catch (NumberFormatException ignored) {
                }
            }
        });
        this.offsetYBox = this.addRenderableWidget(new EditBox(this.font, 0, 0, 80, 16, Component.translatable("gui.vn_edit.offset_y")));
        this.offsetYBox.setMaxLength(10);
        this.offsetYBox.setResponder(s -> {
            PortraitInfo info = this.getSelected();
            if (info != null && !s.isEmpty()) {
                try {
                    float v = Float.parseFloat(s.trim());
                    info.setOffsetY(v);
                } catch (NumberFormatException ignored) {
                }
            }
        });
        this.delBtn = this.addRenderableWidget(EditorButton.builder(Component.translatable("gui.vn_edit.delete"), b -> {
            if (this.selectedIndex >= 0 && this.selectedIndex < this.portraits.size()) {
                this.portraits.remove(this.selectedIndex);
                if (this.selectedIndex >= this.portraits.size()) {
                    this.selectedIndex = this.portraits.size() - 1;
                }
                this.needsLayoutRefresh = true;
                this.updatePreview();
            }
        }).bounds(0, 0, 60, 16).build());
        this.resetOffsetBtn = this.addRenderableWidget(EditorButton.builder(Component.translatable("gui.vn_edit.reset_offset"), b -> {
            PortraitInfo info = this.getSelected();
            if (info != null) {
                info.setOffsetX(0.0f);
                info.setOffsetY(0.0f);
                syncBoxIfNotFocused(this.offsetXBox, 0.0f);
                syncBoxIfNotFocused(this.offsetYBox, 0.0f);
            }
        }).bounds(0, 0, 38, 16).build());
        this.upBtn = this.addRenderableWidget(EditorButton.builder(Component.literal("\u25b2"), b -> {
            if (this.selectedIndex > 0 && this.selectedIndex < this.portraits.size()) {
                this.portraits.add(this.selectedIndex - 1, this.portraits.remove(this.selectedIndex));
                this.selectedIndex--;
                this.needsLayoutRefresh = true;
            }
        }).bounds(0, 0, 20, 16).build());
        this.downBtn = this.addRenderableWidget(EditorButton.builder(Component.literal("\u25bc"), b -> {
            if (this.selectedIndex >= 0 && this.selectedIndex < this.portraits.size() - 1) {
                this.portraits.add(this.selectedIndex + 1, this.portraits.remove(this.selectedIndex));
                this.selectedIndex++;
                this.needsLayoutRefresh = true;
            }
        }).bounds(0, 0, 20, 16).build());
        this.folderBtn = this.addRenderableWidget(EditorButton.builder(Component.literal("\uD83D\uDCC2"), b -> EditorConfig.openFolder(EditorConfig.PORTRAITS_DIR))
                .bounds(this.width - 30, 25, 25, 16).build());
        // 下拉框最后加入渲染列表，使其弹出浮层渲染在所有输入框之上，避免浮层被输入框覆盖
        this.addRenderableWidget(this.posDropdown);
        this.addRenderableWidget(this.animDropdown);
        this.updateDynamicButtons();
    }

    private PortraitInfo getSelected() {
        if (this.selectedIndex >= 0 && this.selectedIndex < this.portraits.size()) {
            return this.portraits.get(this.selectedIndex);
        }
        return null;
    }

    private void openFileBrowser() {
        FileBrowserScreen.open(EditorConfig.PORTRAITS_DIR.toFile(), new String[]{"png", "jpg", "jpeg"}, path -> {
            String lower = path.toLowerCase(Locale.ROOT);
            boolean exists = this.portraits.stream().anyMatch(p -> p.getPath() != null && p.getPath().equalsIgnoreCase(lower));
            if (!exists) {
                this.portraits.add(new PortraitInfo(lower, PortraitPosition.RIGHT, 1.0f, PortraitAnimationType.NONE));
                this.selectedIndex = this.portraits.size() - 1;
                this.needsLayoutRefresh = true;
                this.updatePreview();
            }
        }, this);
    }

    private void openBuiltInBrowser() {
        Minecraft.getInstance().setScreen(new BuiltInTextureBrowserScreen("textures/portraits/", path -> {
            String lower = path.toLowerCase(Locale.ROOT);
            boolean exists = this.portraits.stream().anyMatch(p -> p.getPath() != null && p.getPath().equalsIgnoreCase(lower));
            if (!exists) {
                this.portraits.add(new PortraitInfo(lower, PortraitPosition.RIGHT, 1.0f, PortraitAnimationType.NONE));
                this.selectedIndex = this.portraits.size() - 1;
                this.needsLayoutRefresh = true;
                this.updatePreview();
            }
        }, Minecraft.getInstance().screen));
    }

    /** 清空当前预览引用（纹理由静态缓存管理，切换/关闭时不释放）。 */
    private void clearPreviewRef() {
        this.previewTex = null;
        this.previewPath = null;
        this.previewW = 0;
        this.previewH = 0;
    }

    /**
     * 释放预览纹理现由 TextureCacheService 统一管理，编辑器关闭时由主屏调用 releaseAll()。
     */

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        this.renderBackground(g, mx, my, pt);
        g.drawCenteredString(this.font, this.title, this.width / 2, 10, EditorTheme.TEXT_PRIMARY);
        int contentH = this.height - HEADER - FOOTER;
        // 计算本帧舞台视口：把实际屏幕等比映射到舞台区域，所有渲染共用此视口
        this.viewport = new StageViewport(this.width, this.height, STAGE_X, HEADER, stageWidth(), contentH);
        this.renderLeftList(g, mx, my, contentH);
        this.renderMiddlePanel(g, mx, my, contentH);
        // 舞台背景在 widget 之前绘制（作为底层背景）
        this.renderStageBackground(g);
        this.updateDynamicButtons();
        super.render(g, mx, my, pt);
        // 立绘 blit 必须在 super.render() 之后绘制：本屏是独立 Screen，
        // 立绘 blit 若在 super.render() 之前调用会被 GuiGraphics 缓冲，随后渲染 widget 时
        // 批量 flush 会把 DynamicTexture 纹理绑定与其它 RenderType 混在一起导致不可见。
        this.renderPortraitBlit(g, mx, my);
        // 对话框参考框在立绘之后绘制，避免被立绘图片覆盖而看不见
        this.renderDialogBoxGuide(g);
        // 下拉浮层已由 DropdownWidget.renderWidget 自包含渲染，无需手动调用。
    }

    /** 舞台宽度（右侧预览区）。 */
    private int stageWidth() {
        return Math.max(80, this.width - STAGE_X - 10);
    }

    /**
     * 绘制舞台背景与边框（底层，在 widget 之前）。
     * 舞台外层用 BG_SURFACE 填充整个区域；视口区域（实际屏幕等比映射）用更深色填充并画边框，
     * 让用户清楚看到"实际屏幕"在舞台中的范围。九宫格和对话框参考框都画在视口内。
     */
    private void renderStageBackground(GuiGraphics g) {
        int x = STAGE_X;
        int y = HEADER;
        int w = stageWidth();
        int h = this.height - HEADER - FOOTER;
        // 舞台外层背景
        g.fill(x, y, x + w, y + h, EditorTheme.BG_SURFACE);
        // 视口区域（实际屏幕等比映射区）：用更深的颜色区分，并画边框
        g.fill(this.viewport.viewX, this.viewport.viewY,
               this.viewport.viewX + this.viewport.viewW, this.viewport.viewY + this.viewport.viewH,
               0xFF101010);
        int frameColor = EditorTheme.BORDER_LIGHT;
        g.fill(this.viewport.viewX, this.viewport.viewY,
               this.viewport.viewX + this.viewport.viewW, this.viewport.viewY + 1, frameColor);
        g.fill(this.viewport.viewX, this.viewport.viewY + this.viewport.viewH - 1,
               this.viewport.viewX + this.viewport.viewW, this.viewport.viewY + this.viewport.viewH, frameColor);
        g.fill(this.viewport.viewX, this.viewport.viewY,
               this.viewport.viewX + 1, this.viewport.viewY + this.viewport.viewH, frameColor);
        g.fill(this.viewport.viewX + this.viewport.viewW - 1, this.viewport.viewY,
               this.viewport.viewX + this.viewport.viewW, this.viewport.viewY + this.viewport.viewH, frameColor);
        // 舞台外边框
        g.fill(x, y, x + w, y + 1, EditorTheme.BG_ELEVATED);
        g.fill(x, y + h - 1, x + w, y + h, EditorTheme.BG_ELEVATED);
        g.fill(x, y, x + 1, y + h, EditorTheme.BG_ELEVATED);
        g.fill(x + w - 1, y, x + w, y + h, EditorTheme.BG_ELEVATED);
        // 九宫格辅助线画在视口内（实际屏幕的三分线）
        renderRuleOfThirdsGrid(g);
        // 对话框参考框不在此处绘制：立绘 blit 在 super.render 之后绘制，
        // 会覆盖此处画的对话框框。对话框框改到 renderPortraitBlit 之后绘制，确保可见。
    }

    /**
     * 绘制九宫格辅助线（三分线）：在视口内画两条竖线、两条横线，将实际屏幕均分为 3x3。
     * 基于视口映射，与实际屏幕比例一致。1px 半透明白色，仅作对齐辅助不干扰主视觉。
     */
    private void renderRuleOfThirdsGrid(GuiGraphics g) {
        int gridColor = 0x33FFFFFF;
        // 三分线在实际屏幕坐标的 1/3、2/3 处，映射到视口
        int x1 = this.viewport.mapX(this.width / 3);
        int x2 = this.viewport.mapX(this.width * 2 / 3);
        int y1 = this.viewport.mapY(this.height / 3);
        int y2 = this.viewport.mapY(this.height * 2 / 3);
        int top = this.viewport.viewY;
        int bot = this.viewport.viewY + this.viewport.viewH;
        int left = this.viewport.viewX;
        int right = this.viewport.viewX + this.viewport.viewW;
        // 竖线
        g.fill(x1, top, x1 + 1, bot, gridColor);
        g.fill(x2, top, x2 + 1, bot, gridColor);
        // 横线
        g.fill(left, y1, right, y1 + 1, gridColor);
        g.fill(left, y2, right, y2 + 1, gridColor);
    }

    /**
     * 在视口内按实际演出位置绘制对话框参考框。
     * 实际演出中对话框：宽=min(DIALOG_BOX_WIDTH, width-20)，高=DIALOG_BOX_HEIGHT，
     * X=(width-boxW)/2，Y=height-boxH-20。通过视口等比映射到舞台，位置与实际演出完全一致。
     * 框内绘制示意文字和内边距参考线，模拟真实对话框样式。
     */
    private void renderDialogBoxGuide(GuiGraphics g) {
        int cfgBoxW = top.yourzi.dialog.config.ClientConfig.DIALOG_BOX_WIDTH.get();
        int cfgBoxH = top.yourzi.dialog.config.ClientConfig.DIALOG_BOX_HEIGHT.get();
        int cfgPad = top.yourzi.dialog.config.ClientConfig.DIALOG_BOX_PADDING.get();
        // 实际屏幕对应的对话框宽高（与 DialogScreen 完全一致）
        int realBoxW = Math.min(cfgBoxW, this.width - 20);
        int realBoxH = cfgBoxH;
        // 实际演出位置
        int realBoxX = (this.width - realBoxW) / 2;
        int realBoxY = this.height - realBoxH - 20;
        // 映射到视口
        int boxX = this.viewport.mapX(realBoxX);
        int boxY = this.viewport.mapY(realBoxY);
        int boxW = this.viewport.mapSize(realBoxW);
        int boxH = this.viewport.mapSize(realBoxH);
        // 对话框底色：深色半透明（模拟实际对话框底色）
        g.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0xCC000000);
        // 醒目边框（强调色 2px 厚）
        int borderColor = EditorTheme.ACCENT;
        g.fill(boxX, boxY, boxX + boxW, boxY + 2, borderColor);
        g.fill(boxX, boxY + boxH - 2, boxX + boxW, boxY + boxH, borderColor);
        g.fill(boxX, boxY, boxX + 2, boxY + boxH, borderColor);
        g.fill(boxX + boxW - 2, boxY, boxX + boxW, boxY + boxH, borderColor);
        // 内边距参考线（虚线感）：实际 padding 映射到视口
        int pad = Math.max(2, this.viewport.mapSize(cfgPad));
        int padColor = 0x40FFFFFF;
        g.fill(boxX + pad, boxY + pad, boxX + boxW - pad, boxY + pad + 1, padColor);
        g.fill(boxX + pad, boxY + boxH - pad - 1, boxX + boxW - pad, boxY + boxH - pad, padColor);
        g.fill(boxX + pad, boxY + pad, boxX + pad + 1, boxY + boxH - pad, padColor);
        g.fill(boxX + boxW - pad - 1, boxY + pad, boxX + boxW - pad, boxY + boxH - pad, padColor);
        // 框内标注：仅当框足够大时显示，避免小框文字溢出
        if (boxW > 60 && boxH > 20) {
            g.drawCenteredString(this.font, Component.translatable("gui.vn_edit.dialog_box_preview"),
                    boxX + boxW / 2, boxY + boxH / 2 - 4, 0xFFFFFFFF);
        }
    }

    /**
     * 绘制立绘 blit 与提示文字（在所有 widget 之后）。
     * 立绘渲染逻辑与 DialogScreen.renderPortraits 完全对齐，先在实际屏幕坐标系计算位置，
     * 再通过视口统一映射到舞台，保证位置与实际演出 100% 一致：
     * - 高度 = 屏幕高 * 0.68 * size
     * - X 按 position（LEFT/CENTER/RIGHT）定位，侧边距 20px
     * - Y 底部对齐（立绘站在屏幕底部）
     * - offset 相对屏幕尺寸
     *
     * 预览缩放（previewZoom）仅影响预览显示，不修改实际 size/offset。
     * 缩放以立绘底部中心为基点放大/缩小。
     * 立绘绘制用 scissor 裁剪到舞台区域，避免放大后溢出到左侧列表/中间面板。
     */
    private void renderPortraitBlit(GuiGraphics g, int mx, int my) {
        int stageX = STAGE_X;
        int stageY = HEADER;
        int stageW = stageWidth();
        int stageH = this.height - HEADER - FOOTER;
        PortraitInfo info = this.getSelected();
        if (this.previewTex != null && info != null) {
            float size = Mth.clamp(info.getSize(), 0.1f, 5.0f);
            // 先在实际屏幕坐标系计算立绘尺寸（与 DialogScreen 完全一致）
            int realPortraitH = (int) (this.height * 0.68f * size);
            float ratio = (this.previewW > 0 && this.previewH > 0)
                    ? (float) this.previewW / (float) this.previewH
                    : 1.0f;
            int realPortraitW = Math.max(1, (int) (realPortraitH * ratio));
            // 实际屏幕坐标的立绘基准位置（与 DialogScreen.renderPortraits 一致）
            int realBaseX = switch (info.getPosition()) {
                case LEFT -> 20;                                    // PORTRAIT_SIDE_MARGIN
                case CENTER -> (this.width - realPortraitW) / 2;
                case RIGHT -> this.width - realPortraitW - 20;
            };
            int realBaseY = this.height - realPortraitH;            // 底部对齐
            // offset 相对实际屏幕尺寸（与 DialogScreen 一致）
            int realRenderX = realBaseX + (int) (info.getOffsetX() * this.width);
            int realRenderY = realBaseY + (int) (info.getOffsetY() * this.height);
            // 通过视口映射到舞台（统一等比缩放，与对话框参考框用同一套映射）
            int renderX = this.viewport.mapX(realRenderX);
            int renderY = this.viewport.mapY(realRenderY);
            int portraitW = this.viewport.mapSize(realPortraitW);
            int portraitH = this.viewport.mapSize(realPortraitH);
            // 应用预览缩放：以立绘底边为基点放大/缩小，仅影响预览显示不影响实际 size。
            // X 方向按 position 决定锚点（立绘靠边的那一侧位置不变，避免放大后整体平移）：
            //   LEFT 左边不动向右扩展、RIGHT 右边不动向左扩展、CENTER 中心不动两侧扩展
            // Y 方向底边对齐：立绘脚部位置不变，向上扩展
            if (this.previewZoom != 1.0f) {
                int scaledW = Math.max(1, (int) (portraitW * this.previewZoom));
                int scaledH = Math.max(1, (int) (portraitH * this.previewZoom));
                switch (info.getPosition()) {
                    case LEFT -> { /* renderX 不变，向右扩展 */ }
                    case CENTER -> renderX = renderX + (portraitW - scaledW) / 2;
                    case RIGHT -> renderX = renderX + (portraitW - scaledW);
                }
                renderY = renderY + (portraitH - scaledH);
                portraitW = scaledW;
                portraitH = scaledH;
            }
            if (portraitW < 1) portraitW = 1;
            if (portraitH < 1) portraitH = 1;
            // scissor 裁剪到舞台区域，避免放大后立绘溢出到左侧列表与中间面板
            g.enableScissor(stageX, stageY, stageX + stageW, stageY + stageH);
            // 渲染立绘
            RenderSystem.setShaderTexture(0, this.previewTex);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            g.blit(this.previewTex, renderX, renderY, 0.0f, 0.0f, portraitW, portraitH, portraitW, portraitH);
            RenderSystem.disableBlend();
            // 拖动时显示十字辅助线（PPT 智能参考线）：从立绘中心向视口四边延伸
            if (this.draggingPortrait) {
                int centerX = renderX + portraitW / 2;
                int centerY = renderY + portraitH / 2;
                int crossColor = 0x804A9EFF;
                int left = this.viewport.viewX;
                int right = this.viewport.viewX + this.viewport.viewW;
                int top = this.viewport.viewY;
                int bot = this.viewport.viewY + this.viewport.viewH;
                // 横线：贯穿视口左右
                g.fill(left, centerY, right, centerY + 1, crossColor);
                // 竖线：贯穿视口上下
                g.fill(centerX, top, centerX + 1, bot, crossColor);
                // 立绘外框高亮：强调色 1px 边框，明确显示立绘当前边界
                int boxColor = EditorTheme.ACCENT;
                g.fill(renderX, renderY, renderX + portraitW, renderY + 1, boxColor);
                g.fill(renderX, renderY + portraitH - 1, renderX + portraitW, renderY + portraitH, boxColor);
                g.fill(renderX, renderY, renderX + 1, renderY + portraitH, boxColor);
                g.fill(renderX + portraitW - 1, renderY, renderX + portraitW, renderY + portraitH, boxColor);
            }
            g.disableScissor();
            // 顶部提示文字（在 scissor 外，确保不被裁剪）
            // Y 定位在缩放控件下方，避免与左上角的 [-] 倍率 [+] 控件重叠
            int hintY = stageY + ZOOM_CTRL_H + 6;
            if (this.draggingPortrait) {
                g.drawCenteredString(this.font, Component.translatable("gui.vn_edit.drag_hint"), stageX + stageW / 2, hintY, EditorTheme.ACCENT);
                // offset 数值贴近立绘上方显示
                String offsetText = String.format(java.util.Locale.ROOT, "X: %.2f  Y: %.2f", info.getOffsetX(), info.getOffsetY());
                int textX = renderX + portraitW / 2;
                int textY = Math.max(hintY, renderY - 11);
                g.drawCenteredString(this.font, Component.literal(offsetText), textX, textY, EditorTheme.ACCENT);
            } else {
                g.drawCenteredString(this.font, Component.translatable("gui.vn_edit.drag_to_adjust"), stageX + stageW / 2, hintY, EditorTheme.TEXT_MUTED);
            }
            // 缩放控件 UI（舞台左上角，避开右上角文件夹按钮）
            renderZoomControl(g, mx, my, stageX, stageY, stageW);
        } else if (info == null) {
            g.drawCenteredString(this.font, Component.translatable("gui.vn_edit.no_portrait_selected"), stageX + stageW / 2, stageY + stageH / 2 - 4, EditorTheme.TEXT_MUTED);
        } else {
            g.drawCenteredString(this.font, Component.translatable("gui.vn_edit.no_preview"), stageX + stageW / 2, stageY + stageH / 2 - 4, EditorTheme.TEXT_MUTED);
        }
    }

    /**
     * 缩放控件 UI：舞台左上角的 [-] 倍率 [+] 控件条（避开右上角的文件夹按钮）。
     * - [-] 减小（步长 ZOOM_BTN_STEP）
     * - [+] 增大（步长 ZOOM_BTN_STEP）
     * - 中间倍率文字可点击：单击重置预览缩放
     *
     * 双模式（Ctrl 切换）：
     * - 默认：中间显示预览倍率（如 1.50x），蓝色，仅影响预览
     * - Ctrl 按住：中间显示立绘实际 size（如 S=1.20），橙色，缩放直接改 size 影响实际对话
     * 控件背景在 Ctrl 模式下边框变橙，让用户明确感知当前模式。
     */
    private void renderZoomControl(GuiGraphics g, int mx, int my, int stageX, int stageY, int stageW) {
        int totalW = ZOOM_CTRL_BTN_W + ZOOM_CTRL_LABEL_W + ZOOM_CTRL_BTN_W;
        // 左上角，避开右上角 folderBtn
        int cx = stageX + 4;
        int cy = stageY + 4;
        boolean ctrlSize = hasControlDown();
        int modeColor = ctrlSize ? EditorTheme.DANGER : EditorTheme.ACCENT;
        // 背景胶囊（半透明深色 + 模式色边框）
        g.fill(cx - 1, cy - 1, cx + totalW + 1, cy + ZOOM_CTRL_H + 1, 0xE6000000);
        g.fill(cx, cy, cx + totalW, cy + ZOOM_CTRL_H, EditorTheme.BG_ELEVATED);
        // 模式边框（1px，Ctrl 时橙色，否则蓝色；非默认状态才画边框以提示模式）
        if (ctrlSize || this.previewZoom != 1.0f) {
            g.fill(cx, cy, cx + totalW, cy + 1, modeColor);
            g.fill(cx, cy + ZOOM_CTRL_H - 1, cx + totalW, cy + ZOOM_CTRL_H, modeColor);
            g.fill(cx, cy, cx + 1, cy + ZOOM_CTRL_H, modeColor);
            g.fill(cx + totalW - 1, cy, cx + totalW, cy + ZOOM_CTRL_H, modeColor);
        }
        // 三个区域的 bounds
        int minusX = cx;
        int labelX = cx + ZOOM_CTRL_BTN_W;
        int plusX = cx + ZOOM_CTRL_BTN_W + ZOOM_CTRL_LABEL_W;
        boolean hoverMinus = isMouseInRect(mx, my, minusX, cy, ZOOM_CTRL_BTN_W, ZOOM_CTRL_H);
        boolean hoverLabel = isMouseInRect(mx, my, labelX, cy, ZOOM_CTRL_LABEL_W, ZOOM_CTRL_H);
        boolean hoverPlus = isMouseInRect(mx, my, plusX, cy, ZOOM_CTRL_BTN_W, ZOOM_CTRL_H);
        // 悬停高亮
        if (hoverMinus) g.fill(minusX, cy, minusX + ZOOM_CTRL_BTN_W, cy + ZOOM_CTRL_H, EditorTheme.BG_HOVER);
        if (hoverLabel) g.fill(labelX, cy, labelX + ZOOM_CTRL_LABEL_W, cy + ZOOM_CTRL_H, EditorTheme.BG_HOVER);
        if (hoverPlus) g.fill(plusX, cy, plusX + ZOOM_CTRL_BTN_W, cy + ZOOM_CTRL_H, EditorTheme.BG_HOVER);
        // 分隔线
        int lineColor = EditorTheme.BORDER;
        g.fill(labelX, cy, labelX + 1, cy + ZOOM_CTRL_H, lineColor);
        g.fill(plusX, cy, plusX + 1, cy + ZOOM_CTRL_H, lineColor);
        // 文字
        int textY = cy + (ZOOM_CTRL_H - this.font.lineHeight) / 2 + 1;
        int minusColor = hoverMinus ? EditorTheme.ACCENT : EditorTheme.TEXT_PRIMARY;
        int plusColor = hoverPlus ? EditorTheme.ACCENT : EditorTheme.TEXT_PRIMARY;
        g.drawCenteredString(this.font, Component.literal("-"), minusX + ZOOM_CTRL_BTN_W / 2, textY, minusColor);
        // 中间标签：Ctrl 模式显示实际 size，否则显示预览倍率
        String labelText;
        int labelColor;
        if (ctrlSize) {
            PortraitInfo info = this.getSelected();
            float size = info != null ? info.getSize() : 1.0f;
            labelText = String.format(java.util.Locale.ROOT, "S=%.2f", size);
            labelColor = EditorTheme.DANGER;
        } else {
            labelText = String.format(java.util.Locale.ROOT, "%.2fx", this.previewZoom);
            labelColor = this.previewZoom == 1.0f ? EditorTheme.TEXT_SECONDARY : EditorTheme.ACCENT;
        }
        g.drawCenteredString(this.font, Component.literal(labelText), labelX + ZOOM_CTRL_LABEL_W / 2, textY, labelColor);
        g.drawCenteredString(this.font, Component.literal("+"), plusX + ZOOM_CTRL_BTN_W / 2, textY, plusColor);
    }

    /** 返回缩放控件三个可点击区域的 bounds：[minus, label, plus]，每个为 {x, y, w, h}。左上角对齐。 */
    private int[][] getZoomControlBounds(int stageX, int stageY, int stageW) {
        int totalW = ZOOM_CTRL_BTN_W + ZOOM_CTRL_LABEL_W + ZOOM_CTRL_BTN_W;
        int cx = stageX + 4;
        int cy = stageY + 4;
        return new int[][]{
                {cx, cy, ZOOM_CTRL_BTN_W, ZOOM_CTRL_H},                                     // minus
                {cx + ZOOM_CTRL_BTN_W, cy, ZOOM_CTRL_LABEL_W, ZOOM_CTRL_H},                 // label
                {cx + ZOOM_CTRL_BTN_W + ZOOM_CTRL_LABEL_W, cy, ZOOM_CTRL_BTN_W, ZOOM_CTRL_H} // plus
        };
    }

    /**
     * 重置缩放：恢复选中立绘到图片默认状态（size=1.0、offset=0、0.0），
     * 同时把预览缩放 previewZoom 归位到 1.0。
     * 原实现仅重置 previewZoom=1.0，若用户从未改过预览缩放则无可见效果，故"没用"。
     * 现改为恢复实际 size/offset 到默认值：滚轮直接改 size 后，R 能明显回退到默认。
     */
    private void resetZoom() {
        this.previewZoom = 1.0f;
        PortraitInfo info = this.getSelected();
        if (info == null) {
            return;
        }
        info.setSize(1.0f);
        info.setOffsetX(0.0f);
        info.setOffsetY(0.0f);
        syncBoxIfNotFocused(this.sizeBox, info.getSize());
        syncBoxIfNotFocused(this.offsetXBox, info.getOffsetX());
        syncBoxIfNotFocused(this.offsetYBox, info.getOffsetY());
    }

    private void updateDynamicButtons() {
        PortraitInfo info = this.getSelected();
        boolean visible = info != null;
        // 可见性每帧更新（开销极小，且列表增删时需及时响应）
        this.posDropdown.visible = visible;
        this.animDropdown.visible = visible;
        this.sizeBox.visible = visible;
        this.brightnessBox.visible = visible;
        this.offsetXBox.visible = visible;
        this.offsetYBox.visible = visible;
        this.delBtn.visible = visible;
        this.upBtn.visible = visible && this.selectedIndex > 0;
        this.downBtn.visible = visible && this.selectedIndex < this.portraits.size() - 1;
        this.resetOffsetBtn.visible = visible;
        // 仅在选中项变化时刷新布局和值，避免每帧覆盖用户输入
        if (visible && this.needsLayoutRefresh) {
            this.needsLayoutRefresh = false;
            // 位置/动画下拉框并排放置（同一行），水平错开，弹出菜单不再互相覆盖；
            // 下方输入框自上而下整齐排列，标签在左、输入框在右。
            this.posDropdown.setX(145);
            this.posDropdown.setY(42);
            this.posDropdown.setWidth(82);
            this.posDropdown.setPopupAbove(false);
            this.posDropdown.setSelected(this.getPositionDisplay(info.getPosition()).getString());
            this.animDropdown.setX(233);
            this.animDropdown.setY(42);
            this.animDropdown.setWidth(82);
            this.animDropdown.setPopupAbove(false);
            this.animDropdown.setSelected(this.getAnimationDisplay(info.getAnimationType()).getString());
            layoutFloatBox(this.sizeBox, 70);
            layoutFloatBox(this.brightnessBox, 95);
            layoutFloatBox(this.offsetXBox, 120);
            layoutFloatBox(this.offsetYBox, 145);
            this.resetOffsetBtn.setX(282);
            this.resetOffsetBtn.setY(145);
            this.delBtn.setX(145);
            this.delBtn.setY(175);
            this.upBtn.setX(255);
            this.upBtn.setY(175);
            this.downBtn.setX(285);
            this.downBtn.setY(175);
            this.sizeBox.setValue(String.format(java.util.Locale.ROOT, "%.2f", info.getSize()));
            this.brightnessBox.setValue(String.format(java.util.Locale.ROOT, "%.2f", info.getBrightness()));
            this.offsetXBox.setValue(String.format(java.util.Locale.ROOT, "%.2f", info.getOffsetX()));
            this.offsetYBox.setValue(String.format(java.util.Locale.ROOT, "%.2f", info.getOffsetY()));
        }
    }

    /** 仅设置输入框位置，不动值与 responder。 */
    private void layoutFloatBox(EditBox box, int y) {
        box.setX(200);
        box.setY(y);
    }

    private void renderLeftList(GuiGraphics g, int mx, int my, int contentH) {
        int x = 10;
        int y = HEADER;
        int w = LEFT_W;
        int h = contentH;
        g.fill(x, y, x + w, y + h, EditorTheme.BG_SURFACE);
        g.enableScissor(x, y, x + w, y + h);
        int maxScroll = Math.max(0, this.portraits.size() * ROW_H - h);
        this.scrollOffset = Mth.clamp(this.scrollOffset, 0, maxScroll);
        // 计算 dt 驱动平滑滚动（首帧 lastFrameNanos=0 直接吸附）
        long now = System.nanoTime();
        float dt = this.lastFrameNanos == 0L ? 0f : Math.min(0.1f, (now - this.lastFrameNanos) / 1.0e9f);
        this.lastFrameNanos = now;
        int displayOffset = this.scrollState.tick(this.scrollOffset, dt);
        int curY = y - displayOffset;
        for (int i = 0; i < this.portraits.size(); i++) {
            PortraitInfo info = this.portraits.get(i);
            int rowY = curY + i * ROW_H;
            if (rowY + ROW_H < y || rowY > y + h) {
                continue;
            }
            boolean hover = isMouseInRect(mx, my, x, rowY, w, ROW_H);
            int bg = i == this.selectedIndex ? EditorTheme.BG_SELECTED : (hover ? EditorTheme.BG_HOVER : 0);
            g.fill(x, rowY, x + w, rowY + ROW_H, bg);
            // 选中项左侧 2px 强调色竖条（VS Code 活动标签风格），视觉锚点更明确
            if (i == this.selectedIndex) {
                g.fill(x, rowY, x + 2, rowY + ROW_H, EditorTheme.ACCENT);
            }
            String name = info.getPath() != null ? info.getPath() : "";
            String trimmed = this.font.plainSubstrByWidth(name, w - 10);
            g.drawString(this.font, trimmed, x + 4, rowY + 2, EditorTheme.TEXT_PRIMARY);
        }
        // 空状态：无立绘时居中提示（借鉴 Sparkle 三态列表）
        if (this.portraits.isEmpty()) {
            g.drawCenteredString(this.font, Component.translatable("gui.vn_edit.portrait.empty"),
                    x + w / 2, y + h / 2 - 4, EditorTheme.TEXT_MUTED);
        }
        g.disableScissor();
        if (maxScroll > 0) {
            int sbH = Math.max(10, h * h / (this.portraits.size() * ROW_H));
            int sbY = y + (int) ((float) displayOffset / (float) maxScroll * (float) (h - sbH));
            g.fill(x + w - 4, y, x + w, y + h, 0x33FFFFFF);
            int thumbColor = this.scrollState.dragging ? 0xFFFFFFFF : EditorTheme.TEXT_MUTED;
            g.fill(x + w - 4, sbY, x + w, sbY + sbH, thumbColor);
        }
    }

    private void renderMiddlePanel(GuiGraphics g, int mx, int my, int contentH) {
        int x = 140;
        int y = HEADER;
        int w = 180;
        int h = contentH;
        g.fill(x, y, x + w, y + h, EditorTheme.BG_ELEVATED);
        if (this.selectedIndex < 0 || this.selectedIndex >= this.portraits.size()) {
            g.drawCenteredString(this.font, Component.translatable("gui.vn_edit.no_portrait_selected"), x + w / 2, y + 20, EditorTheme.TEXT_MUTED);
            return;
        }
        // 位置/动画下拉框上方的小标签（下拉框并排于 y=42）
        g.drawString(this.font, this.fitLabel(Component.translatable("gui.vn_edit.position"), 82), x + 5, 32, EditorTheme.TEXT_SECONDARY);
        g.drawString(this.font, this.fitLabel(Component.translatable("gui.vn_edit.animation"), 82), x + 93, 32, EditorTheme.TEXT_SECONDARY);
        // 下方输入框标签（输入框 y 分别为 70/95/120/145，标签 y 对齐输入框垂直中心）
        // 标签区域宽度 = 输入框 x(200) - 标签 x(145) - 2px 间隙 = 53px，超宽截断防溢入输入框
        int labelMaxW = 200 - (x + 5) - 2;
        g.drawString(this.font, this.fitLabel(Component.translatable("gui.vn_edit.size"), labelMaxW), x + 5, 74, EditorTheme.TEXT_SECONDARY);
        g.drawString(this.font, this.fitLabel(Component.translatable("gui.vn_edit.brightness"), labelMaxW), x + 5, 99, EditorTheme.TEXT_SECONDARY);
        g.drawString(this.font, this.fitLabel(Component.translatable("gui.vn_edit.offset_x"), labelMaxW), x + 5, 124, EditorTheme.TEXT_SECONDARY);
        g.drawString(this.font, this.fitLabel(Component.translatable("gui.vn_edit.offset_y"), labelMaxW), x + 5, 149, EditorTheme.TEXT_SECONDARY);
    }

    private boolean isMouseInStage(double mx, double my) {
        int x = STAGE_X;
        int y = HEADER;
        int w = stageWidth();
        int h = this.height - HEADER - FOOTER;
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    /** 截断标签文字至指定像素宽度，防止长标签溢入相邻输入框。 */
    private Component fitLabel(Component label, int maxWidth) {
        String text = label.getString();
        if (this.font.width(text) <= maxWidth) {
            return label;
        }
        return Component.literal(this.font.plainSubstrByWidth(text, maxWidth - 6) + "...");
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 如果下拉框已展开，优先处理
        if (this.posDropdown.isExpanded()) {
            if (this.posDropdown.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            this.posDropdown.close();
            return true;
        }
        if (this.animDropdown.isExpanded()) {
            if (this.animDropdown.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            this.animDropdown.close();
            return true;
        }
        // 缩放控件点击：[-] / [+] / 中间倍率文字（单击文字重置缩放）
        // 仅在选中立绘且有预览时响应，左键生效
        // 默认直接点 +/- 修改立绘实际 size（与滚轮一致）；Ctrl+点 +/- 仅预览缩放
        if (button == 0 && this.getSelected() != null && this.previewTex != null) {
            int stageW = stageWidth();
            int[][] zb = getZoomControlBounds(STAGE_X, HEADER, stageW);
            int[] minusB = zb[0], labelB = zb[1], plusB = zb[2];
            boolean ctrlSize = hasControlDown();
            if (isMouseInRect(mouseX, mouseY, minusB[0], minusB[1], minusB[2], minusB[3])) {
                if (ctrlSize) {
                    this.previewZoom = Mth.clamp(this.previewZoom - ZOOM_BTN_STEP, ZOOM_MIN, ZOOM_MAX);
                } else {
                    PortraitInfo info = this.getSelected();
                    if (info != null) {
                        info.setSize(Mth.clamp(info.getSize() - ZOOM_BTN_STEP, 0.1f, 5.0f));
                        syncBoxIfNotFocused(this.sizeBox, info.getSize());
                    }
                }
                return true;
            }
            if (isMouseInRect(mouseX, mouseY, plusB[0], plusB[1], plusB[2], plusB[3])) {
                if (ctrlSize) {
                    this.previewZoom = Mth.clamp(this.previewZoom + ZOOM_BTN_STEP, ZOOM_MIN, ZOOM_MAX);
                } else {
                    PortraitInfo info = this.getSelected();
                    if (info != null) {
                        info.setSize(Mth.clamp(info.getSize() + ZOOM_BTN_STEP, 0.1f, 5.0f));
                        syncBoxIfNotFocused(this.sizeBox, info.getSize());
                    }
                }
                return true;
            }
            if (isMouseInRect(mouseX, mouseY, labelB[0], labelB[1], labelB[2], labelB[3])) {
                resetZoom();
                return true;
            }
        }
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        int contentH = this.height - HEADER - FOOTER;
        // 左列表滚动条命中：开始拖拽并立即跳到点击位置（优先于列表项命中）
        int maxScroll = Math.max(0, this.portraits.size() * ROW_H - contentH);
        if (maxScroll > 0 && button == 0 && isMouseInRect(mouseX, mouseY, 10 + LEFT_W - 4, HEADER, 4, contentH)) {
            this.scrollState.dragging = true;
            this.scrollOffset = top.yourzi.dialog.editor.gui.EditorRenderHelper.offsetFromMouseY(mouseY, HEADER, HEADER + contentH, maxScroll);
            return true;
        }
        if (isMouseInRect(mouseX, mouseY, 10, HEADER, LEFT_W, contentH)) {
            int idx = ((int) mouseY - HEADER + this.scrollOffset) / ROW_H;
            if (idx >= 0 && idx < this.portraits.size()) {
                this.selectedIndex = idx;
                this.needsLayoutRefresh = true;
                this.updatePreview();
                return true;
            }
        }
        // 在舞台内左键按下：开始拖动立绘调整偏移
        if (button == 0 && this.isMouseInStage(mouseX, mouseY) && this.getSelected() != null && this.previewTex != null) {
            // 点击舞台时取消输入框聚焦，使方向键微调可用
            clearAllBoxFocus();
            this.draggingPortrait = true;
            this.lastDragX = mouseX;
            this.lastDragY = mouseY;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        // 滚动条拖拽中：按鼠标 Y 映射 scrollOffset（优先于立绘拖动）
        if (this.scrollState.dragging) {
            int contentH = this.height - HEADER - FOOTER;
            int maxScroll = Math.max(0, this.portraits.size() * ROW_H - contentH);
            this.scrollOffset = top.yourzi.dialog.editor.gui.EditorRenderHelper.offsetFromMouseY(mouseY, HEADER, HEADER + contentH, maxScroll);
            return true;
        }
        if (this.draggingPortrait && button == 0) {
            PortraitInfo info = this.getSelected();
            if (info != null && this.viewport != null) {
                // offset = 实际屏幕像素变化 / 屏幕尺寸。
                // 舞台是实际屏幕的等比缩放（scale），舞台像素变化 / scale = 实际屏幕像素变化。
                float scale = this.viewport.scale;
                float dxReal = (float) ((mouseX - this.lastDragX) / scale);
                float dyReal = (float) ((mouseY - this.lastDragY) / scale);
                info.setOffsetX(info.getOffsetX() + dxReal / this.width);
                info.setOffsetY(info.getOffsetY() + dyReal / this.height);
                this.lastDragX = mouseX;
                this.lastDragY = mouseY;
                // 拖动时同步输入框显示（仅当输入框未聚焦时，避免打断用户输入）
                syncBoxIfNotFocused(this.offsetXBox, info.getOffsetX());
                syncBoxIfNotFocused(this.offsetYBox, info.getOffsetY());
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    /** 当输入框未聚焦时同步值（避免覆盖用户正在输入的内容）。 */
    private void syncBoxIfNotFocused(EditBox box, float value) {
        if (!box.isFocused()) {
            box.setValue(String.format(java.util.Locale.ROOT, "%.2f", value));
        }
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.scrollState.dragging) {
            this.scrollState.dragging = false;
            return true;
        }
        if (this.draggingPortrait && button == 0) {
            this.draggingPortrait = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (isMouseInRect(mx, my, 10, HEADER, LEFT_W, this.height - HEADER - FOOTER)) {
            this.scrollOffset = Mth.clamp(this.scrollOffset - (int) scrollY * ROW_H, 0, Integer.MAX_VALUE);
            return true;
        }
        // 在舞台区域内滚轮控制缩放
        int stageW = stageWidth();
        if (isMouseInRect(mx, my, STAGE_X, HEADER, stageW, this.height - HEADER - FOOTER)) {
            // Ctrl+滚轮：纯预览缩放（仅影响显示，不修改实际 size）
            if (hasControlDown()) {
                float delta = (float) scrollY * ZOOM_WHEEL_STEP;
                this.previewZoom = Mth.clamp(this.previewZoom + delta, ZOOM_MIN, ZOOM_MAX);
                return true;
            }
            // 默认直接滚轮：缩放直接应用到立绘实际 size，实际对话中立绘跟着变大/变小
            PortraitInfo info = this.getSelected();
            if (info != null) {
                float sizeStep = (float) scrollY * ZOOM_WHEEL_STEP;
                float newSize = Mth.clamp(info.getSize() + sizeStep, 0.1f, 5.0f);
                info.setSize(newSize);
                syncBoxIfNotFocused(this.sizeBox, info.getSize());
            }
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        // 显式转发字符输入到聚焦的输入框，不依赖 Screen.getFocused()
        if (this.sizeBox.isFocused()) return this.sizeBox.charTyped(codePoint, modifiers);
        if (this.brightnessBox.isFocused()) return this.brightnessBox.charTyped(codePoint, modifiers);
        if (this.offsetXBox.isFocused()) return this.offsetXBox.charTyped(codePoint, modifiers);
        if (this.offsetYBox.isFocused()) return this.offsetYBox.charTyped(codePoint, modifiers);
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // ESC：先取消输入框聚焦，再交给父类（关闭屏幕）
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            if (clearAllBoxFocus()) {
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        // 输入框聚焦时，直接转发键盘事件给输入框（包括退格、方向键移动光标等）
        if (this.sizeBox.isFocused()) return this.sizeBox.keyPressed(keyCode, scanCode, modifiers);
        if (this.brightnessBox.isFocused()) return this.brightnessBox.keyPressed(keyCode, scanCode, modifiers);
        if (this.offsetXBox.isFocused()) return this.offsetXBox.keyPressed(keyCode, scanCode, modifiers);
        if (this.offsetYBox.isFocused()) return this.offsetYBox.keyPressed(keyCode, scanCode, modifiers);
        // 无输入框聚焦时，方向键微调立绘 offset；Shift 组合更精细
        PortraitInfo info = this.getSelected();
        if (info != null) {
            float step = hasShiftDown() ? 0.005f : 0.02f;
            switch (keyCode) {
                case org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT -> { info.setOffsetX(info.getOffsetX() - step); syncBoxIfNotFocused(this.offsetXBox, info.getOffsetX()); return true; }
                case org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT -> { info.setOffsetX(info.getOffsetX() + step); syncBoxIfNotFocused(this.offsetXBox, info.getOffsetX()); return true; }
                case org.lwjgl.glfw.GLFW.GLFW_KEY_UP -> { info.setOffsetY(info.getOffsetY() - step); syncBoxIfNotFocused(this.offsetYBox, info.getOffsetY()); return true; }
                case org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN -> { info.setOffsetY(info.getOffsetY() + step); syncBoxIfNotFocused(this.offsetYBox, info.getOffsetY()); return true; }
                // R：重置预览缩放（不影响立绘实际 offset/size）
                case org.lwjgl.glfw.GLFW.GLFW_KEY_R -> { resetZoom(); return true; }
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** 取消所有输入框聚焦，返回是否有输入框曾被聚焦。 */
    private boolean clearAllBoxFocus() {
        boolean any = false;
        if (this.sizeBox.isFocused()) { this.sizeBox.setFocused(false); any = true; }
        if (this.brightnessBox.isFocused()) { this.brightnessBox.setFocused(false); any = true; }
        if (this.offsetXBox.isFocused()) { this.offsetXBox.setFocused(false); any = true; }
        if (this.offsetYBox.isFocused()) { this.offsetYBox.setFocused(false); any = true; }
        return any;
    }

    private void updatePreview() {
        PortraitInfo info = this.getSelected();
        if (info != null) {
            this.loadPreview(info.getPath());
        } else {
            this.clearPreviewRef();
        }
    }

    /**
     * 加载立绘预览。先尝试配置目录文件，再回退内置纹理资源。
     * 优化：用静态缓存 key 检查命中，避免每次 screen 重建都反复解码同一图片导致 native 内存堆积崩溃。
     */
    private void loadPreview(String path) {
        if (path == null || path.isEmpty()) {
            this.clearPreviewRef();
            return;
        }
        if (path.equals(this.previewPath) && this.previewTex != null) {
            return;
        }
        this.clearPreviewRef();
        this.previewPath = path;
        File file = EditorConfig.PORTRAITS_DIR.resolve(path).toFile();
        if (file.exists()) {
            this.previewTex = this.loadTexture(file, "portrait_" + path);
            if (this.previewTex != null) {
                Dialog.LOGGER.info("Portrait preview loaded: {} ({}x{})", path, this.previewW, this.previewH);
            }
            return;
        }
        // 内置纹理路径必须合法；含非法字符（如中文）时直接视为无预览
        try {
            ResourceLocation builtinLoc = ResourceLocation.fromNamespaceAndPath(Dialog.MODID, "textures/portraits/" + path);
            if (Minecraft.getInstance().getResourceManager().getResource(builtinLoc).isPresent()) {
                this.previewTex = builtinLoc;
                this.previewW = 256;
                this.previewH = 256;
                Dialog.LOGGER.info("Portrait preview loaded from builtin: {}", path);
            } else {
                Dialog.LOGGER.warn("Portrait preview not found in config dir or builtin: {}", path);
            }
        } catch (net.minecraft.ResourceLocationException e) {
            Dialog.LOGGER.warn("Portrait preview path invalid: {}", path, e);
        }
    }

    /**
     * 加载纹理：复用 TextureCacheService 统一缓存，命中时返回缓存尺寸，未命中时由服务解码并注册。
     */
    private ResourceLocation loadTexture(File file, String cacheKey) {
        TextureCacheService.CachedTexture cached = TextureCacheService.load(file);
        if (cached == null) {
            return null;
        }
        this.previewW = cached.width();
        this.previewH = cached.height();
        return cached.location();
    }

    private static boolean isMouseInRect(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private Component getPositionDisplay(PortraitPosition pos) {
        return switch (pos) {
            case LEFT -> Component.translatable("gui.vn_edit.position.left");
            case RIGHT -> Component.translatable("gui.vn_edit.position.right");
            case CENTER -> Component.translatable("gui.vn_edit.position.center");
        };
    }

    /**
     * 适配 VNDialogReborn 新增的所有立绘动画类型。
     */
    private Component getAnimationDisplay(PortraitAnimationType anim) {
        return switch (anim) {
            case NONE -> Component.translatable("gui.vn_edit.animation.none");
            case FADE_IN -> Component.translatable("gui.vn_edit.animation.fade_in");
            case SLIDE_IN_FROM_BOTTOM -> Component.translatable("gui.vn_edit.animation.slide_in_from_bottom");
            case BOUNCE -> Component.translatable("gui.vn_edit.animation.bounce");
            case IMPACT -> Component.translatable("gui.vn_edit.animation.impact");
            case IMPACT_MAX -> Component.translatable("gui.vn_edit.animation.impact_max");
            case ROTATE -> Component.translatable("gui.vn_edit.animation.rotate");
            case REVERSE -> Component.translatable("gui.vn_edit.animation.reverse");
            case FLASH -> Component.translatable("gui.vn_edit.animation.flash");
        };
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, EditorTheme.BG_DEEPEST);
    }

    /** 保存按钮：回写编辑后的立绘列表并返回。 */
    private void onSaveAndClose() {
        this.clearPreviewRef();
        if (this.onSave != null) {
            this.onSave.accept(this.portraits);
        }
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    /** 取消按钮 / ESC：丢弃编辑结果，用原始数据回写并返回。 */
    private void onCancelAndClose() {
        this.clearPreviewRef();
        if (this.onSave != null) {
            this.onSave.accept(this.originalPortraits);
        }
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    @Override
    public void onClose() {
        // ESC 视为取消
        this.onCancelAndClose();
    }
}
