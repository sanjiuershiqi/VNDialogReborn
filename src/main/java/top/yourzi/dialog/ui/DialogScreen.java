package top.yourzi.dialog.ui;

import com.mojang.math.Axis;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import org.lwjgl.glfw.GLFW;
import top.yourzi.dialog.Dialog;
import top.yourzi.dialog.DialogManager;
import top.yourzi.dialog.config.ClientConfig;
import top.yourzi.dialog.editor.util.EditorConfig;
import top.yourzi.dialog.editor.util.FileSystemTextureLoader;
import top.yourzi.dialog.model.BackgroundImageInfo;
import top.yourzi.dialog.model.DialogEntry;
import top.yourzi.dialog.model.DialogOption;
import top.yourzi.dialog.model.DialogSequence;
import top.yourzi.dialog.model.DisplayItemInfo;
import top.yourzi.dialog.model.PortraitAnimationType;
import top.yourzi.dialog.model.PortraitInfo;
import top.yourzi.dialog.model.PortraitPosition;
import top.yourzi.dialog.network.NetworkHandler;
import top.yourzi.dialog.util.STBBackendImage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.nio.file.Path;

public class DialogScreen extends Screen {
    private static final int PORTRAIT_ANIMATION_DURATION_MS = 300;
    private static final int PORTRAIT_IMPACT_ANIMATION_DURATION_MS = 450;
    private static final int PORTRAIT_ROTATE_ANIMATION_DURATION_MS = 600;
    private static final int PORTRAIT_FLASH_ANIMATION_DURATION_MS = 800;
    private static final int PORTRAIT_SIDE_MARGIN = 20;
    private static final Map<ResourceLocation, PortraitTextureSize> PORTRAIT_TEXTURE_SIZE_CACHE = new HashMap<>();

    private final DialogSequence dialogSequence;
    private final DialogEntry dialogEntry;
    private final String playerName;
    private final net.minecraft.world.entity.Entity speakerEntity;
    private final List<PortraitRenderInfo> portraits = new ArrayList<>();
    private final List<ItemStack> displayItemStacks = new ArrayList<>();
    private final List<Button> optionButtons = new ArrayList<>();
    private final List<ResourceLocation> dynamicTextures = new ArrayList<>();
    private Button viewHistoryButton;
    private Button autoPlayButton;
    private Button closeHistoryButton;
    private int dialogBoxX;
    private int dialogBoxY;
    private int dialogBoxWidth;
    private int dialogBoxHeight;
    private int currentCharIndex;
    private long lastCharTime;
    private boolean textFullyDisplayed;
    private boolean optionButtonsCreated;
    private boolean showingHistory;
    private boolean upcomingPortraitsPrecached;
    private int protectionHeartbeatTicks;
    private int historyScrollOffset;
    private int totalHistoryContentHeight;
    private ResourceLocation backgroundLocation;

    public DialogScreen(DialogSequence dialogSequence, DialogEntry dialogEntry, String playerName) {
        this(dialogSequence, dialogEntry, playerName, null);
    }

    public DialogScreen(DialogSequence dialogSequence, DialogEntry dialogEntry, String playerName, net.minecraft.world.entity.Entity speakerEntity) {
        super(dialogEntry.getSpeaker(playerName) != null ? dialogEntry.getSpeaker(playerName) : Component.empty());
        this.dialogSequence = dialogSequence;
        this.dialogEntry = dialogEntry;
        this.playerName = playerName;
        this.speakerEntity = speakerEntity;
        collectPortraits();
        collectDisplayItems();
        collectBackground();
        initializeAudio();
    }

