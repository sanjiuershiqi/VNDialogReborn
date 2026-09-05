package top.yourzi.dialog.editor.gui.property;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import top.yourzi.dialog.editor.gui.VNDialogEditorScreen;
import top.yourzi.dialog.editor.gui.widget.EditorButton;
import top.yourzi.dialog.editor.gui.widget.MultiLineEditBox;
import top.yourzi.dialog.editor.gui.widget.ThemedEditBox;
import top.yourzi.dialog.editor.util.EditorConfig;
import top.yourzi.dialog.editor.util.EditorTheme;
import top.yourzi.dialog.editor.util.LangFileGenerator;
import top.yourzi.dialog.editor.util.PageLayout;
import top.yourzi.dialog.model.DialogEntry;
import top.yourzi.dialog.util.ComponentJson;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 文本属性页：说话者、正文、格式化代码、翻译模式。融合自 visual_mod_edit_vndialog。
 * 使用 PageLayout 游标布局，自动适应不同屏幕尺寸和 GUI 缩放。
 */
public class TextPropertyPage extends AbstractPropertyPage {
    private static final int MODE_PLAIN = 0;
    private static final int MODE_TRANSLATION = 1;
    private static final ChatFormatting[] COLORS = new ChatFormatting[]{
            ChatFormatting.BLACK, ChatFormatting.DARK_BLUE, ChatFormatting.DARK_GREEN, ChatFormatting.DARK_AQUA,
            ChatFormatting.DARK_RED, ChatFormatting.DARK_PURPLE, ChatFormatting.GOLD, ChatFormatting.GRAY,
            ChatFormatting.DARK_GRAY, ChatFormatting.BLUE, ChatFormatting.GREEN, ChatFormatting.AQUA,
            ChatFormatting.RED, ChatFormatting.LIGHT_PURPLE, ChatFormatting.YELLOW, ChatFormatting.WHITE
    };
    private static final Gson GSON = new Gson();

    private EditBox speakerBox;
    private MultiLineEditBox contentBox;
    private boolean textModified = false;
    private EditorButton boldBtn;
    private EditorButton italicBtn;
    private EditorButton underlineBtn;
    private EditorButton strikethroughBtn;
    private EditorButton obfuscatedBtn;
    private EditorButton resetBtn;
    private EditorButton clearBtn;
    private EditBox hexColorBox;
    private EditorButton applyHexBtn;
    private final List<EditorButton> colorButtons = new ArrayList<>();
    private int currentMode = MODE_PLAIN;
    public EditorButton modeSwitchBtn;
    private EditorButton generateLangBtn;
    private EditBox translationKeyBox;
    private EditBox translationZhCnBox;
    private EditBox translationEnUsBox;
    private int computedHeight = 0;

    // 渲染位置缓存（init 阶段计算，render 阶段使用）
    private int speakerHeaderY;
    private int speakerLabelY;
    private int contentHeaderY;
    private int contentLabelY;
    private int formatHeaderY;
    private int translationHeaderY;
    private int hexLabelY;

    public TextPropertyPage(Font font) {
        super(font);
    }

