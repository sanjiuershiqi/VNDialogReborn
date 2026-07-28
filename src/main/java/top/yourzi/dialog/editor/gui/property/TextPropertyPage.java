package top.yourzi.dialog.editor.gui.property;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.reflect.TypeToken;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import top.yourzi.dialog.Dialog;
import top.yourzi.dialog.editor.gui.VNDialogEditorScreen;
import top.yourzi.dialog.editor.gui.widget.FocusAwareButton;
import top.yourzi.dialog.editor.gui.widget.MultiLineEditBox;
import top.yourzi.dialog.editor.util.EditorConfig;
import top.yourzi.dialog.editor.util.LangFileGenerator;
import top.yourzi.dialog.model.DialogEntry;
import top.yourzi.dialog.util.ComponentJson;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 文本属性页：说话者、正文、格式化代码、翻译模式。融合自 visual_mod_edit_vndialog。
 */
public class TextPropertyPage implements PropertyPage {
    private static final int LABEL_WIDTH = 60;
    private static final int FIELD_SPACING = 25;
    private static final int MODE_PLAIN = 0;
    private static final int MODE_TRANSLATION = 1;
    private static final ChatFormatting[] COLORS = new ChatFormatting[]{
            ChatFormatting.BLACK, ChatFormatting.DARK_BLUE, ChatFormatting.DARK_GREEN, ChatFormatting.DARK_AQUA,
            ChatFormatting.DARK_RED, ChatFormatting.DARK_PURPLE, ChatFormatting.GOLD, ChatFormatting.GRAY,
            ChatFormatting.DARK_GRAY, ChatFormatting.BLUE, ChatFormatting.GREEN, ChatFormatting.AQUA,
            ChatFormatting.RED, ChatFormatting.LIGHT_PURPLE, ChatFormatting.YELLOW, ChatFormatting.WHITE
    };
    private static final Gson GSON = new Gson();

    private final Font font;
    private EditBox speakerBox;
    private MultiLineEditBox contentBox;
    private boolean visible = true;
    private int x;
    private int y;
    private int width;
    private int height;
    private DialogEntry currentEntry;
    private Button boldBtn;
    private Button italicBtn;
    private Button underlineBtn;
    private Button strikethroughBtn;
    private Button resetBtn;
    private Button clearBtn;
    private final List<Button> colorButtons = new ArrayList<>();
    private int currentMode = MODE_PLAIN;
    public Button modeSwitchBtn;
    private Button generateLangBtn;
    private EditBox translationKeyBox;
    private EditBox translationZhCnBox;
    private EditBox translationEnUsBox;

    public TextPropertyPage(Font font) {
        this.font = font;
    }

    @Override
    public void init(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        int fieldX = x + LABEL_WIDTH + 5;
        int fieldWidth = width - LABEL_WIDTH - 10;
        this.speakerBox = new EditBox(this.font, fieldX, y + 5, fieldWidth, 16, Component.translatable("gui.vn_edit.speaker"));
        this.speakerBox.setMaxLength(999999999);
        this.speakerBox.setEditable(true);
        this.speakerBox.setResponder(s -> {
            if (this.currentEntry != null) {
                this.currentEntry.setSpeaker(new JsonPrimitive(s));
            }
        });
        this.contentBox = new MultiLineEditBox(this.font, fieldX, y + 25, fieldWidth, 80);
        this.contentBox.setResponder(s -> this.saveTextToEntry());
        this.modeSwitchBtn = new FocusAwareButton(fieldX, y + 25 + 85, 60, 16, Component.translatable("gui.vn_edit.mode_plain"), b -> this.toggleMode());
        int transY = y + 25 + 85 + 18;
        this.translationKeyBox = new EditBox(this.font, fieldX, transY, fieldWidth, 16, Component.translatable("gui.vn_edit.translation_key"));
        this.translationKeyBox.setMaxLength(999999999);
        this.translationKeyBox.setResponder(s -> this.saveTranslationToEntry());
        int transY2 = transY + 20;
        this.translationZhCnBox = new EditBox(this.font, fieldX, transY2, fieldWidth, 16, Component.translatable("gui.vn_edit.translation_zh_cn"));
        this.translationZhCnBox.setMaxLength(999999999);
        this.translationZhCnBox.setResponder(s -> this.saveTranslationToEntry());
        int transY3 = transY2 + 20;
        this.translationEnUsBox = new EditBox(this.font, fieldX, transY3, fieldWidth, 16, Component.translatable("gui.vn_edit.translation_en_us"));
        this.translationEnUsBox.setMaxLength(999999999);
        this.translationEnUsBox.setResponder(s -> this.saveTranslationToEntry());
        this.generateLangBtn = new FocusAwareButton(fieldX, transY3 + 22, 80, 16, Component.translatable("gui.vn_edit.generate_lang"), b -> this.generateLangFiles());
        int barY = transY3 + 42;
        int btnSize = 16;
        int gap = 2;
        int bx = fieldX;
        int colorsPerRow = 8;
        int colorCount = 0;
        for (ChatFormatting color : COLORS) {
            int rgb = color.getColor() != null ? color.getColor() : 0xFFFFFF;
            FocusAwareButton btn = new FocusAwareButton(bx, barY, btnSize, btnSize,
                    Component.literal("\u25a0").withStyle(Style.EMPTY.withColor(rgb)),
                    b -> this.contentBox.insertAtCursor("\u00a7" + color.getChar()));
            this.colorButtons.add(btn);
            bx += btnSize + gap;
            if (++colorCount % colorsPerRow == 0) {
                bx = fieldX;
                barY += btnSize + gap;
            }
        }
        if (colorCount % colorsPerRow != 0) {
            barY += btnSize + gap;
        }
        bx = fieldX;
        this.boldBtn = this.makeBtn("B", Style.EMPTY.withBold(true), 'l', bx, barY);
        this.italicBtn = this.makeBtn("I", Style.EMPTY.withItalic(true), 'o', bx += btnSize + gap, barY);
        this.underlineBtn = this.makeBtn("U", Style.EMPTY.withUnderlined(true), 'n', bx += btnSize + gap, barY);
        this.strikethroughBtn = this.makeBtn("S", Style.EMPTY.withStrikethrough(true), 'm', bx += btnSize + gap, barY);
        this.resetBtn = this.makeBtn("R", Style.EMPTY.withColor(0xAAAAAA), 'r', bx += btnSize + gap, barY);
        bx = fieldX;
        this.clearBtn = new FocusAwareButton(bx, barY += btnSize + 4, 80, 16, Component.translatable("gui.vn_edit.clear_format"), b -> this.clearFormatting());
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
        this.resetBtn.visible = isPlain;
        this.clearBtn.visible = isPlain;
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
            JsonObject json = new JsonObject();
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
        this.refreshDisplay();
    }