    private void collectPortraits() {
        if (dialogEntry.getPortraits() == null) {
            return;
        }
        for (PortraitInfo portrait : dialogEntry.getPortraits()) {
            if (portrait.getPath() == null || portrait.getPath().isEmpty()) {
                continue;
            }
            ResourceLocation texture = getPortraitTextureLocation(portrait.getPath());
            PortraitTextureSize textureSize;
            // 先检查资源包是否有该纹理，如果没有则从编辑器配置目录加载
            FileSystemTextureLoader.LoadedTexture fsTex = tryLoadFromFileSystem(
                    portrait.getPath(), EditorConfig.PORTRAITS_DIR, "portraits");
            if (fsTex != null) {
                texture = fsTex.location();
                textureSize = new PortraitTextureSize(fsTex.width(), fsTex.height());
            } else {
                textureSize = readPortraitTextureSize(texture);
            }
            portraits.add(new PortraitRenderInfo(
                    texture,
                    portrait.getPosition() == null ? PortraitPosition.RIGHT : portrait.getPosition(),
                    Mth.clamp(portrait.getBrightness(), 0.0f, 1.0f),
                    Mth.clamp(portrait.getSize(), 0.1f, 5.0f),
                    portrait.getAnimationType() == null ? PortraitAnimationType.NONE : portrait.getAnimationType(),
                    System.currentTimeMillis(),
                    textureSize.width(),
                    textureSize.height()
            ));
        }
    }

    /**
     * 尝试从编辑器配置目录加载纹理。如果资源包中已有该纹理则返回 null（使用资源包版本）。
     * 替代原 visual_mod_edit_vndialog 的 MixinDialogScreenPortraitDisplayData/BackgroundImageDisplayData 功能。
     */
    private FileSystemTextureLoader.LoadedTexture tryLoadFromFileSystem(String path, Path fsDir, String prefix) {
        ResourceLocation packLocation = ResourceLocation.fromNamespaceAndPath(Dialog.MODID, "textures/" + prefix + "/" + path);
        if (Minecraft.getInstance().getResourceManager().getResource(packLocation).isPresent()) {
            return null;
        }
        java.io.File fsFile = fsDir.resolve(path).toFile();
        if (!fsFile.exists()) {
            return null;
        }
        FileSystemTextureLoader.LoadedTexture loaded = FileSystemTextureLoader.loadAndRegister(fsFile, Dialog.MODID, "textures/" + prefix);
        if (loaded != null) {
            dynamicTextures.add(loaded.location());
        }
        return loaded;
    }

    private ResourceLocation getPortraitTextureLocation(String path) {
        return ResourceLocation.fromNamespaceAndPath(Dialog.MODID, "textures/portraits/" + path);
    }

    private PortraitTextureSize readPortraitTextureSize(ResourceLocation texture) {
        PortraitTextureSize cachedSize = PORTRAIT_TEXTURE_SIZE_CACHE.get(texture);
        if (cachedSize != null) {
            return cachedSize;
        }

        PortraitTextureSize loadedSize;
        try (STBBackendImage image = STBBackendImage.read(texture)) {
            loadedSize = new PortraitTextureSize(image.getWidth(), image.getHeight());
        } catch (Exception e) {
            Dialog.LOGGER.warn("Failed to read portrait texture size for {}, falling back to default aspect ratio.", texture, e);
            loadedSize = PortraitTextureSize.FALLBACK;
        }
        PORTRAIT_TEXTURE_SIZE_CACHE.put(texture, loadedSize);
        return loadedSize;
    }

    private void precacheUpcomingPortraitSizes() {
        if (upcomingPortraitsPrecached) {
            return;
        }

        upcomingPortraitsPrecached = true;
        if (dialogEntry.hasOptions()) {
            DialogOption[] options = dialogEntry.getOptions();
            if (options == null) {
                return;
            }
            for (DialogOption option : options) {
                precachePortraitSizes(dialogSequence.findEntryById(option.getTargetId()));
            }
            return;
        }

        precachePortraitSizes(dialogSequence.getNextEntry(dialogEntry));
    }

    private void precachePortraitSizes(DialogEntry entry) {
        if (entry == null || entry.getPortraits() == null) {
            return;
        }
        for (PortraitInfo portrait : entry.getPortraits()) {
            if (portrait.getPath() != null && !portrait.getPath().isEmpty()) {
                readPortraitTextureSize(getPortraitTextureLocation(portrait.getPath()));
            }
        }
    }

    private void collectDisplayItems() {
        if (dialogEntry.getDisplayItems() == null) {
            return;
        }
        for (DisplayItemInfo itemInfo : dialogEntry.getDisplayItems()) {
            ItemStack stack = createDisplayStack(itemInfo);
            if (!stack.isEmpty()) {
                displayItemStacks.add(stack);
            }
        }
    }

