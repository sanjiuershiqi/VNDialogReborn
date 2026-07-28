package top.yourzi.dialog.editor.gui.property;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import top.yourzi.dialog.Dialog;
import top.yourzi.dialog.editor.gui.FileBrowserScreen;
import top.yourzi.dialog.editor.gui.PortraitListScreen;
import top.yourzi.dialog.editor.gui.VNDialogEditorScreen;
import top.yourzi.dialog.editor.util.EditorConfig;
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
 * 融合自 visual_mod_edit_vndialog，并适配 VNDialogReborn 新增的背景动画类型。
 */
public class AppearancePropertyPage implements PropertyPage {
    private static final int LABEL_WIDTH = 60;
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
    private Button backgroundBrowseBtn;
    private Button portraitListBtn;
    private Button backgroundRenderBtn;
    private Button backgroundAnimBtn;
    private boolean visible = true;
    private int x;
    private int y;
    private int width;
    private int height;
    private ResourceLocation backgroundTexture = null;
    private int backgroundPreviewX;
    private int backgroundPreviewY;
    private int backgroundPreviewWidth = 100;
    private int backgroundPreviewHeight = 56;
    private DialogEntry currentEntry = null;

    public AppearancePropertyPage(Font font) {
        this.font = font;
    }

    @Override
    public void init(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        int fieldX = x + LABEL_WIDTH + 5;
        int fieldWidth = width - LABEL_WIDTH - 10 - 60;
        this.backgroundPathBox = new EditBox(this.font, fieldX, y + 5, fieldWidth, 16, Component.translatable("gui.vn_edit.background_path"));
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
        this.backgroundBrowseBtn = Button.builder(Component.translatable("gui.vn_edit.browse"), btn -> this.onBackgroundBrowse())
                .bounds(fieldX + fieldWidth + 5, y + 5, 48, 16).build();
        this.portraitListBtn = Button.builder(Component.translatable("gui.vn_edit.edit_portrait_list"), btn -> this.openPortraitList())
                .bounds(fieldX, y + 25 + 5, 100, 16).build();
        this.backgroundRenderBtn = Button.builder(Component.translatable("gui.vn_edit.render_option.fill"), btn -> {
            if (this.currentEntry == null) {
                return;
            }
            BackgroundImageInfo info = this.currentEntry.getBackgroundImage();
            BackgroundRenderOption current = info != null && info.getRenderOption() != null ? info.getRenderOption() : BackgroundRenderOption.FILL;
            BackgroundRenderOption[] vals = BackgroundRenderOption.values();
            BackgroundRenderOption next = vals[(current.ordinal() + 1) % vals.length];
            this.backgroundRenderBtn.setMessage(this.getRenderOptionDisplay(next));
            if (info != null) {
                info.setRenderOption(next);
            } else {
                String path = this.backgroundPathBox.getValue();
                if (!path.isEmpty()) {
                    this.currentEntry.setBackgroundImage(new BackgroundImageInfo(path, next));
                }
            }
        }).bounds(fieldX, y + 25 + 25, 80, 16).build();
        this.backgroundAnimBtn = Button.builder(Component.translatable("gui.vn_edit.bg_anim.none"), btn -> {
            if (this.currentEntry == null) {
                return;
            }
            BackgroundImageInfo info = this.currentEntry.getBackgroundImage();
            BackgroundAnimationType current = info != null && info.getAnimationType() != null ? info.getAnimationType() : BackgroundAnimationType.NONE;
            BackgroundAnimationType[] vals = BackgroundAnimationType.values();
            BackgroundAnimationType next = vals[(current.ordinal() + 1) % vals.length];
            this.backgroundAnimBtn.setMessage(this.getBgAnimDisplay(next));
            if (info != null) {
                info.setAnimationType(next);
            } else {
                String path = this.backgroundPathBox.getValue();
                if (!path.isEmpty()) {
                    BackgroundImageInfo newInfo = new BackgroundImageInfo(path, BackgroundRenderOption.FILL, next);
                    this.currentEntry.setBackgroundImage(newInfo);
                }
            }
        }).bounds(fieldX + 85, y + 25 + 25, 80, 16).build();
        this.backgroundPreviewX = fieldX;
        this.backgroundPreviewY = y + 25 + 50;
        this.backgroundPreviewWidth = Math.min(100, fieldWidth - 30);
        this.backgroundPreviewHeight = 56;
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
            return;
        }
        this.backgroundTexture = this.loadTexture(EditorConfig.BACKGROUNDS_DIR.resolve(path).toFile(), "background_" + path);
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
        graphics.drawString(this.font, Component.translatable("gui.vn_edit.background_path"), this.x + 5, this.y + 9, 0xCCCCCC);
        graphics.drawString(this.font, Component.translatable("gui.vn_edit.portraits"), this.x + 5, this.y + 25 + 9, 0xCCCCCC);
        graphics.drawString(this.font, Component.translatable("gui.vn_edit.render_option"), this.x + 5, this.y + 25 + 29, 0xCCCCCC);
        this.backgroundPathBox.render(graphics, mouseX, mouseY, partialTick);
        this.backgroundBrowseBtn.render(graphics, mouseX, mouseY, partialTick);
        this.portraitListBtn.render(graphics, mouseX, mouseY, partialTick);
        this.backgroundRenderBtn.render(graphics, mouseX, mouseY, partialTick);
        this.backgroundAnimBtn.render(graphics, mouseX, mouseY, partialTick);
        if (this.backgroundTexture != null) {
            RenderSystem.setShaderTexture(0, this.backgroundTexture);
            graphics.blit(this.backgroundTexture, this.backgroundPreviewX, this.backgroundPreviewY, 0.0f, 0.0f,
                    this.backgroundPreviewWidth, this.backgroundPreviewHeight, this.backgroundPreviewWidth, this.backgroundPreviewHeight);
        } else {
            graphics.fill(this.backgroundPreviewX, this.backgroundPreviewY,
                    this.backgroundPreviewX + this.backgroundPreviewWidth, this.backgroundPreviewY + this.backgroundPreviewHeight, -11184811);
            graphics.drawCenteredString(this.font, Component.translatable("gui.vn_edit.no_background"),
                    this.backgroundPreviewX + this.backgroundPreviewWidth / 2, this.backgroundPreviewY + 20, 0xCCCCCC);
        }
    }

    @Override
    public List<? extends GuiEventListener> children() {
        return List.of(this.backgroundPathBox, this.backgroundBrowseBtn, this.portraitListBtn, this.backgroundRenderBtn, this.backgroundAnimBtn);
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
        this.backgroundRenderBtn.setMessage(this.getRenderOptionDisplay(BackgroundRenderOption.FILL));
        this.backgroundAnimBtn.setMessage(this.getBgAnimDisplay(BackgroundAnimationType.NONE));
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
        this.backgroundRenderBtn.setMessage(this.getRenderOptionDisplay(option));
        BackgroundAnimationType anim = bgInfo != null && bgInfo.getAnimationType() != null ? bgInfo.getAnimationType() : BackgroundAnimationType.NONE;
        this.backgroundAnimBtn.setMessage(this.getBgAnimDisplay(anim));
        this.loadBackgroundPreview(bgPath);
    }

    @Override
    public void setVisible(boolean visible) {
        this.visible = visible;
        this.backgroundPathBox.setVisible(visible);
        this.backgroundBrowseBtn.visible = visible;
        this.portraitListBtn.visible = visible;
        this.backgroundRenderBtn.visible = visible;
        this.backgroundAnimBtn.visible = visible;
    }

    @Override
    public int getContentHeight() {
        return 160;
    }
}