    @Override
    public void unbind() {
        this.saveToEntryBasedOnMode();
        this.currentEntry = null;
        this.speakerBox.setValue("");
        this.contentBox.setValueSilently("");
        this.translationKeyBox.setValue("");
        this.translationZhCnBox.setValue("");
        this.translationEnUsBox.setValue("");
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
        this.speakerBox.setResponder(null);
        String speakerStr = "";
        JsonElement speakerJson = this.currentEntry.getSpeaker();
        if (speakerJson != null && !speakerJson.isJsonNull()) {
            if (speakerJson.isJsonPrimitive()) {
                speakerStr = speakerJson.getAsString();
            } else {
                Component comp = ComponentJson.fromJson(speakerJson);
                speakerStr = comp != null ? comp.getString() : speakerJson.toString();
            }
        }
        this.speakerBox.setValue(speakerStr);
        this.speakerBox.setResponder(s -> {
            if (this.currentEntry != null) {
                this.currentEntry.setSpeaker(new JsonPrimitive(s));
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
        this.updateVisibility();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        if (!this.visible) {
            return;
        }
        g.drawString(this.font, Component.translatable("gui.vn_edit.speaker"), this.x + 5, this.y + 9, 0xCCCCCC);
        if (this.currentMode == MODE_PLAIN) {
            g.drawString(this.font, Component.translatable("gui.vn_edit.text"), this.x + 5, this.y + 25 + 5, 0xCCCCCC);
        }
        if (this.currentMode == MODE_TRANSLATION) {
            g.drawString(this.font, Component.translatable("gui.vn_edit.translation_key"), this.x + 5, this.translationKeyBox.getY() + 1, 0xCCCCCC);
            g.drawString(this.font, Component.translatable("gui.vn_edit.translation_zh_cn"), this.x + 5, this.translationZhCnBox.getY() + 1, 0xCCCCCC);
            g.drawString(this.font, Component.translatable("gui.vn_edit.translation_en_us"), this.x + 5, this.translationEnUsBox.getY() + 1, 0xCCCCCC);
        }
        this.speakerBox.render(g, mx, my, pt);
        this.contentBox.render(g, mx, my, pt);
        this.modeSwitchBtn.render(g, mx, my, pt);
        this.translationKeyBox.render(g, mx, my, pt);
        this.translationZhCnBox.render(g, mx, my, pt);
        this.translationEnUsBox.render(g, mx, my, pt);
        this.generateLangBtn.render(g, mx, my, pt);
        this.colorButtons.forEach(b -> b.render(g, mx, my, pt));
        this.boldBtn.render(g, mx, my, pt);
        this.italicBtn.render(g, mx, my, pt);
        this.underlineBtn.render(g, mx, my, pt);
        this.strikethroughBtn.render(g, mx, my, pt);
        this.resetBtn.render(g, mx, my, pt);
        this.clearBtn.render(g, mx, my, pt);
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
        list.add(this.resetBtn);
        list.add(this.clearBtn);
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
        return 320;
    }

    private Button makeBtn(String text, Style style, char code, int x, int y) {
        return new FocusAwareButton(x, y, 16, 16, Component.literal(text).withStyle(style), b -> this.contentBox.insertAtCursor("\u00a7" + code));
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

    private String componentToFormattingCodes(Component comp) {
        StringBuilder sb = new StringBuilder();
        if (!comp.getString().isEmpty()) {
            this.appendStyledText(comp, sb);
        }
        for (Component sibling : comp.getSiblings()) {
            this.appendStyledText(sibling, sb);
        }
        return sb.toString();
    }

    private void appendStyledText(Component comp, StringBuilder sb) {
        Style style = comp.getStyle();
        if (!style.isEmpty()) {
            this.appendStyle(style, sb);
        }
        sb.append(comp.getString());
    }

    private void appendStyle(Style style, StringBuilder sb) {
        TextColor color = style.getColor();
        if (color != null) {
            int rgb = color.getValue();
            for (ChatFormatting f : ChatFormatting.values()) {
                if (f.getColor() != null && f.getColor() == rgb) {
                    sb.append('\u00a7').append(f.getChar());
                    break;
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
    }
}