    private ItemStack createDisplayStack(DisplayItemInfo itemInfo) {
        if (itemInfo == null || itemInfo.getItemId() == null || itemInfo.getItemId().isEmpty()) {
            return ItemStack.EMPTY;
        }
        try {
            ResourceLocation itemId = ResourceLocation.parse(itemInfo.getItemId());
            Item item = BuiltInRegistries.ITEM.get(itemId);
            if (item == null || item == Items.AIR) {
                Dialog.LOGGER.warn("Item not found or is AIR: {}", itemInfo.getItemId());
                return ItemStack.EMPTY;
            }

            ItemStack stack = new ItemStack(item, Math.max(1, itemInfo.getCount()));
            if (itemInfo.getNbt() != null && !itemInfo.getNbt().isEmpty()) {
                CompoundTag tag = TagParser.parseTag(itemInfo.getNbt());
                stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            }
            return stack;
        } catch (Exception e) {
            Dialog.LOGGER.error("Error creating ItemStack for display item {}: {}", itemInfo.getItemId(), e.getMessage());
            return ItemStack.EMPTY;
        }
    }

    private void collectBackground() {
        BackgroundImageInfo background = dialogEntry.getBackgroundImage();
        if (background != null && background.getPath() != null && !background.getPath().isEmpty()) {
            FileSystemTextureLoader.LoadedTexture fsTex = tryLoadFromFileSystem(
                    background.getPath(), EditorConfig.BACKGROUNDS_DIR, "backgrounds");
            backgroundLocation = fsTex != null ? fsTex.location()
                    : ResourceLocation.fromNamespaceAndPath(Dialog.MODID, "textures/backgrounds/" + background.getPath());
        }
    }

    private void initializeAudio() {
        if (dialogEntry.getAudioPath() != null && !dialogEntry.getAudioPath().isEmpty()) {
            DialogManager.playDialogAudio(dialogEntry.getAudioPath());
        }
    }

    @Override
    protected void init() {
        super.init();
        dialogBoxWidth = Math.min(ClientConfig.DIALOG_BOX_WIDTH.get(), width - 20);
        dialogBoxHeight = ClientConfig.DIALOG_BOX_HEIGHT.get();
        dialogBoxX = (width - dialogBoxWidth) / 2;
        dialogBoxY = height - dialogBoxHeight - 20;

        int buttonSize = 20;
        int padding = 5;
        viewHistoryButton = Button.builder(Component.literal("H"), button -> toggleHistoryScreen())
                .bounds(dialogBoxX + dialogBoxWidth - buttonSize - padding, dialogBoxY + dialogBoxHeight - buttonSize - padding, buttonSize, buttonSize)
                .build();
        addRenderableWidget(viewHistoryButton);

        autoPlayButton = Button.builder(autoPlayLabel(), button -> toggleAutoPlay())
                .bounds(dialogBoxX + dialogBoxWidth - (buttonSize + padding) * 2, dialogBoxY + dialogBoxHeight - buttonSize - padding, buttonSize, buttonSize)
                .build();
        addRenderableWidget(autoPlayButton);

        closeHistoryButton = Button.builder(Component.translatable("dialog.ui.close_history"), button -> toggleHistoryScreen())
                .bounds(width / 2 - 45, height - 30, 90, 20)
                .build();

        if (dialogEntry.hasOptions()) {
            DialogManager.stopAutoPlay();
            updateAutoPlayButtonText();
        }

        optionButtonsCreated = false;
        protectionHeartbeatTicks = 0;
        sendDialogProtectionHeartbeat();
    }

    private Component autoPlayLabel() {
        return Component.literal(DialogManager.isAutoPlaying() ? "A" : ">");
    }