    @Override
    public void init(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;

        PageLayout layout = new PageLayout(x, y, width);
        int fieldX = layout.fieldX();
        int fieldW = layout.fieldWidth();

        // ===== 说话者分节 =====
        this.speakerHeaderY = layout.section();
        int speakerY = layout.fieldRow();
        this.speakerLabelY = speakerY + 4;
        this.speakerBox = new ThemedEditBox(this.font, fieldX, speakerY, fieldW, EditorTheme.FIELD_HEIGHT, Component.translatable("gui.vn_edit.speaker"));
        this.speakerBox.setMaxLength(999999999);
        this.speakerBox.setEditable(true);
        this.speakerBox.setResponder(s -> {
            if (this.currentEntry != null) {
                MutableComponent component = this.parseFormattingCodesToComponent(s);
                this.currentEntry.setSpeaker(ComponentJson.toJsonTree(component));
            }
            this.notifyDirty();
        });

        // ===== 正文 / 翻译分节（根据模式不同位置不同）=====
        // 正文模式布局
        this.contentHeaderY = layout.section();
        int contentY = layout.customRow(EditorTheme.CONTENT_BOX_H);
        this.contentLabelY = contentY + 4;
        this.contentBox = new MultiLineEditBox(this.font, fieldX, contentY, fieldW, EditorTheme.CONTENT_BOX_H);
        this.contentBox.setResponder(s -> { this.saveTextToEntry(); this.notifyDirty(); });

        // 模式切换按钮
        int modeY = layout.fieldRow();
        this.modeSwitchBtn = new EditorButton(fieldX, modeY, 60, EditorTheme.FIELD_HEIGHT, Component.translatable("gui.vn_edit.mode_plain"), b -> this.toggleMode());

        // ===== 翻译分节（翻译模式下显示）=====
        this.translationHeaderY = layout.section();
        int transKeyY = layout.fieldRow();
        this.translationKeyBox = new ThemedEditBox(this.font, fieldX, transKeyY, fieldW, EditorTheme.FIELD_HEIGHT, Component.translatable("gui.vn_edit.translation_key"));
        this.translationKeyBox.setMaxLength(999999999);
        this.translationKeyBox.setResponder(s -> { this.saveTranslationToEntry(); this.notifyDirty(); });
        int transZhY = layout.fieldRow();
        this.translationZhCnBox = new ThemedEditBox(this.font, fieldX, transZhY, fieldW, EditorTheme.FIELD_HEIGHT, Component.translatable("gui.vn_edit.translation_zh_cn"));
        this.translationZhCnBox.setMaxLength(999999999);
        this.translationZhCnBox.setResponder(s -> { this.saveTranslationToEntry(); this.notifyDirty(); });
        int transEnY = layout.fieldRow();
        this.translationEnUsBox = new ThemedEditBox(this.font, fieldX, transEnY, fieldW, EditorTheme.FIELD_HEIGHT, Component.translatable("gui.vn_edit.translation_en_us"));
        this.translationEnUsBox.setMaxLength(999999999);
        this.translationEnUsBox.setResponder(s -> { this.saveTranslationToEntry(); this.notifyDirty(); });
        int genLangY = layout.fieldRow();
        this.generateLangBtn = new EditorButton(fieldX, genLangY, 80, EditorTheme.FIELD_HEIGHT, Component.translatable("gui.vn_edit.generate_lang"), b -> this.generateLangFiles());

        // ===== 格式分节（正文模式下显示）=====
        this.formatHeaderY = layout.section();

        // 颜色按钮网格（自动换行）
        int btnSize = EditorTheme.COLOR_BTN_SZ;
        int gap = EditorTheme.COLOR_BTN_GAP;
        int[][] colorPos = layout.gridLayout(COLORS.length, btnSize, btnSize, gap);
        this.colorButtons.clear();
        for (int i = 0; i < COLORS.length; i++) {
            ChatFormatting color = COLORS[i];
            int rgb = color.getColor() != null ? color.getColor() : 0xFFFFFF;
            final int idx = i;
            EditorButton btn = new EditorButton(colorPos[i][0], colorPos[i][1], btnSize, btnSize,
                    Component.literal("\u25a0").withStyle(Style.EMPTY.withColor(rgb)),
                    b -> this.contentBox.insertAtCursor("\u00a7" + COLORS[idx].getChar()));
            this.colorButtons.add(btn);
        }

        // 格式按钮行（B/I/U/S/O/R + 清除格式），自动换行
        List<int[]> formatSizes = new ArrayList<>();
        formatSizes.add(new int[]{btnSize, btnSize}); // B
        formatSizes.add(new int[]{btnSize, btnSize}); // I
        formatSizes.add(new int[]{btnSize, btnSize}); // U
        formatSizes.add(new int[]{btnSize, btnSize}); // S
        formatSizes.add(new int[]{btnSize, btnSize}); // O
        formatSizes.add(new int[]{btnSize, btnSize}); // R
        formatSizes.add(new int[]{80, EditorTheme.FIELD_HEIGHT}); // Clear
        int[][] formatPos = layout.rowLayout(formatSizes, gap);
        this.boldBtn = this.makeBtn("B", Style.EMPTY.withBold(true), 'l', formatPos[0][0], formatPos[0][1]);
        this.italicBtn = this.makeBtn("I", Style.EMPTY.withItalic(true), 'o', formatPos[1][0], formatPos[1][1]);
        this.underlineBtn = this.makeBtn("U", Style.EMPTY.withUnderlined(true), 'n', formatPos[2][0], formatPos[2][1]);
        this.strikethroughBtn = this.makeBtn("S", Style.EMPTY.withStrikethrough(true), 'm', formatPos[3][0], formatPos[3][1]);
        this.obfuscatedBtn = this.makeBtn("O", Style.EMPTY.withObfuscated(true), 'k', formatPos[4][0], formatPos[4][1]);
        this.resetBtn = this.makeBtn("R", Style.EMPTY.withColor(0xAAAAAA), 'r', formatPos[5][0], formatPos[5][1]);
        this.clearBtn = new EditorButton(formatPos[6][0], formatPos[6][1], 80, EditorTheme.FIELD_HEIGHT, Component.translatable("gui.vn_edit.clear_format"), b -> this.clearFormatting());

        // 十六进制颜色输入行
        int hexY = layout.fieldRow();
        this.hexLabelY = hexY + 4;
        int hexBoxW = Math.min(50, fieldW / 3);
        this.hexColorBox = new ThemedEditBox(this.font, fieldX, hexY, hexBoxW, EditorTheme.FIELD_HEIGHT, Component.translatable("gui.vn_edit.hex_color"));
        this.hexColorBox.setMaxLength(7);
        this.hexColorBox.setValue("#");
        this.applyHexBtn = new EditorButton(fieldX + hexBoxW + gap, hexY, 30, EditorTheme.FIELD_HEIGHT, Component.translatable("gui.vn_edit.apply"), b -> {
            String hex = this.hexColorBox.getValue().trim();
            if (hex.startsWith("#") && hex.length() == 7) {
                this.contentBox.insertAtCursor("\u00a7x");
                for (int i = 1; i < hex.length(); i++) {
                    this.contentBox.insertAtCursor("\u00a7" + hex.charAt(i));
                }
            }
        });

        this.computedHeight = layout.getContentHeight();
        this.updateVisibility();
    }

