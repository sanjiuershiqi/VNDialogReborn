package top.yourzi.dialog.editor.gui.property;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import top.yourzi.dialog.Dialog;
import top.yourzi.dialog.editor.gui.FileBrowserScreen;
import top.yourzi.dialog.editor.gui.PortraitListScreen;
import top.yourzi.dialog.editor.gui.BuiltInTextureBrowserScreen;
import top.yourzi.dialog.editor.gui.VNDialogEditorScreen;
import top.yourzi.dialog.editor.gui.widget.DropdownWidget;
import top.yourzi.dialog.editor.gui.widget.EditorButton;
import top.yourzi.dialog.editor.util.EditorConfig;
import top.yourzi.dialog.editor.util.EditorTheme;
import top.yourzi.dialog.editor.util.PageLayout;
import top.yourzi.dialog.model.BackgroundAnimationType;
import top.yourzi.dialog.model.BackgroundImageInfo;
import top.yourzi.dialog.model.BackgroundRenderOption;
import top.yourzi.dialog.model.DialogEntry;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 外观属性页：背景图、渲染选项、背景动画、立绘列表。
 * 使用 PageLayout 游标布局，自动适应不同屏幕尺寸。
 */
public class AppearancePropertyPage implements PropertyPage {
    private static final int MAX_CACHE_SIZE = 30;
    private static final Map<String, ResourceLocation> textureCache = new LinkedHashMap<String, ResourceLocation>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, ResourceLocation> eldest) {
            if (this.size() > MAX_CACHE_SIZE) {
                Minecraft.getInstance().getTextureManager().release(eldest.getValue());
                return true;
            }
            return false;
        }
    };

    private final Font font;
    private EditBox backgroundPathBox;
    private EditorButton backgroundBrowseBtn;
    private EditorButton backgroundBuiltInBtn;
    private EditorButton backgroundFolderBtn;
    private EditorButton portraitListBtn;
    private DropdownWidget backgroundRenderDropdown;
    private DropdownWidget backgroundAnimDropdown;
    private boolean visible = true;
    private static final List<String> RENDER_ITEMS = List.of(
            Component.translatable("gui.vn_edit.render_option.fill").getString(),
            Component.translatable("gui.vn_edit.render_option.fit").getString(),
            Component.translatable("gui.vn_edit.render_option.stretch").getString(),
            Component.translatable("gui.vn_edit.render_option.tile").getString(),
            Component.translatable("gui.vn_edit.render_option.center").getString()
    );
    private static final BackgroundRenderOption[] RENDER_VALUES = BackgroundRenderOption.values();
    private static final List<String> BG_ANIM_ITEMS = List.of(
            Component.translatable("gui.vn_edit.bg_anim.none").getString(),
            Component.translatable("gui.vn_edit.bg_anim.fade_in").getString()
    );
    private static final BackgroundAnimationType[] BG_ANIM_VALUES = BackgroundAnimationType.values();
    private int x;
    private int y;
    private int width;
    private ResourceLocation backgroundTexture = null;
    private int backgroundTexWidth = 0;
    private int backgroundTexHeight = 0;
    private int backgroundPreviewX;
    private int backgroundPreviewY;
    private int backgroundPreviewWidth = 100;
    private int backgroundPreviewHeight = 64;
    private DialogEntry currentEntry = null;
    private int computedHeight = 0;

    // 渲染位置缓存
    private int bgHeaderY;
    private int bgLabelY;
    private int portraitHeaderY;
    private int portraitLabelY;
    private int renderHeaderY;
    private int renderLabelY;

    public AppearancePropertyPage(Font font) {
        this.font = font;
    }

    @Override
    public void init(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;

        PageLayout layout = new PageLayout(x, y, width);
        int fieldX = layout.fieldX();
        int fieldW = layout.fieldWidth();

        // ===== 背景分节 =====
        this.bgHeaderY = layout.section();
        int bgRowY = layout.fieldRow();
        this.bgLabelY = bgRowY + 4;

        // 计算路径框和按钮的宽度，确保不溢出
        int browseBtnW = 44;
        int builtinBtnW = 36;
        int folderBtnW = 20;
        int totalBtnW = browseBtnW + builtinBtnW + folderBtnW + EditorTheme.GAP * 3;
        int pathBoxW = Math.max(50, fieldW - totalBtnW);

        this.backgroundPathBox = new EditBox(this.font, fieldX, bgRowY, pathBoxW, EditorTheme.FIELD_HEIGHT, Component.translatable("gui.vn_edit.background_path"));
        this.backgroundPathBox.setMaxLength(999999999);
        this.backgroundPathBox.setResponder(s -> {
            String clean = s.toLowerCase(Locale.ROOT);
            this.loadBackgroundPreview(clean);
            if (this.currentEntry != null) {
                if (clean.isEmpty()) {
                    this.currentEntry.setBackgroundImage(null);
                } else {
                    BackgroundImageInfo info = this.currentEntry.getBackgroundImage();
                    if (info == null) {
                        info = new BackgroundImageInfo(clean, BackgroundRenderOption.FILL);
                        this.currentEntry.setBackgroundImage(info);
                    } else {
                        info.setPath(clean);
                    }
                }
            }
        });
        int btnOffset = fieldX + pathBoxW + EditorTheme.GAP;
        this.backgroundBrowseBtn = EditorButton.builder(Component.translatable("gui.vn_edit.browse"), btn -> this.onBackgroundBrowse())
                .bounds(btnOffset, bgRowY, browseBtnW, EditorTheme.FIELD_HEIGHT).build();
        btnOffset += browseBtnW + EditorTheme.GAP;
        this.backgroundBuiltInBtn = EditorButton.builder(Component.translatable("gui.vn_edit.builtin_bg"), btn -> this.onBackgroundBuiltIn())
                .bounds(btnOffset, bgRowY, builtinBtnW, EditorTheme.FIELD_HEIGHT).build();
        btnOffset += builtinBtnW + EditorTheme.GAP;
        this.backgroundFolderBtn = EditorButton.builder(Component.literal("\uD83D\uDCC2"), btn -> EditorConfig.openFolder(EditorConfig.BACKGROUNDS_DIR))
                .bounds(btnOffset, bgRowY, folderBtnW, EditorTheme.FIELD_HEIGHT).build();

        // 背景预览
        int previewY = layout.customRow(this.backgroundPreviewHeight + EditorTheme.ROW_GAP);
        this.backgroundPreviewX = fieldX;
        this.backgroundPreviewY = previewY;
        this.backgroundPreviewWidth = Math.min(100, fieldW);

        // ===== 立绘分节 =====
        this.portraitHeaderY = layout.section();
        int portraitRowY = layout.fieldRow();
        this.portraitLabelY = portraitRowY + 4;
        int portraitBtnW = Math.min(120, fieldW);
        this.portraitListBtn = EditorButton.builder(Component.translatable("gui.vn_edit.edit_portrait_list"), btn -> this.openPortraitList())
                .bounds(fieldX, portraitRowY, portraitBtnW, EditorTheme.FIELD_HEIGHT).build();

        // ===== 渲染分节 =====
        this.renderHeaderY = layout.section();
        int renderRowY = layout.fieldRow();
        this.renderLabelY = renderRowY + 4;

        // 两个下拉框并排，宽度自适应
        int dropdownGap = EditorTheme.GAP;
        int totalDropdownW = fieldW - dropdownGap;
        int eachDropdownW = Math.max(60, totalDropdownW / 2 - dropdownGap / 2);

        this.backgroundRenderDropdown = new DropdownWidget(this.font, fieldX, renderRowY, eachDropdownW, EditorTheme.FIELD_HEIGHT, new ArrayList<>(RENDER_ITEMS), selected -> {
            if (this.currentEntry == null) {
                return;
            }
            int idx = RENDER_ITEMS.indexOf(selected);
            if (idx < 0 || idx >= RENDER_VALUES.length) {
                return;
            }
            BackgroundRenderOption next = RENDER_VALUES[idx];
            BackgroundImageInfo info = this.currentEntry.getBackgroundImage();
            if (info != null) {
                info.setRenderOption(next);
            } else {
                String path = this.backgroundPathBox.getValue();
                if (!path.isEmpty()) {
                    this.currentEntry.setBackgroundImage(new BackgroundImageInfo(path, next));
                }
            }
        });
        this.backgroundAnimDropdown = new DropdownWidget(this.font, fieldX + eachDropdownW + dropdownGap, renderRowY, eachDropdownW, EditorTheme.FIELD_HEIGHT, new ArrayList<>(BG_ANIM_ITEMS), selected -> {
            if (this.currentEntry == null) {
                return;
            }
            int idx = BG_ANIM_ITEMS.indexOf(selected);
            if (idx < 0 || idx >= BG_ANIM_VALUES.length) {
                return;
            }
            BackgroundAnimationType next = BG_ANIM_VALUES[idx];
            BackgroundImageInfo info = this.currentEntry.getBackgroundImage();
            if (info != null) {
                info.setAnimationType(next);
            } else {
                String path = this.backgroundPathBox.getValue();
                if (!path.isEmpty()) {
                    BackgroundImageInfo newInfo = new BackgroundImageInfo(path, BackgroundRenderOption.FILL, next);
                    this.currentEntry.setBackgroundImage(newInfo);
                }
            }
        });

        this.computedHeight = layout.getContentHeight();
    }

    private Component getRenderOptionDisplay(BackgroundRenderOption option) {
        return switch (option) {
            case FILL -> Component.translatable("gui.vn_edit.render_option.fill");
            case FIT -> Component.translatable("gui.vn_edit.render_option.fit");
            case STRETCH -> Component.translatable("gui.vn_edit.render_option.stretch");
            case TILE -> Component.translatable("gui.vn_edit.render_option.tile");
            case CENTER -> Component.translatable("gui.vn_edit.render_option.center");
        };
    }

    private Component getBgAnimDisplay(BackgroundAnimationType anim) {
        return switch (anim) {
            case NONE -> Component.translatable("gui.vn_edit.bg_anim.none");
            case FADE_IN -> Component.translatable("gui.vn_edit.bg_anim.fade_in");
        };
    }

    private void onBackgroundBrowse() {
        FileBrowserScreen.open(EditorConfig.BACKGROUNDS_DIR.toFile(), new String[]{"png", "jpg", "jpeg"}, path -> {
            String lowerPath = path.toLowerCase(Locale.ROOT);
            this.backgroundPathBox.setValue(lowerPath);
            this.recoverAppearanceTab();
        }, Minecraft.getInstance().screen);
    }

    private void onBackgroundBuiltIn() {
        Minecraft.getInstance().setScreen(new BuiltInTextureBrowserScreen("textures/backgrounds/", path -> {
            String lowerPath = path.toLowerCase(Locale.ROOT);
            this.backgroundPathBox.setValue(lowerPath);
            this.recoverAppearanceTab();
        }, Minecraft.getInstance().screen));
    }

    private void openPortraitList() {
        if (this.currentEntry == null) {
            return;
        }
        ArrayList<top.yourzi.dialog.model.PortraitInfo> portraits = this.currentEntry.getPortraits() != null
                ? new ArrayList<>(this.currentEntry.getPortraits()) : new ArrayList<>();
        Minecraft.getInstance().setScreen(new PortraitListScreen(portraits, editedList -> {
            this.currentEntry.setPortraits(editedList.isEmpty() ? null : new ArrayList<>(editedList));
            this.recoverAppearanceTab();
        }, Minecraft.getInstance().screen));
    }

    private void recoverAppearanceTab() {
        Screen screen = Minecraft.getInstance().screen;
        if (screen instanceof VNDialogEditorScreen editor) {
            editor.setPropertyPanelTab(1);
        }
    }

    private void loadBackgroundPreview(String path) {
        if (path.isEmpty()) {
            this.backgroundTexture = null;
            this.backgroundTexWidth = 0;
            this.backgroundTexHeight = 0;
            return;
        }
        File file = EditorConfig.BACKGROUNDS_DIR.resolve(path).toFile();
        if (file.exists()) {
            this.backgroundTexture = this.loadTexture(file, "background_" + path);
        } else {
            ResourceLocation builtinLoc = ResourceLocation.fromNamespaceAndPath(Dialog.MODID, "textures/backgrounds/" + path);
            if (Minecraft.getInstance().getResourceManager().getResource(builtinLoc).isPresent()) {
                this.backgroundTexture = builtinLoc;
                // 内置纹理使用默认尺寸，blit 时按 256x256 作为源
                this.backgroundTexWidth = 256;
                this.backgroundTexHeight = 256;
            } else {
                this.backgroundTexture = null;
                this.backgroundTexWidth = 0;
                this.backgroundTexHeight = 0;
            }
        }
    }

    private ResourceLocation loadTexture(File file, String cacheKey) {
        String safeKey = cacheKey.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/._-]", "_");
        if (textureCache.containsKey(safeKey)) {
            return textureCache.get(safeKey);
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
            NativeImage image = NativeImage.read(fis);
            this.backgroundTexWidth = image.getWidth();
            this.backgroundTexHeight = image.getHeight();
            DynamicTexture dynamicTexture = new DynamicTexture(image);
            ResourceLocation rl = ResourceLocation.fromNamespaceAndPath(Dialog.MODID, "editor_preview/" + safeKey);
            Minecraft.getInstance().getTextureManager().register(rl, dynamicTexture);
            textureCache.put(safeKey, rl);
            return rl;
        } catch (IOException e) {
            Dialog.LOGGER.error("Failed to load preview texture: {}", file, e);
            return null;
        }
    }

    public static void releaseTextures() {
        for (ResourceLocation rl : textureCache.values()) {
            Minecraft.getInstance().getTextureManager().release(rl);
        }
        textureCache.clear();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!this.visible) {
            return;
        }
        // 背景分节
        EditorTheme.drawSectionHeader(graphics, this.font, this.x, this.bgHeaderY, this.width, Component.translatable("gui.vn_edit.section.background"));
        graphics.drawString(this.font, Component.translatable("gui.vn_edit.background_path"), this.x + 5, this.bgLabelY, EditorTheme.TEXT_SECONDARY);
        this.backgroundPathBox.render(graphics, mouseX, mouseY, partialTick);
        this.backgroundBrowseBtn.render(graphics, mouseX, mouseY, partialTick);
        this.backgroundBuiltInBtn.render(graphics, mouseX, mouseY, partialTick);
        this.backgroundFolderBtn.render(graphics, mouseX, mouseY, partialTick);
        // 背景预览
        if (this.backgroundTexture != null && this.backgroundTexWidth > 0 && this.backgroundTexHeight > 0) {
            RenderSystem.setShaderTexture(0, this.backgroundTexture);
            // 使用完整纹理尺寸作为源，将整张图缩放绘制到预览区域内
            graphics.blit(this.backgroundTexture, this.backgroundPreviewX, this.backgroundPreviewY, 0.0f, 0.0f,
                    this.backgroundPreviewWidth, this.backgroundPreviewHeight, this.backgroundTexWidth, this.backgroundTexHeight);
        } else if (this.backgroundTexture != null) {
            // 纹理尺寸未知时，使用默认 256x256 作为源尺寸
            RenderSystem.setShaderTexture(0, this.backgroundTexture);
            graphics.blit(this.backgroundTexture, this.backgroundPreviewX, this.backgroundPreviewY, 0.0f, 0.0f,
                    this.backgroundPreviewWidth, this.backgroundPreviewHeight, 256, 256);
        } else {
            graphics.fill(this.backgroundPreviewX, this.backgroundPreviewY,
                    this.backgroundPreviewX + this.backgroundPreviewWidth, this.backgroundPreviewY + this.backgroundPreviewHeight, EditorTheme.BG_SURFACE);
            graphics.drawCenteredString(this.font, Component.translatable("gui.vn_edit.no_background"),
                    this.backgroundPreviewX + this.backgroundPreviewWidth / 2, this.backgroundPreviewY + 20, EditorTheme.TEXT_SECONDARY);
        }
        // 立绘分节
        EditorTheme.drawSectionHeader(graphics, this.font, this.x, this.portraitHeaderY, this.width, Component.translatable("gui.vn_edit.section.portraits"));
        graphics.drawString(this.font, Component.translatable("gui.vn_edit.portraits"), this.x + 5, this.portraitLabelY, EditorTheme.TEXT_SECONDARY);
        this.portraitListBtn.render(graphics, mouseX, mouseY, partialTick);
        // 渲染分节
        EditorTheme.drawSectionHeader(graphics, this.font, this.x, this.renderHeaderY, this.width, Component.translatable("gui.vn_edit.section.render"));
        graphics.drawString(this.font, Component.translatable("gui.vn_edit.render_option"), this.x + 5, this.renderLabelY, EditorTheme.TEXT_SECONDARY);
        this.backgroundRenderDropdown.render(graphics, mouseX, mouseY, partialTick);
        this.backgroundAnimDropdown.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public List<? extends GuiEventListener> children() {
        return List.of(this.backgroundPathBox, this.backgroundBrowseBtn, this.backgroundBuiltInBtn, this.backgroundFolderBtn, this.portraitListBtn, this.backgroundRenderDropdown, this.backgroundAnimDropdown);
    }

    @Override
    public void bindTo(DialogEntry entry) {
        this.currentEntry = entry;
        this.refreshDisplay();
    }

    @Override
    public void unbind() {
        this.currentEntry = null;
        this.backgroundTexture = null;
        this.backgroundPathBox.setValue("");
        this.backgroundRenderDropdown.setSelected(this.getRenderOptionDisplay(BackgroundRenderOption.FILL).getString());
        this.backgroundAnimDropdown.setSelected(this.getBgAnimDisplay(BackgroundAnimationType.NONE).getString());
    }

    @Override
    public void refreshDisplay() {
        if (this.currentEntry == null) {
            this.unbind();
            return;
        }
        BackgroundImageInfo bgInfo = this.currentEntry.getBackgroundImage();
        String bgPath = bgInfo != null ? bgInfo.getPath() : "";
        this.backgroundPathBox.setResponder(null);
        this.backgroundPathBox.setValue(bgPath.toLowerCase(Locale.ROOT));
        this.backgroundPathBox.setResponder(s -> {
            String clean = s.toLowerCase(Locale.ROOT);
            this.loadBackgroundPreview(clean);
            if (this.currentEntry != null) {
                if (clean.isEmpty()) {
                    this.currentEntry.setBackgroundImage(null);
                } else {
                    BackgroundImageInfo info = this.currentEntry.getBackgroundImage();
                    if (info == null) {
                        info = new BackgroundImageInfo(clean, BackgroundRenderOption.FILL);
                        this.currentEntry.setBackgroundImage(info);
                    } else {
                        info.setPath(clean);
                    }
                }
            }
        });
        BackgroundRenderOption option = bgInfo != null && bgInfo.getRenderOption() != null ? bgInfo.getRenderOption() : BackgroundRenderOption.FILL;
        this.backgroundRenderDropdown.setSelected(this.getRenderOptionDisplay(option).getString());
        BackgroundAnimationType anim = bgInfo != null && bgInfo.getAnimationType() != null ? bgInfo.getAnimationType() : BackgroundAnimationType.NONE;
        this.backgroundAnimDropdown.setSelected(this.getBgAnimDisplay(anim).getString());
        this.loadBackgroundPreview(bgPath);
    }

    @Override
    public void setVisible(boolean visible) {
        this.visible = visible;
        this.backgroundPathBox.setVisible(visible);
        this.backgroundBrowseBtn.visible = visible;
        this.backgroundBuiltInBtn.visible = visible;
        this.backgroundFolderBtn.visible = visible;
        this.portraitListBtn.visible = visible;
        this.backgroundRenderDropdown.visible = visible;
        this.backgroundAnimDropdown.visible = visible;
    }

    @Override
    public List<DropdownWidget> getDropdowns() {
        return List.of(this.backgroundRenderDropdown, this.backgroundAnimDropdown);
    }

    @Override
    public int getContentHeight() {
        return this.computedHeight;
    }
}