    private void createOptionButtons() {
        optionButtons.clear();
        DialogOption[] options = dialogEntry.getOptions();
        if (options == null || options.length == 0) {
            return;
        }

        int buttonWidth = Math.min(240, width - 40);
        int buttonHeight = 20;
        int spacing = 5;
        int startY = dialogBoxY - options.length * (buttonHeight + spacing) - 10;
        if (!displayItemStacks.isEmpty()) {
            startY -= 30;
        }

        for (int i = 0; i < options.length; i++) {
            DialogOption option = options[i];
            Button button = Button.builder(option.getText(playerName), b -> {
                if (option.getCommand() != null && !option.getCommand().isEmpty()) {
                    DialogManager.getInstance().executeCommands(minecraft.player, option.getCommand(), speakerEntity);
                }
                DialogManager.getInstance().recordChoiceForCurrentDialog(option.getText(playerName).getString());
                DialogManager.getInstance().jumpToDialog(option.getTargetId());
            }).bounds((width - buttonWidth) / 2, startY + i * (buttonHeight + spacing), buttonWidth, buttonHeight).build();
            optionButtons.add(button);
            addRenderableWidget(button);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        if (backgroundLocation != null) {
            guiGraphics.blit(backgroundLocation, 0, 0, width, height, 0, 0, width, height, width, height);
        } else {
            renderWorldOverlay(guiGraphics);
        }

        if (showingHistory) {
            renderHistoryScreen(guiGraphics);
            closeHistoryButton.render(guiGraphics, mouseX, mouseY, partialTicks);
            renderFlashOverlay(guiGraphics);
            return;
        }

        renderPortraits(guiGraphics);
        renderDialogBox(guiGraphics);

        if (textFullyDisplayed && dialogEntry.hasOptions() && !optionButtonsCreated) {
            createOptionButtons();
            optionButtonsCreated = true;
        }

        super.render(guiGraphics, mouseX, mouseY, partialTicks);
        renderFlashOverlay(guiGraphics);
    }

    private void renderWorldOverlay(GuiGraphics guiGraphics) {
        guiGraphics.fill(0, 0, width, height, 0x66000000);
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
    }

    @Override
    protected void renderBlurredBackground(float partialTicks) {
    }

    private void renderPortraits(GuiGraphics guiGraphics) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        for (PortraitRenderInfo portrait : portraits) {
            int portraitHeight = (int) (height * 0.68f * portrait.size);
            int portraitWidth = Math.max(1, (int) (portraitHeight * portrait.aspectRatio()));
            int x = switch (portrait.position) {
                case LEFT -> PORTRAIT_SIDE_MARGIN;
                case CENTER -> (width - portraitWidth) / 2;
                case RIGHT -> width - portraitWidth - PORTRAIT_SIDE_MARGIN;
            };
            PortraitAnimationFrame animation = getPortraitAnimationFrame(portrait, x, portraitWidth);
            int y = animation.topAligned ? 0 : height - portraitHeight;
            int renderX = x + animation.xOffset;
            int renderY = y + animation.yOffset;
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderColor(portrait.brightness, portrait.brightness, portrait.brightness, animation.alpha);
            if (animation.rotationDegrees != 0.0f) {
                guiGraphics.pose().pushPose();
                guiGraphics.pose().translate(renderX + portraitWidth / 2.0f, renderY + portraitHeight / 2.0f, 0.0f);
                guiGraphics.pose().mulPose(Axis.ZP.rotationDegrees(animation.rotationDegrees));
                guiGraphics.pose().translate(-(renderX + portraitWidth / 2.0f), -(renderY + portraitHeight / 2.0f), 0.0f);
                guiGraphics.blit(portrait.texture, renderX, renderY, portraitWidth, portraitHeight, 0, 0, portraitWidth, portraitHeight, portraitWidth, portraitHeight);
                guiGraphics.pose().popPose();
            } else {
                guiGraphics.blit(portrait.texture, renderX, renderY, portraitWidth, portraitHeight, 0, 0, portraitWidth, portraitHeight, portraitWidth, portraitHeight);
            }
        }
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableBlend();
    }