    private void toggleMode() {
        if (this.currentMode == MODE_PLAIN) {
            this.currentMode = MODE_TRANSLATION;
            this.modeSwitchBtn.setMessage(Component.translatable("gui.vn_edit.mode_translation"));
            this.speakerBox.setFocused(false);
            this.contentBox.setFocused(false);
            this.translationZhCnBox.setFocused(false);
            this.translationEnUsBox.setFocused(false);
            this.translationKeyBox.setFocused(true);
        } else {
            this.currentMode = MODE_PLAIN;
            this.modeSwitchBtn.setMessage(Component.translatable("gui.vn_edit.mode_plain"));
            this.speakerBox.setFocused(true);
            this.translationKeyBox.setFocused(false);
            this.translationZhCnBox.setFocused(false);
            this.translationEnUsBox.setFocused(false);
        }
        if (this.modeSwitchBtn != null) {
            this.modeSwitchBtn.setFocused(false);
        }
        this.updateVisibility();
        this.saveToEntryBasedOnMode();
    }

    private void updateVisibility() {
        boolean isPlain = this.currentMode == MODE_PLAIN;
        this.contentBox.visible = isPlain;
        this.boldBtn.visible = isPlain;
        this.italicBtn.visible = isPlain;
        this.underlineBtn.visible = isPlain;
        this.strikethroughBtn.visible = isPlain;
        this.obfuscatedBtn.visible = isPlain;
        this.resetBtn.visible = isPlain;
        this.clearBtn.visible = isPlain;
        this.hexColorBox.setVisible(isPlain);
        this.applyHexBtn.visible = isPlain;
        this.colorButtons.forEach(b -> b.visible = isPlain);
        boolean isTranslation = this.currentMode == MODE_TRANSLATION;
        this.translationKeyBox.setVisible(isTranslation);
        this.translationZhCnBox.setVisible(isTranslation);
        this.translationEnUsBox.setVisible(isTranslation);
        this.generateLangBtn.visible = isTranslation;
    }

