package top.yourzi.dialog.editor.screen;

import net.minecraft.ChatFormatting;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import top.yourzi.dialog.editor.TextCodec;
import top.yourzi.dialog.editor.ui.Button;
import top.yourzi.dialog.editor.ui.Column;
import top.yourzi.dialog.editor.ui.FormatCodes;
import top.yourzi.dialog.editor.ui.Modal;
import top.yourzi.dialog.editor.ui.Nodes;
import top.yourzi.dialog.editor.ui.Paragraph;
import top.yourzi.dialog.editor.ui.TextArea;
import top.yourzi.dialog.editor.ui.TextBox;
import top.yourzi.dialog.editor.ui.Theme;
import top.yourzi.dialog.editor.ui.Toggle;
import top.yourzi.dialog.editor.ui.UiHost;
import top.yourzi.dialog.model.DisplayItemInfo;

/**
 * Customises a displayed item without writing NBT by hand: its name, its description lines and
 * whether it glows. The result is stored as {@code {components:{...}}} in the item's NBT field, and
 * anything else already in there (enchantments carried over from a backpack item, say) is kept.
 */
final class ItemEditSheet extends Modal {
    private static final String NAME = "minecraft:custom_name";
    private static final String LORE = "minecraft:lore";
    private static final String GLINT = "minecraft:enchantment_glint_override";
    /** Lore is drawn grey and upright, the way item descriptions usually look. */
    private static final String LORE_PREFIX = "\u00a77";

    private final DisplayItemInfo item;
    private final Runnable onApplied;
    private final CompoundTag root;
    private final TextBox name;
    private final TextArea lore;
    private final Toggle glint;

    private ItemEditSheet(DisplayItemInfo item, Runnable onApplied) {
        super(Theme.tr("item_edit.title"));
        this.setCardWidth(340);
        this.item = item;
        this.onApplied = onApplied;
        this.root = parse(item.getNbt());
        CompoundTag components = this.root.getCompound("components");
        this.name = new TextBox(readText(components.getString(NAME)));
        StringBuilder lines = new StringBuilder();
        ListTag list = components.getList(LORE, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            String line = readText(list.getString(i));
            if (i > 0) {
                lines.append('\n');
            }
            lines.append(line.startsWith(LORE_PREFIX) ? line.substring(LORE_PREFIX.length()) : line);
        }
        this.lore = new TextArea(lines.toString());
        this.glint = new Toggle(Theme.tr("item_edit.glint"), components.getBoolean(GLINT), value -> {
        });
    }

    static void open(UiHost host, DisplayItemInfo item, Runnable onApplied) {
        ItemEditSheet sheet = new ItemEditSheet(item, onApplied);
        sheet.build();
        host.open(sheet, true, false);
        host.focus(sheet.name);
    }

    private static CompoundTag parse(String nbt) {
        if (nbt == null || nbt.isBlank()) {
            return new CompoundTag();
        }
        try {
            return TagParser.parseTag(nbt);
        } catch (Exception e) {
            return new CompoundTag();
        }
    }

    /** A stored text component as editable text with formatting codes. */
    private static String readText(String json) {
        if (json == null || json.isEmpty()) {
            return "";
        }
        try {
            Component component = Component.Serializer.fromJson(json, RegistryAccess.EMPTY);
            return component == null ? "" : TextCodec.toFormattedCodes(component);
        } catch (Exception e) {
            return json;
        }
    }

    /** Editable text as a stored component; {@code &a} works as well as the section sign. */
    private static String writeText(String text, Style base) {
        String coded = text.replaceAll("&([0-9a-fk-orA-FK-OR])", "\u00a7$1");
        Component component = Component.empty().withStyle(base).append(FormatCodes.parse(coded));
        return Component.Serializer.toJson(component, RegistryAccess.EMPTY);
    }

    @Override
    protected void buildBody(Column body) {
        this.name.placeholder(Theme.tr("item_edit.name_hint"));
        this.lore.placeholder(Theme.tr("item_edit.lore_hint"));
        body.add(Nodes.caption(Theme.tr("item_edit.name")));
        body.add(this.name);
        body.add(Nodes.caption(Theme.tr("item_edit.lore")));
        body.add(this.lore.prefHeight(64));
        body.add(this.glint);
        body.add(Paragraph.of(Theme.tr("item_edit.help")).color(Theme.TEXT_MUTED));
    }

    @Override
    public void build() {
        this.footerButton(Theme.tr("apply"), Button.Tone.PRIMARY, this::apply);
        super.build();
    }

    private void apply() {
        CompoundTag components = this.root.getCompound("components");
        Style upright = Style.EMPTY.withItalic(false);
        if (this.name.value().isBlank()) {
            components.remove(NAME);
        } else {
            components.putString(NAME, writeText(this.name.value(), upright));
        }
        if (this.lore.value().isBlank()) {
            components.remove(LORE);
        } else {
            ListTag list = new ListTag();
            for (String line : this.lore.value().split("\n", -1)) {
                list.add(StringTag.valueOf(writeText(line, upright.withColor(ChatFormatting.GRAY))));
            }
            components.put(LORE, list);
        }
        if (this.glint.value()) {
            components.putBoolean(GLINT, true);
        } else {
            components.remove(GLINT);
        }
        if (components.isEmpty()) {
            this.root.remove("components");
        } else {
            this.root.put("components", components);
        }
        this.item.setNbt(this.root.isEmpty() ? null : this.root.toString());
        this.dismissLayer();
        this.onApplied.run();
    }
}