    private PortraitAnimationFrame getPortraitAnimationFrame(PortraitRenderInfo portrait, int baseX, int portraitWidth) {
        if (!ClientConfig.ENABLE_PORTRAIT_ANIMATIONS.get() || portrait.animationType == PortraitAnimationType.NONE) {
            return PortraitAnimationFrame.NONE;
        }

        if (portrait.animationType == PortraitAnimationType.REVERSE) {
            return PortraitAnimationFrame.REVERSE;
        }

        long elapsed = System.currentTimeMillis() - portrait.animationStartTime;
        int duration = getPortraitAnimationDurationMs(portrait.animationType);
        if (elapsed >= duration) {
            return PortraitAnimationFrame.NONE;
        }

        float progress = Mth.clamp((float) elapsed / duration, 0.0f, 1.0f);
        return switch (portrait.animationType) {
            case FADE_IN -> new PortraitAnimationFrame(0, 0, progress);
            case SLIDE_IN_FROM_BOTTOM -> new PortraitAnimationFrame(0, (int) Mth.lerp(progress, 50.0f, 0.0f), 1.0f);
            case BOUNCE -> {
                float yOffset = progress < 0.5f
                        ? Mth.lerp(progress * 2.0f, 0.0f, -20.0f)
                        : Mth.lerp((progress - 0.5f) * 2.0f, -20.0f, 0.0f);
                yield new PortraitAnimationFrame(0, (int) yOffset, 1.0f);
            }
            case IMPACT -> getImpactAnimationFrame(portrait.position, baseX, portraitWidth, progress, false);
            case IMPACT_MAX -> getImpactAnimationFrame(portrait.position, baseX, portraitWidth, progress, true);
            case ROTATE -> new PortraitAnimationFrame(0, 0, 1.0f, progress * 360.0f, false);
            case REVERSE -> PortraitAnimationFrame.REVERSE;
            case FLASH -> PortraitAnimationFrame.NONE;
            case NONE -> PortraitAnimationFrame.NONE;
        };
    }

    private int getPortraitAnimationDurationMs(PortraitAnimationType animationType) {
        return switch (animationType) {
            case IMPACT, IMPACT_MAX -> PORTRAIT_IMPACT_ANIMATION_DURATION_MS;
            case ROTATE -> PORTRAIT_ROTATE_ANIMATION_DURATION_MS;
            case FLASH -> PORTRAIT_FLASH_ANIMATION_DURATION_MS;
            case NONE, FADE_IN, SLIDE_IN_FROM_BOTTOM, BOUNCE, REVERSE -> PORTRAIT_ANIMATION_DURATION_MS;
        };
    }

    private PortraitAnimationFrame getImpactAnimationFrame(PortraitPosition position, int baseX, int portraitWidth, float progress, boolean maxImpact) {
        int targetXOffset = maxImpact
                ? getImpactMaxXOffset(position, baseX, portraitWidth)
                : getImpactXOffset(position);
        if (targetXOffset == 0) {
            return PortraitAnimationFrame.NONE;
        }

        float travel = Mth.sin(progress * (float) Math.PI);
        int verticalDistance = Math.max(18, (int) (height * 0.08f));
        int xOffset = Math.round(targetXOffset * travel);
        int yOffset = -Math.round(verticalDistance * travel);
        return new PortraitAnimationFrame(xOffset, yOffset, 1.0f);
    }

    private int getImpactXOffset(PortraitPosition position) {
        int distance = Math.max(1, width / 2);
        return switch (position) {
            case LEFT -> distance;
            case RIGHT -> -distance;
            case CENTER -> 0;
        };
    }

    private int getImpactMaxXOffset(PortraitPosition position, int baseX, int portraitWidth) {
        int targetX = switch (position) {
            case LEFT -> width - portraitWidth - PORTRAIT_SIDE_MARGIN;
            case RIGHT -> PORTRAIT_SIDE_MARGIN;
            case CENTER -> baseX;
        };
        int targetOffset = targetX - baseX;
        return switch (position) {
            case LEFT -> Math.max(0, targetOffset);
            case RIGHT -> Math.min(0, targetOffset);
            case CENTER -> 0;
        };
    }

    private void renderFlashOverlay(GuiGraphics guiGraphics) {
        if (!ClientConfig.ENABLE_PORTRAIT_ANIMATIONS.get()) {
            return;
        }

        float alpha = 0.0f;
        long now = System.currentTimeMillis();
        for (PortraitRenderInfo portrait : portraits) {
            if (portrait.animationType != PortraitAnimationType.FLASH) {
                continue;
            }

            long elapsed = now - portrait.animationStartTime;
            if (elapsed < 0 || elapsed >= PORTRAIT_FLASH_ANIMATION_DURATION_MS) {
                continue;
            }

            float progress = Mth.clamp((float) elapsed / PORTRAIT_FLASH_ANIMATION_DURATION_MS, 0.0f, 1.0f);
            float pulse = Mth.sin(progress * (float) Math.PI * 4.0f);
            alpha = Math.max(alpha, Math.max(0.0f, pulse));
        }

        if (alpha <= 0.0f) {
            return;
        }

        int alphaChannel = Mth.clamp(Math.round(alpha * 210.0f), 0, 210);
        guiGraphics.fill(0, 0, width, height, (alphaChannel << 24) | 0xFFFFFF);
    }