    private void saveTextToEntry() {
        if (this.currentEntry == null || this.currentMode != MODE_PLAIN) {
            return;
        }
        this.textModified = true;
        String rawText = this.contentBox.getValue();
        MutableComponent component = this.parseFormattingCodesToComponent(rawText);
        this.currentEntry.setText(ComponentJson.toJsonTree(component));
    }

    private void saveTranslationToEntry() {
        if (this.currentEntry == null || this.currentMode != MODE_TRANSLATION) {
            return;
        }
        String key = this.translationKeyBox.getValue().trim();
        if (key.isEmpty()) {
            this.currentEntry.setText(null);
        } else {
            JsonElement currentText = this.currentEntry.getText();
            JsonObject json;
            if (currentText != null && currentText.isJsonObject()) {
                json = currentText.getAsJsonObject().deepCopy();
            } else {
                json = new JsonObject();
            }
            json.addProperty("translate", key);
            this.currentEntry.setText(json);
        }
    }

    private void saveToEntryBasedOnMode() {
        if (this.currentMode == MODE_PLAIN) {
            this.saveTextToEntry();
        } else {
            this.saveTranslationToEntry();
        }
    }

    private void generateLangFiles() {
        if (this.currentEntry == null || this.currentMode != MODE_TRANSLATION) {
            return;
        }
        String key = this.translationKeyBox.getValue().trim();
        String zhCn = this.translationZhCnBox.getValue().trim();
        String enUs = this.translationEnUsBox.getValue().trim();
        if (!key.isEmpty()) {
            LangFileGenerator.saveTranslation(key, zhCn, enUs);
            Screen screen = Minecraft.getInstance().screen;
            if (screen instanceof VNDialogEditorScreen editor) {
                editor.statusText = Component.translatable("gui.vn_edit.lang_generated").getString();
            }
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.displayClientMessage(Component.translatable("gui.vn_edit.lang_generated"), false);
            }
        }
    }

    @Override
    public void bindTo(DialogEntry entry) {
        this.currentEntry = entry;
        // 回填期间抑制 notifyDirty，避免 refreshDisplay 内 setValue 误触发 markDirty（打开即标记 *）
        this.beginSilentRefresh();
        try {
            this.refreshDisplay();
        } finally {
            this.endSilentRefresh();
        }
    }

    @Override
    public void unbind() {
        if (this.textModified) {
            this.saveToEntryBasedOnMode();
        }
        this.textModified = false;
        this.currentEntry = null;
        this.speakerBox.setValue("");
        this.contentBox.setValueSilently("");
        this.translationKeyBox.setValue("");
        this.translationZhCnBox.setValue("");
        this.translationEnUsBox.setValue("");
        this.hexColorBox.setValue("#");
        this.currentMode = MODE_PLAIN;
        this.modeSwitchBtn.setMessage(Component.translatable("gui.vn_edit.mode_plain"));
        this.updateVisibility();
    }

    @Override
    public void refreshDisplay() {
        if (this.currentEntry == null) {
            this.unbind();
            return;
        }
        String speakerStr = "";
        JsonElement speakerJson = this.currentEntry.getSpeaker();
        if (speakerJson != null && !speakerJson.isJsonNull()) {
            if (speakerJson.isJsonPrimitive()) {
                speakerStr = speakerJson.getAsString();
            } else {
                Component comp = ComponentJson.fromJson(speakerJson);
                speakerStr = comp != null ? this.componentToFormattingCodes(comp) : speakerJson.toString();
            }
        }
        this.setBoxSilent(this.speakerBox, speakerStr, s -> {
            if (this.currentEntry != null) {
                MutableComponent component = this.parseFormattingCodesToComponent(s);
                this.currentEntry.setSpeaker(ComponentJson.toJsonTree(component));
            }
        });
        JsonElement textJson = this.currentEntry.getText();
        boolean isTranslation = false;
        String transKey = "";
        if (textJson != null && textJson.isJsonObject()) {
            JsonObject obj = textJson.getAsJsonObject();
            if (obj.has("translate")) {
                isTranslation = true;
                transKey = obj.get("translate").getAsString();
            }
        }
        if (isTranslation) {
            this.currentMode = MODE_TRANSLATION;
            this.modeSwitchBtn.setMessage(Component.translatable("gui.vn_edit.mode_translation"));
            this.translationKeyBox.setValue(transKey);
            this.loadTranslationsFromFile(transKey);
        } else {
            this.currentMode = MODE_PLAIN;
            this.modeSwitchBtn.setMessage(Component.translatable("gui.vn_edit.mode_plain"));
            String text;
            if (textJson == null || textJson.isJsonNull()) {
                text = "";
            } else if (textJson.isJsonPrimitive()) {
                text = textJson.getAsString();
            } else {
                Component comp = ComponentJson.fromJson(textJson);
                text = comp != null ? this.componentToFormattingCodes(comp) : "";
            }
            this.contentBox.setValueSilently(text);
        }
        this.textModified = false;
        this.updateVisibility();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        if (!this.visible) {
            return;
        }
        // 说话者分节
        EditorTheme.drawSectionHeader(g, this.font, this.x, this.speakerHeaderY, this.width, Component.translatable("gui.vn_edit.section.speaker"));
        g.drawString(this.font, Component.translatable("gui.vn_edit.speaker"), this.x + 5, this.speakerLabelY, EditorTheme.TEXT_SECONDARY);
        this.speakerBox.render(g, mx, my, pt);

        if (this.currentMode == MODE_PLAIN) {
            // 正文分节
            EditorTheme.drawSectionHeader(g, this.font, this.x, this.contentHeaderY, this.width, Component.translatable("gui.vn_edit.section.content"));
            g.drawString(this.font, Component.translatable("gui.vn_edit.text"), this.x + 5, this.contentLabelY, EditorTheme.TEXT_SECONDARY);
            this.contentBox.render(g, mx, my, pt);
            // 格式分节
            EditorTheme.drawSectionHeader(g, this.font, this.x, this.formatHeaderY, this.width, Component.translatable("gui.vn_edit.section.format"));
            this.colorButtons.forEach(b -> b.render(g, mx, my, pt));
            this.boldBtn.render(g, mx, my, pt);
            this.italicBtn.render(g, mx, my, pt);
            this.underlineBtn.render(g, mx, my, pt);
            this.strikethroughBtn.render(g, mx, my, pt);
            this.obfuscatedBtn.render(g, mx, my, pt);
            this.resetBtn.render(g, mx, my, pt);
            this.clearBtn.render(g, mx, my, pt);
            g.drawString(this.font, Component.translatable("gui.vn_edit.hex_color"), this.x + 5, this.hexLabelY, EditorTheme.TEXT_SECONDARY);
            this.hexColorBox.render(g, mx, my, pt);
            this.applyHexBtn.render(g, mx, my, pt);
        }

        if (this.currentMode == MODE_TRANSLATION) {
            // 翻译分节
            EditorTheme.drawSectionHeader(g, this.font, this.x, this.translationHeaderY, this.width, Component.translatable("gui.vn_edit.section.translation"));
            g.drawString(this.font, Component.translatable("gui.vn_edit.translation_key"), this.x + 5, this.translationKeyBox.getY() + 4, EditorTheme.TEXT_SECONDARY);
            g.drawString(this.font, Component.translatable("gui.vn_edit.translation_zh_cn"), this.x + 5, this.translationZhCnBox.getY() + 4, EditorTheme.TEXT_SECONDARY);
            g.drawString(this.font, Component.translatable("gui.vn_edit.translation_en_us"), this.x + 5, this.translationEnUsBox.getY() + 4, EditorTheme.TEXT_SECONDARY);
            this.translationKeyBox.render(g, mx, my, pt);
            this.translationZhCnBox.render(g, mx, my, pt);
            this.translationEnUsBox.render(g, mx, my, pt);
            this.generateLangBtn.render(g, mx, my, pt);
        }

        this.modeSwitchBtn.render(g, mx, my, pt);
    }

    @Override
    public List<? extends GuiEventListener> children() {
        List<GuiEventListener> list = new ArrayList<>();
        list.add(this.speakerBox);
        list.add(this.contentBox);
        list.add(this.modeSwitchBtn);
        list.add(this.translationKeyBox);
        list.add(this.translationZhCnBox);
        list.add(this.translationEnUsBox);
        list.add(this.generateLangBtn);
        list.addAll(this.colorButtons);
        list.add(this.boldBtn);
        list.add(this.italicBtn);
        list.add(this.underlineBtn);
        list.add(this.strikethroughBtn);
        list.add(this.obfuscatedBtn);
        list.add(this.resetBtn);
        list.add(this.clearBtn);
        list.add(this.hexColorBox);
        list.add(this.applyHexBtn);
        return list;
    }

    @Override
    public void setVisible(boolean v) {
        this.visible = v;
        this.speakerBox.setVisible(v);
        this.contentBox.visible = v;
        this.modeSwitchBtn.visible = v;
        this.updateVisibility();
    }

    @Override
    public int getContentHeight() {
        // 根据当前模式返回实际高度
        if (this.currentMode == MODE_TRANSLATION) {
            // 翻译模式：说话者 + 翻译分节（4行）
            return this.translationHeaderY - this.y + EditorTheme.SECTION_HDR_H + (EditorTheme.FIELD_HEIGHT + EditorTheme.ROW_GAP) * 4 + EditorTheme.PADDING;
        }
        // 纯文本模式：使用完整计算高度
        return this.computedHeight;
    }

    private EditorButton makeBtn(String text, Style style, char code, int x, int y) {
        return new EditorButton(x, y, EditorTheme.COLOR_BTN_SZ, EditorTheme.COLOR_BTN_SZ, Component.literal(text).withStyle(style), b -> this.contentBox.insertAtCursor("\u00a7" + code));
    }

    private void clearFormatting() {
        String cleaned = this.contentBox.getValue().replaceAll("\u00a7[0-9a-fk-or]", "");
        this.contentBox.setValue(cleaned);
        this.contentBox.setCursorPos(cleaned.length());
        this.saveTextToEntry();
    }

    private MutableComponent parseFormattingCodesToComponent(String text) {
        MutableComponent result = Component.literal("");
        Style cur = Style.EMPTY;
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\u00a7' && i + 1 < text.length()) {
                char code = text.charAt(i + 1);
                if (!buf.isEmpty()) {
                    result.append(Component.literal(buf.toString()).withStyle(cur));
                    buf.setLength(0);
                }
                ChatFormatting fmt = ChatFormatting.getByCode(code);
                if (fmt != null) {
                    if (fmt.isFormat()) {
                        cur = cur.applyFormat(fmt);
                    } else {
                        switch (fmt) {
                            case BOLD -> cur = cur.withBold(true);
                            case ITALIC -> cur = cur.withItalic(true);
                            case UNDERLINE -> cur = cur.withUnderlined(true);
                            case STRIKETHROUGH -> cur = cur.withStrikethrough(true);
                            case RESET -> cur = Style.EMPTY;
                            default -> cur = cur.applyFormat(fmt);
                        }
                    }
                }
                i++;
                continue;
            }
            buf.append(c);
        }
        if (!buf.isEmpty()) {
            result.append(Component.literal(buf.toString()).withStyle(cur));
        }
        return result;
    }

    private void loadTranslationsFromFile(String key) {
        if (key.isEmpty()) {
            return;
        }
        String zh = this.readLangFileValue("zh_cn.json", key);
        this.translationZhCnBox.setValue(zh != null ? zh : "");
        String en = this.readLangFileValue("en_us.json", key);
        this.translationEnUsBox.setValue(en != null ? en : "");
    }

    private String readLangFileValue(String fileName, String key) {
        Path file = EditorConfig.LANG_DIR.resolve(fileName);
        if (!Files.exists(file)) {
            return null;
        }
        try {
            String json = Files.readString(file);
            Map<String, String> map = GSON.fromJson(json, new TypeToken<Map<String, String>>(){}.getType());
            return map != null ? map.get(key) : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 将 Component 转换回带 § 格式代码的字符串。
     * 关键点：段落间样式若出现「属性被移除或颜色变化」，必须先 emit §r 重置，
     * 否则后续文本会错误继承前段格式（C4 往返丢失修复）。
     */
    private String componentToFormattingCodes(Component comp) {
        StringBuilder sb = new StringBuilder();
        final Style[] prev = {Style.EMPTY};
        comp.visit((style, textPart) -> {
            if (!textPart.isEmpty()) {
                boolean needsReset = needsReset(prev[0], style);
                if (needsReset) {
                    sb.append("\u00a7r");
                }
                // 样式有变化时 emit 当前完整样式（含 §r 后重建，或仅新增属性时冗余但无害）
                if (needsReset || !style.equals(prev[0])) {
                    this.appendStyle(style, sb);
                }
                sb.append(textPart);
                prev[0] = style;
            }
            return java.util.Optional.empty();
        }, Style.EMPTY);
        return sb.toString();
    }

    /**
     * 判断从 prev 切换到 cur 是否需要先 emit §r。
     * 当 prev 的某属性在 cur 中被移除或颜色发生变化时必须重置。
     */
    private boolean needsReset(Style prev, Style cur) {
        if (prev.isEmpty()) {
            return false;
        }
        if (prev.isBold() && !cur.isBold()) return true;
        if (prev.isItalic() && !cur.isItalic()) return true;
        if (prev.isUnderlined() && !cur.isUnderlined()) return true;
        if (prev.isStrikethrough() && !cur.isStrikethrough()) return true;
        if (prev.isObfuscated() && !cur.isObfuscated()) return true;
        TextColor pc = prev.getColor();
        TextColor cc = cur.getColor();
        if (pc != null && !pc.equals(cc)) return true;
        return false;
    }

    private void appendStyle(Style style, StringBuilder sb) {
        TextColor color = style.getColor();
        if (color != null) {
            int rgb = color.getValue();
            boolean found = false;
            for (ChatFormatting f : ChatFormatting.values()) {
                if (f.getColor() != null && f.getColor() == rgb) {
                    sb.append('\u00a7').append(f.getChar());
                    found = true;
                    break;
                }
            }
            if (!found) {
                sb.append("\u00a7x");
                String hex = String.format("%06X", rgb);
                for (int i = 0; i < hex.length(); i++) {
                    sb.append('\u00a7').append(hex.charAt(i));
                }
            }
        }
        if (style.isBold()) {
            sb.append("\u00a7l");
        }
        if (style.isItalic()) {
            sb.append("\u00a7o");
        }
        if (style.isUnderlined()) {
            sb.append("\u00a7n");
        }
        if (style.isStrikethrough()) {
            sb.append("\u00a7m");
        }
        if (style.isObfuscated()) {
            sb.append("\u00a7k");
        }
    }
}
