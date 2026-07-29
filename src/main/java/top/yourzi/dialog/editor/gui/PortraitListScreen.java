package top.yourzi.dialog.editor.gui;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import top.yourzi.dialog.editor.gui.widget.EditorButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import top.yourzi.dialog.Dialog;
import top.yourzi.dialog.editor.gui.widget.DropdownWidget;
import top.yourzi.dialog.editor.gui.BuiltInTextureBrowserScreen;
import top.yourzi.dialog.editor.util.EditorConfig;
import top.yourzi.dialog.editor.util.EditorTheme;
import top.yourzi.dialog.editor.util.FileSystemTextureLoader;
import top.yourzi.dialog.model.PortraitAnimationType;
import top.yourzi.dialog.model.PortraitInfo;
import top.yourzi.dialog.model.PortraitPosition;

import java.io.File;
import java.io.FileInputStream;
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
    private static final int STAGE_SIDE_MARGIN = 8;

    private static final int MAX_PREVIEW_CACHE_SIZE = 30;
    // 预览纹理静态缓存：与 AppearancePropertyPage.loadTexture 完全一致的机制，
    // 复用已加载的纹理，避免每次选择都重建 DynamicTexture。
    private static final java.util.Map<String, ResourceLocation> previewTextureCache = new java.util.LinkedHashMap<String, ResourceLocation>() {
        @Override
        protected boolean removeEldestEntry(java.util.Map.Entry<String, ResourceLocation> eldest) {
            if (this.size() > MAX_PREVIEW_CACHE_SIZE) {
                Minecraft.getInstance().getTextureManager().release(eldest.getValue());
                previewSizeCache.remove(eldest.getKey());
                return true;
            }
            return false;
        }
    };
    private static final java.util.Map<String, int[]> previewSizeCache = new java.util.LinkedHashMap<>();

    private final List<PortraitInfo> portraits;
    private final Consumer<List<PortraitInfo>> onSave;
    private final Screen parent;
    private int selectedIndex = -1;
    private int scrollOffset = 0;
    private ResourceLocation previewTex = null;
    private String previewPath = null;
    private int previewW;
    private int previewH;
    private boolean draggingPortrait = false;
    private double lastDragX = 0;
    private double lastDragY = 0;
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
    private static final List<String> POS_ITEMS = List.of(
            Component.translatable("gui.vn_edit.position.left").getString(),
            Component.translatable("gui.vn_edit.position.right").getString(),
            Component.translatable("gui.vn_edit.position.center").getString()
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
        this.onSave = onSave;
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        this.addRenderableWidget(EditorButton.builder(Component.translatable("gui.vn_edit.add_portrait"), b -> this.openFileBrowser())
                .bounds(10, this.height - 25, 80, 20).build());
        this.addRenderableWidget(EditorButton.builder(Component.translatable("gui.vn_edit.builtin_portrait"), b -> this.openBuiltInBrowser())
                .bounds(95, this.height - 25, 60, 20).build());
        this.addRenderableWidget(EditorButton.builder(Component.translatable("gui.vn_edit.save"), b -> this.onClose())
                .bounds(this.width / 2 - 105, this.height - 25, 100, 20).build());
        this.addRenderableWidget(EditorButton.builder(Component.translatable("gui.vn_edit.cancel"), b -> this.onClose())
                .bounds(this.width / 2 + 5, this.height - 25, 100, 20).build());
        this.posDropdown = this.addRenderableWidget(new DropdownWidget(this.font, 0, 0, 100, 16, new ArrayList<>(POS_ITEMS), selected -> {
            PortraitInfo info = this.getSelected();
            if (info != null) {
                int idx = POS_ITEMS.indexOf(selected);
                if (idx >= 0 && idx < POS_VALUES.length) {
                    info.setPosition(POS_VALUES[idx]);
                }
            }
        }));
        this.animDropdown = this.addRenderableWidget(new DropdownWidget(this.font, 0, 0, 100, 16, new ArrayList<>(ANIM_ITEMS), selected -> {
            PortraitInfo info = this.getSelected();
            if (info != null) {
                int idx = ANIM_ITEMS.indexOf(selected);
                if (idx >= 0 && idx < ANIM_VALUES.length) {
                    info.setAnimationType(ANIM_VALUES[idx]);
                }
            }
        }));
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
                this.updatePreview();
            }
        }).bounds(0, 0, 60, 16).build());
        this.resetOffsetBtn = this.addRenderableWidget(EditorButton.builder(Component.translatable("gui.vn_edit.reset_offset"), b -> {
            PortraitInfo info = this.getSelected();
            if (info != null) {
                info.setOffsetX(0.0f);
                info.setOffsetY(0.0f);
            }
        }).bounds(0, 0, 38, 16).build());
        this.upBtn = this.addRenderableWidget(EditorButton.builder(Component.literal("\u25b2"), b -> {
            if (this.selectedIndex > 0 && this.selectedIndex < this.portraits.size()) {
                this.portraits.add(this.selectedIndex - 1, this.portraits.remove(this.selectedIndex));
                this.selectedIndex--;
            }
        }).bounds(0, 0, 20, 16).build());
        this.downBtn = this.addRenderableWidget(EditorButton.builder(Component.literal("\u25bc"), b -> {
            if (this.selectedIndex >= 0 && this.selectedIndex < this.portraits.size() - 1) {
                this.portraits.add(this.selectedIndex + 1, this.portraits.remove(this.selectedIndex));
                this.selectedIndex++;
            }
        }).bounds(0, 0, 20, 16).build());
        this.folderBtn = this.addRenderableWidget(EditorButton.builder(Component.literal("\uD83D\uDCC2"), b -> EditorConfig.openFolder(EditorConfig.PORTRAITS_DIR))
                .bounds(this.width - 30, 25, 25, 16).build());
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
                this.updatePreview();
            }
        }, Minecraft.getInstance().screen));
    }

    private void releasePreviewTexture() {
        // 预览纹理由静态缓存管理（与 AppearancePropertyPage 一致），切换选中项时不释放，
        // 交由缓存 LRU 淘汰时统一 release。
        this.previewTex = null;
        this.previewPath = null;
    }

    /**
     * 释放所有静态缓存的预览纹理。编辑器关闭时调用。
     */
    public static void releaseAllPreviewTextures() {
        for (ResourceLocation rl : previewTextureCache.values()) {
            Minecraft.getInstance().getTextureManager().release(rl);
        }
        previewTextureCache.clear();
        previewSizeCache.clear();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        this.renderBackground(g, mx, my, pt);
        g.drawCenteredString(this.font, this.title, this.width / 2, 10, EditorTheme.TEXT_PRIMARY);
        int contentH = this.height - HEADER - FOOTER;
        this.renderLeftList(g, mx, my, contentH);
        this.renderMiddlePanel(g, mx, my, contentH);
        this.renderPreview(g);
        this.updateDynamicButtons();
        super.render(g, mx, my, pt);
        // 在所有控件之后渲染展开的下拉弹出列表，确保不被遮挡
        this.posDropdown.renderPopup(g, mx, my, pt);
        this.animDropdown.renderPopup(g, mx, my, pt);
    }

    private void updateDynamicButtons() {
        PortraitInfo info = this.getSelected();
        boolean visible = info != null;
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
        if (visible) {
            int line1Y = 40;
            int line2Y = 65;
            int line3Y = 90;
            int line4Y = 115;
            int line5Y = 140;
            int line6Y = 165;
            int line7Y = 190;
            this.posDropdown.setX(200);
            this.posDropdown.setY(line1Y);
            this.posDropdown.setSelected(this.getPositionDisplay(info.getPosition()).getString());
            this.animDropdown.setX(200);
            this.animDropdown.setY(line2Y);
            this.animDropdown.setSelected(this.getAnimationDisplay(info.getAnimationType()).getString());
            this.sizeBox.setX(200);
            this.sizeBox.setY(line3Y);
            this.sizeBox.setResponder(null);
            this.sizeBox.setValue(String.format(java.util.Locale.ROOT, "%.2f", info.getSize()));
            this.sizeBox.setResponder(s -> {
                PortraitInfo si = this.getSelected();
                if (si != null && !s.isEmpty()) {
                    try {
                        float v = Float.parseFloat(s.trim());
                        si.setSize(Math.max(0.0f, Math.min(5.0f, v)));
                    } catch (NumberFormatException ignored) {
                    }
                }
            });
            this.brightnessBox.setX(200);
            this.brightnessBox.setY(line4Y);
            this.brightnessBox.setResponder(null);
            this.brightnessBox.setValue(String.format(java.util.Locale.ROOT, "%.2f", info.getBrightness()));
            this.brightnessBox.setResponder(s -> {
                PortraitInfo si = this.getSelected();
                if (si != null && !s.isEmpty()) {
                    try {
                        float v = Float.parseFloat(s.trim());
                        si.setBrightness(Math.max(0.0f, Math.min(1.0f, v)));
                    } catch (NumberFormatException ignored) {
                    }
                }
            });
            this.offsetXBox.setX(200);
            this.offsetXBox.setY(line5Y);
            this.offsetXBox.setResponder(null);
            this.offsetXBox.setValue(String.format(java.util.Locale.ROOT, "%.2f", info.getOffsetX()));
            this.offsetXBox.setResponder(s -> {
                PortraitInfo si = this.getSelected();
                if (si != null && !s.isEmpty()) {
                    try {
                        float v = Float.parseFloat(s.trim());
                        si.setOffsetX(v);
                    } catch (NumberFormatException ignored) {
                    }
                }
            });
            this.offsetYBox.setX(200);
            this.offsetYBox.setY(line6Y);
            this.offsetYBox.setResponder(null);
            this.offsetYBox.setValue(String.format(java.util.Locale.ROOT, "%.2f", info.getOffsetY()));
            this.offsetYBox.setResponder(s -> {
                PortraitInfo si = this.getSelected();
                if (si != null && !s.isEmpty()) {
                    try {
                        float v = Float.parseFloat(s.trim());
                        si.setOffsetY(v);
                    } catch (NumberFormatException ignored) {
                    }
                }
            });
            this.delBtn.setX(145);
            this.delBtn.setY(line7Y);
            this.upBtn.setX(260);
            this.upBtn.setY(line7Y);
            this.downBtn.setX(285);
            this.downBtn.setY(line7Y);
            this.resetOffsetBtn.setX(282);
            this.resetOffsetBtn.setY(line6Y);
        }
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
        int curY = y - this.scrollOffset;
        for (int i = 0; i < this.portraits.size(); i++) {
            PortraitInfo info = this.portraits.get(i);
            int rowY = curY + i * ROW_H;
            if (rowY + ROW_H < y || rowY > y + h) {
                continue;
            }
            boolean hover = isMouseInRect(mx, my, x, rowY, w, ROW_H);
            int bg = i == this.selectedIndex ? EditorTheme.BG_SELECTED : (hover ? EditorTheme.BG_HOVER : 0);
            g.fill(x, rowY, x + w, rowY + ROW_H, bg);
            String name = info.getPath() != null ? info.getPath() : "";
            String trimmed = this.font.plainSubstrByWidth(name, w - 10);
            g.drawString(this.font, trimmed, x + 4, rowY + 2, EditorTheme.TEXT_PRIMARY);
        }
        g.disableScissor();
        if (maxScroll > 0) {
            int sbH = Math.max(10, h * h / (this.portraits.size() * ROW_H));
            int sbY = y + (int) ((float) this.scrollOffset / (float) maxScroll * (float) (h - sbH));
            g.fill(x + w - 4, y, x + w, y + h, 0x33FFFFFF);
            g.fill(x + w - 4, sbY, x + w, sbY + sbH, EditorTheme.TEXT_MUTED);
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
        g.drawString(this.font, Component.translatable("gui.vn_edit.position"), x + 5, 44, EditorTheme.TEXT_SECONDARY);
        g.drawString(this.font, Component.translatable("gui.vn_edit.animation"), x + 5, 69, EditorTheme.TEXT_SECONDARY);
        g.drawString(this.font, Component.translatable("gui.vn_edit.size"), x + 5, 94, EditorTheme.TEXT_SECONDARY);
        g.drawString(this.font, Component.translatable("gui.vn_edit.brightness"), x + 5, 119, EditorTheme.TEXT_SECONDARY);
        g.drawString(this.font, Component.translatable("gui.vn_edit.offset_x"), x + 5, 144, EditorTheme.TEXT_SECONDARY);
        g.drawString(this.font, Component.translatable("gui.vn_edit.offset_y"), x + 5, 169, EditorTheme.TEXT_SECONDARY);
    }

    private void renderPreview(GuiGraphics g) {
        int x = STAGE_X;
        int y = HEADER;
        int w = Math.max(80, this.width - STAGE_X - 10);
        int h = this.height - HEADER - FOOTER;
        g.fill(x, y, x + w, y + h, EditorTheme.BG_SURFACE);
        // 舞台边框
        g.fill(x, y, x + w, y + 1, EditorTheme.BG_ELEVATED);
        g.fill(x, y + h - 1, x + w, y + h, EditorTheme.BG_ELEVATED);
        g.fill(x, y, x + 1, y + h, EditorTheme.BG_ELEVATED);
        g.fill(x + w - 1, y, x + w, y + h, EditorTheme.BG_ELEVATED);
        PortraitInfo info = this.getSelected();
        if (this.previewTex != null && info != null) {
            // 模拟实际对话场景：立绘高度 = 舞台高度 * 0.68 * size，底部对齐
            float size = Mth.clamp(info.getSize(), 0.1f, 5.0f);
            int portraitH = (int) (h * 0.68f * size);
            if (portraitH > h) {
                portraitH = h;
            }
            // 宽高比：previewW/H 为 0（内置纹理未读取尺寸）时默认 1:1
            float ratio = (this.previewW > 0 && this.previewH > 0)
                    ? (float) this.previewW / (float) this.previewH
                    : 1.0f;
            int portraitW = Math.max(1, (int) (portraitH * ratio));
            if (portraitW > w) {
                portraitW = w;
                portraitH = (int) (portraitW / ratio);
            }
            int baseX = switch (info.getPosition() == null ? PortraitPosition.RIGHT : info.getPosition()) {
                case LEFT -> x + STAGE_SIDE_MARGIN;
                case CENTER -> x + (w - portraitW) / 2;
                case RIGHT -> x + w - portraitW - STAGE_SIDE_MARGIN;
            };
            int baseY = y + h - portraitH;
            int renderX = baseX + (int) (info.getOffsetX() * w);
            int renderY = baseY + (int) (info.getOffsetY() * h);
            // 与背景预览 / 原版编辑器模组完全一致的渲染方式：
            // 仅手动绑定纹理 + 8 参数 float 版 blit（UV 0..1 采样整张纹理缩放绘制）。
            // 不设置 setShader/setShaderColor/enableBlend，这些会与 GuiGraphics 托管批处理管线冲突。
            RenderSystem.setShaderTexture(0, this.previewTex);
            g.blit(this.previewTex, renderX, renderY, 0.0f, 0.0f, portraitW, portraitH, portraitW, portraitH);
            // 拖动提示
            if (this.draggingPortrait) {
                g.drawCenteredString(this.font, Component.translatable("gui.vn_edit.drag_hint"), x + w / 2, y + 4, EditorTheme.ACCENT);
            } else {
                g.drawCenteredString(this.font, Component.translatable("gui.vn_edit.drag_to_adjust"), x + w / 2, y + 4, EditorTheme.TEXT_MUTED);
            }
        } else if (info == null) {
            g.drawCenteredString(this.font, Component.translatable("gui.vn_edit.no_portrait_selected"), x + w / 2, y + h / 2 - 4, EditorTheme.TEXT_MUTED);
        } else {
            g.drawCenteredString(this.font, Component.translatable("gui.vn_edit.no_preview"), x + w / 2, y + h / 2 - 4, EditorTheme.TEXT_MUTED);
        }
    }

    private boolean isMouseInStage(double mx, double my) {
        int x = STAGE_X;
        int y = HEADER;
        int w = Math.max(80, this.width - STAGE_X - 10);
        int h = this.height - HEADER - FOOTER;
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
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
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        int contentH = this.height - HEADER - FOOTER;
        if (isMouseInRect(mouseX, mouseY, 10, HEADER, LEFT_W, contentH)) {
            int idx = ((int) mouseY - HEADER + this.scrollOffset) / ROW_H;
            if (idx >= 0 && idx < this.portraits.size()) {
                this.selectedIndex = idx;
                this.updatePreview();
                return true;
            }
        }
        // 在舞台内按下：开始拖动立绘调整偏移
        if (button == 0 && this.isMouseInStage(mouseX, mouseY) && this.getSelected() != null && this.previewTex != null) {
            this.draggingPortrait = true;
            this.lastDragX = mouseX;
            this.lastDragY = mouseY;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.draggingPortrait && button == 0) {
            PortraitInfo info = this.getSelected();
            if (info != null) {
                int stageW = Math.max(1, this.width - STAGE_X - 10);
                int stageH = Math.max(1, this.height - HEADER - FOOTER);
                double dx = mouseX - this.lastDragX;
                double dy = mouseY - this.lastDragY;
                info.setOffsetX(info.getOffsetX() + (float) (dx / stageW));
                info.setOffsetY(info.getOffsetY() + (float) (dy / stageH));
                this.lastDragX = mouseX;
                this.lastDragY = mouseY;
                // 同步输入框显示
                this.offsetXBox.setResponder(null);
                this.offsetXBox.setValue(String.format(java.util.Locale.ROOT, "%.2f", info.getOffsetX()));
                this.offsetXBox.setResponder(s -> {
                    PortraitInfo si = this.getSelected();
                    if (si != null && !s.isEmpty()) {
                        try {
                            si.setOffsetX(Float.parseFloat(s.trim()));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                });
                this.offsetYBox.setResponder(null);
                this.offsetYBox.setValue(String.format(java.util.Locale.ROOT, "%.2f", info.getOffsetY()));
                this.offsetYBox.setResponder(s -> {
                    PortraitInfo si = this.getSelected();
                    if (si != null && !s.isEmpty()) {
                        try {
                            si.setOffsetY(Float.parseFloat(s.trim()));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                });
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
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
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    private void updatePreview() {
        PortraitInfo info = this.getSelected();
        if (info != null) {
            this.loadPreview(info.getPath());
        } else {
            this.previewTex = null;
            this.previewPath = null;
        }
    }

    private void loadPreview(String path) {
        if (path == null) {
            return;
        }
        if (path.equals(this.previewPath)) {
            return;
        }
        this.releasePreviewTexture();
        this.previewPath = path;
        File f = EditorConfig.PORTRAITS_DIR.resolve(path).toFile();
        if (!f.exists()) {
            // 配置目录没有该文件，检查是否为模组内置纹理
            // 路径含非法字符（如中文）时 ResourceLocation 构造会抛异常，需 try-catch
            try {
                ResourceLocation builtinLoc = ResourceLocation.fromNamespaceAndPath(Dialog.MODID, "textures/portraits/" + path);
                if (Minecraft.getInstance().getResourceManager().getResource(builtinLoc).isPresent()) {
                    // 内置纹理使用默认尺寸，blit 时按 UV 0..1 采样整张纹理缩放绘制
                    this.previewW = 256;
                    this.previewH = 256;
                    this.previewTex = builtinLoc;
                    Dialog.LOGGER.info("Portrait preview loaded from builtin: {}", path);
                } else {
                    Dialog.LOGGER.warn("Portrait preview not found in config dir or builtin: {}", path);
                    this.previewTex = null;
                }
            } catch (net.minecraft.ResourceLocationException e) {
                Dialog.LOGGER.warn("Portrait preview path invalid: {}", path, e);
                this.previewTex = null;
            }
            return;
        }
        // 与 AppearancePropertyPage.loadTexture 完全一致的加载逻辑：
        // 用 safeKey 缓存，命中直接返回；未命中则解码 + DynamicTexture + register。
        ResourceLocation loaded = this.loadPreviewTexture(f, "portrait_" + path);
        if (loaded != null) {
            this.previewTex = loaded;
            Dialog.LOGGER.info("Portrait preview loaded from file: {} ({}x{})", f.getAbsolutePath(), this.previewW, this.previewH);
        } else {
            this.previewTex = null;
        }
    }

    /**
     * 加载预览纹理：与 AppearancePropertyPage.loadTexture 完全一致的实现。
     * 使用静态缓存复用纹理，命中时恢复尺寸，未命中时解码并注册。
     */
    private ResourceLocation loadPreviewTexture(File file, String cacheKey) {
        String safeKey = cacheKey.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/._-]", "_");
        if (previewTextureCache.containsKey(safeKey)) {
            int[] size = previewSizeCache.get(safeKey);
            if (size != null) {
                this.previewW = size[0];
                this.previewH = size[1];
            }
            return previewTextureCache.get(safeKey);
        }
        if (!file.exists()) {
            File parent = file.getParentFile();
            if (parent != null && parent.isDirectory()) {
                File finalFile = file;
                File[] matches = parent.listFiles((dir, name) -> name.equalsIgnoreCase(finalFile.getName()));
                if (matches != null && matches.length > 0) {
                    file = matches[0];
                } else {
                    Dialog.LOGGER.warn("Texture file not found: {}", file.getAbsolutePath());
                    return null;
                }
            } else {
                Dialog.LOGGER.warn("Texture file not found: {}", file.getAbsolutePath());
                return null;
            }
        }
        try (FileInputStream fis = new FileInputStream(file)) {
            NativeImage image = FileSystemTextureLoader.decodeToNativeImage(fis);
            this.previewW = image.getWidth();
            this.previewH = image.getHeight();
            DynamicTexture dynamicTexture = new DynamicTexture(image);
            ResourceLocation rl = ResourceLocation.fromNamespaceAndPath(Dialog.MODID, "editor_preview/" + safeKey);
            Minecraft.getInstance().getTextureManager().register(rl, dynamicTexture);
            previewTextureCache.put(safeKey, rl);
            previewSizeCache.put(safeKey, new int[]{this.previewW, this.previewH});
            return rl;
        } catch (Exception e) {
            Dialog.LOGGER.error("Failed to load preview texture: {}", file, e);
            return null;
        }
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

    @Override
    public void onClose() {
        this.releasePreviewTexture();
        if (this.onSave != null) {
            this.onSave.accept(this.portraits);
        }
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }
}