    private void renderDialogBox(GuiGraphics guiGraphics) {
        int backgroundColor = ClientConfig.DIALOG_BACKGROUND_COLOR.get();
        int opacity = ClientConfig.DIALOG_BACKGROUND_OPACITY.get();
        int color = (opacity << 24) | (backgroundColor & 0xFFFFFF);
        guiGraphics.fill(dialogBoxX, dialogBoxY, dialogBoxX + dialogBoxWidth, dialogBoxY + dialogBoxHeight, color);

        int padding = ClientConfig.DIALOG_BOX_PADDING.get();
        int textX = dialogBoxX + padding;
        int textY = dialogBoxY + padding;

        Component speaker = dialogEntry.getSpeaker(playerName);
        if (ClientConfig.SHOW_SPEAKER_NAME.get() && speaker != null && !speaker.getString().isEmpty()) {
            guiGraphics.drawString(font, speaker, textX, textY, 0xFFFFFF);
            textY += font.lineHeight + 5;
        }

        Component text = dialogEntry.getText(playerName);
        String rawText = text.getString();
        advanceText(rawText);
        int displayedChars = Math.min(currentCharIndex, rawText.length());
        boolean fullyShown = currentCharIndex >= rawText.length();
        // 按字符索引截断 Component，保留样式（颜色/加粗等），避免 getString() 丢样式
        Component displayComponent = fullyShown ? text : substringComponent(text, displayedChars);

        for (FormattedCharSequence line : font.split(displayComponent, dialogBoxWidth - padding * 2)) {
            guiGraphics.drawString(font, line, textX, textY, ClientConfig.DIALOG_TEXT_COLOR.get());
            textY += font.lineHeight + 2;
        }

        if (!displayItemStacks.isEmpty() && textFullyDisplayed) {
            renderItems(guiGraphics);
        }

        if (!dialogEntry.hasOptions() && textFullyDisplayed) {
            Component indicator = Component.literal(">>");
            int indicatorWidth = font.width(indicator);
            int indicatorX = autoPlayButton == null
                    ? dialogBoxX + dialogBoxWidth - indicatorWidth - padding
                    : autoPlayButton.getX() - indicatorWidth - 8;
            guiGraphics.drawString(font, indicator, Math.max(textX, indicatorX), dialogBoxY + dialogBoxHeight - 18, 0xFFFFFF);
        }
    }

    /**
     * 按字符索引截断 Component，保留各段样式（颜色/加粗等）。
     * 用于打字机动画效果中逐字显示带样式的文本。
     */
    private Component substringComponent(Component component, int maxChars) {
        MutableComponent result = Component.empty();
        int[] remaining = {maxChars};
        component.visit((style, textPart) -> {
            if (remaining[0] <= 0) {
                return java.util.Optional.empty();
            }
            int len = textPart.length();
            if (len <= remaining[0]) {
                result.append(Component.literal(textPart).setStyle(style));
                remaining[0] -= len;
            } else {
                result.append(Component.literal(textPart.substring(0, remaining[0])).setStyle(style));
                remaining[0] = 0;
            }
            return java.util.Optional.empty();
        }, Style.EMPTY);
        return result;
    }

    private void advanceText(String rawText) {
        int speed = ClientConfig.TEXT_ANIMATION_SPEED.get();
        if (speed <= 0) {
            textFullyDisplayed = true;
            currentCharIndex = rawText.length();
            return;
        }

        if (!textFullyDisplayed) {
            long now = System.currentTimeMillis();
            if (lastCharTime == 0) {
                lastCharTime = now;
            }
            long interval = Math.max(1, 1000L / speed);
            if (now - lastCharTime >= interval) {
                currentCharIndex++;
                lastCharTime = now;
                if (currentCharIndex >= rawText.length()) {
                    currentCharIndex = rawText.length();
                    textFullyDisplayed = true;
                }
            }
        }
    }

    private void renderItems(GuiGraphics guiGraphics) {
        int itemSize = 16;
        int itemPadding = 4;
        int totalWidth = displayItemStacks.size() * itemSize + Math.max(0, displayItemStacks.size() - 1) * itemPadding;
        int startX = dialogBoxX + (dialogBoxWidth - totalWidth) / 2;
        int y = dialogBoxY - itemSize - 5;
        for (int i = 0; i < displayItemStacks.size(); i++) {
            int x = startX + i * (itemSize + itemPadding);
            ItemStack stack = displayItemStacks.get(i);
            guiGraphics.renderItem(stack, x, y);
            guiGraphics.renderItemDecorations(font, stack, x, y);
        }
    }

    private void renderHistoryScreen(GuiGraphics guiGraphics) {
        guiGraphics.fill(0, 0, width, height, 0xDD000000);
        List<DialogEntry> history = DialogManager.getInstance().getDialogHistory();
        int top = (int) (height * 0.1);
        int bottom = height - 40;
        int y = top - historyScrollOffset;
        totalHistoryContentHeight = 0;

        for (DialogEntry entry : history) {
            Component speaker = entry.getSpeaker(playerName);
            Component text = entry.getText(playerName);
            Component line = speaker != null && !speaker.getString().isEmpty()
                    ? Component.literal("[").append(speaker).append("] ").append(text)
                    : text;

            for (FormattedCharSequence wrapped : font.split(line, width - 90)) {
                if (y + font.lineHeight > top && y < bottom) {
                    guiGraphics.drawString(font, wrapped, 50, y, 0xFFFFFF);
                }
                y += font.lineHeight + 2;
                totalHistoryContentHeight += font.lineHeight + 2;
            }

            if (entry.getSelectedOptionText() != null && !entry.getSelectedOptionText().isEmpty()) {
                Component option = Component.literal(" -> " + entry.getSelectedOptionText());
                for (FormattedCharSequence wrapped : font.split(option, width - 100)) {
                    if (y + font.lineHeight > top && y < bottom) {
                        guiGraphics.drawString(font, wrapped, 60, y, 0xAAAAAA);
                    }
                    y += font.lineHeight + 2;
                    totalHistoryContentHeight += font.lineHeight + 2;
                }
            }
            y += 8;
            totalHistoryContentHeight += 8;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return ClientConfig.IS_PAUSE_SCREEN.get();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (dialogSequence.isCloseAllowed()) {
                onClose();
            } else {
                minecraft.setScreen(new ConfirmScreen(confirmed -> {
                    if (confirmed) {
                        onClose();
                    } else {
                        minecraft.setScreen(this);
                    }
                }, Component.translatable("dialog.ui.esc"), Component.translatable("dialog.ui.confirm_esc")));
            }
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_LEFT_CONTROL || keyCode == GLFW.GLFW_KEY_RIGHT_CONTROL) {
            if (textFullyDisplayed) {
                DialogManager.setFastForwardingNext(true);
                DialogManager.getInstance().showNextDialog();
            } else {
                currentCharIndex = dialogEntry.getText(playerName).getString().length();
                textFullyDisplayed = true;
            }
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (showingHistory) {
            if (closeHistoryButton.isMouseOver(mouseX, mouseY)) {
                return closeHistoryButton.mouseClicked(mouseX, mouseY, button);
            }
            return true;
        }

        if (button == 0 && isDialogBoxClick(mouseX, mouseY) && !isDialogControlClick(mouseX, mouseY)) {
            return handleDialogAdvanceClick();
        }

        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        if (button == 0) {
            return handleDialogAdvanceClick();
        }

        return false;
    }

    private boolean isDialogBoxClick(double mouseX, double mouseY) {
        return mouseX >= dialogBoxX && mouseX <= dialogBoxX + dialogBoxWidth
                && mouseY >= dialogBoxY && mouseY <= dialogBoxY + dialogBoxHeight;
    }

    private boolean isDialogControlClick(double mouseX, double mouseY) {
        return isButtonUnderMouse(viewHistoryButton, mouseX, mouseY)
                || isButtonUnderMouse(autoPlayButton, mouseX, mouseY);
    }

    private boolean isButtonUnderMouse(Button button, double mouseX, double mouseY) {
        return button != null && button.isMouseOver(mouseX, mouseY);
    }

    private boolean handleDialogAdvanceClick() {
        if (!textFullyDisplayed) {
            currentCharIndex = dialogEntry.getText(playerName).getString().length();
            textFullyDisplayed = true;
        } else if (!dialogEntry.hasOptions()) {
            DialogManager.getInstance().executeCommands(minecraft.player, dialogEntry.getCommands(), speakerEntity);
            DialogManager.getInstance().showNextDialog();
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (showingHistory) {
            int historyAreaHeight = height - 40 - (int) (height * 0.1);
            int maxScroll = Math.max(0, totalHistoryContentHeight - historyAreaHeight);
            historyScrollOffset = Mth.clamp(historyScrollOffset + (int) (-scrollY * font.lineHeight * 2), 0, maxScroll);
            return true;
        }
        if (scrollY > 0) {
            toggleHistoryScreen();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void tick() {
        super.tick();
        precacheUpcomingPortraitSizes();
        protectionHeartbeatTicks++;
        if (protectionHeartbeatTicks >= 20) {
            protectionHeartbeatTicks = 0;
            sendDialogProtectionHeartbeat();
        }
        if (DialogManager.isAutoPlaying() && !dialogEntry.hasOptions() && textFullyDisplayed) {
            if (DialogManager.isAudioFinished()) {
                DialogManager.getInstance().showNextDialog();
            }
        }
    }

    @Override
    public void onClose() {
        DialogManager.stopAutoPlay();
        DialogManager.stopCurrentAudio();
        for (ResourceLocation tex : dynamicTextures) {
            Minecraft.getInstance().getTextureManager().release(tex);
        }
        dynamicTextures.clear();
        super.onClose();
    }

    private void toggleAutoPlay() {
        DialogManager.setAutoPlaying(!DialogManager.isAutoPlaying());
        updateAutoPlayButtonText();
    }

    private void sendDialogProtectionHeartbeat() {
        NetworkHandler.sendDialogProtectionHeartbeatToServer(dialogSequence.getEffect());
    }

    private void updateAutoPlayButtonText() {
        if (autoPlayButton != null) {
            autoPlayButton.setMessage(autoPlayLabel());
        }
    }

    private void toggleHistoryScreen() {
        showingHistory = !showingHistory;
        historyScrollOffset = 0;
        if (showingHistory) {
            viewHistoryButton.active = false;
            autoPlayButton.active = false;
            if (!children().contains(closeHistoryButton)) {
                addRenderableWidget(closeHistoryButton);
            }
        } else {
            viewHistoryButton.active = true;
            autoPlayButton.active = true;
            if (children().contains(closeHistoryButton)) {
                removeWidget(closeHistoryButton);
            }
        }
    }

    private record PortraitRenderInfo(ResourceLocation texture, PortraitPosition position, float brightness, float size,
                                      PortraitAnimationType animationType, long animationStartTime,
                                      int textureWidth, int textureHeight) {
        private float aspectRatio() {
            return textureWidth > 0 && textureHeight > 0 ? (float) textureWidth / textureHeight : PortraitTextureSize.FALLBACK.aspectRatio();
        }
    }

    private record PortraitTextureSize(int width, int height) {
        private static final PortraitTextureSize FALLBACK = new PortraitTextureSize(65, 100);

        private float aspectRatio() {
            return (float) width / height;
        }
    }

    private record PortraitAnimationFrame(int xOffset, int yOffset, float alpha, float rotationDegrees, boolean topAligned) {
        private static final PortraitAnimationFrame NONE = new PortraitAnimationFrame(0, 0, 1.0f);
        private static final PortraitAnimationFrame REVERSE = new PortraitAnimationFrame(0, 0, 1.0f, 180.0f, true);

        private PortraitAnimationFrame(int xOffset, int yOffset, float alpha) {
            this(xOffset, yOffset, alpha, 0.0f, false);
        }
    }
}
