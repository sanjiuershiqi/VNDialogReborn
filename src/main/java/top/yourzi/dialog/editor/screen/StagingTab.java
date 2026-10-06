package top.yourzi.dialog.editor.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import top.yourzi.dialog.editor.AssetService;
import top.yourzi.dialog.editor.ui.Button;
import top.yourzi.dialog.editor.ui.Column;
import top.yourzi.dialog.editor.ui.Nodes;
import top.yourzi.dialog.editor.ui.Paragraph;
import top.yourzi.dialog.editor.ui.Row;
import top.yourzi.dialog.editor.ui.Select;
import top.yourzi.dialog.editor.ui.TextBox;
import top.yourzi.dialog.editor.ui.Theme;
import top.yourzi.dialog.editor.ui.UiNode;
import top.yourzi.dialog.model.BackgroundAnimationType;
import top.yourzi.dialog.model.BackgroundImageInfo;
import top.yourzi.dialog.model.BackgroundRenderOption;
import top.yourzi.dialog.model.DialogEntry;
import top.yourzi.dialog.model.PortraitInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Staging tab: background, portraits and audio for the selected node.
 *
 * <p>Assets are chosen through pickers rather than typed, because a mistyped path fails silently at
 * runtime. Portrait order is editable here since the runtime draws the list back to front; precise
 * placement happens on the stage view, which this tab links to.
 */
final class StagingTab extends Column {
    /** Screen-side actions this tab needs. */
    interface Actions {
        void pickBackgroundFile();

        void pickBuiltinBackground();

        void pickPortraitFile();

        void pickBuiltinPortrait();

        void pickAudio();

        void playAudio();

        void openStage(PortraitInfo portrait);
    }

    private final EditorContext context;
    private final TextBox backgroundPath = new TextBox("");
    private final Select<BackgroundRenderOption> renderOption;
    private final Select<BackgroundAnimationType> backgroundAnimation;
    private final BackgroundPreview preview = new BackgroundPreview();
    private final Column portraitRows = new Column().gap(2);
    private final Paragraph noPortraits = Paragraph.of(Theme.tr("staging.no_portraits")).color(Theme.TEXT_MUTED);
    private final TextBox audioPath = new TextBox("");
    private Actions actions;
    private DialogEntry entry;
    private boolean binding;

    StagingTab(EditorContext context) {
        this.context = context;
        this.gap(3).padding(2);
        this.renderOption = new Select<>(List.of(BackgroundRenderOption.values()), BackgroundRenderOption.FILL,
                option -> Theme.tr("staging.render." + option.name().toLowerCase(Locale.ROOT)),
                option -> this.updateBackground(info -> info.setRenderOption(option)));
        this.backgroundAnimation = new Select<>(List.of(BackgroundAnimationType.values()), BackgroundAnimationType.NONE,
                animation -> Theme.tr("staging.bg_anim." + animation.name().toLowerCase(Locale.ROOT)),
                animation -> this.updateBackground(info -> info.setAnimationType(animation)));

        this.add(Nodes.section(Theme.tr("section.background")));
        Row backgroundRow = new Row().gap(2);
        backgroundRow.add(this.backgroundPath.flex(1));
        backgroundRow.add(Button.of(Theme.tr("staging.choose"), () -> this.actions.pickBackgroundFile()).fit());
        backgroundRow.add(Button.of(Theme.tr("staging.builtin"), () -> this.actions.pickBuiltinBackground()).fit());
        this.add(backgroundRow);
        Row optionRow = new Row().gap(2);
        optionRow.add(this.renderOption);
        optionRow.add(this.backgroundAnimation);
        this.add(optionRow);
        this.add(this.preview.prefHeight(80));

        this.add(Nodes.section(Theme.tr("section.portraits")));
        Row portraitButtons = new Row().gap(2);
        portraitButtons.add(Button.of(Theme.tr("staging.add_portrait"), () -> this.actions.pickPortraitFile()).fit());
        portraitButtons.add(Button.of(Theme.tr("staging.builtin"), () -> this.actions.pickBuiltinPortrait()).fit());
        portraitButtons.add(Nodes.fill());
        portraitButtons.add(Button.of(Theme.tr("staging.open_stage"), () -> this.actions.openStage(null)).fit());
        this.add(portraitButtons);
        this.add(this.portraitRows);
        this.add(this.noPortraits);

        this.add(Nodes.section(Theme.tr("section.audio")));
        Row audioRow = new Row().gap(2);
        audioRow.add(this.audioPath.flex(1));
        audioRow.add(Button.of(Theme.tr("staging.choose"), () -> this.actions.pickAudio()).fit());
        audioRow.add(Button.of(Theme.tr("staging.play"), () -> this.actions.playAudio()).fit());
        this.add(audioRow);

        this.backgroundPath.placeholder(Theme.tr("staging.background_hint"));
        this.backgroundPath.onChange(this::setBackgroundPath);
        this.audioPath.placeholder(Theme.tr("staging.audio_hint"));
        this.audioPath.onChange(value -> {
            if (this.entry != null && !this.binding) {
                this.entry.setAudioPath(value.isBlank() ? null : value.trim());
                this.context.touch(true);
            }
        });
    }

    void setActions(Actions actions) {
        this.actions = actions;
    }

    void bind(DialogEntry entry) {
        this.binding = true;
        try {
            this.entry = entry;
            this.portraitRows.clear();
            if (entry == null) {
                return;
            }
            BackgroundImageInfo background = entry.getBackgroundImage();
            this.backgroundPath.setValue(background == null || background.getPath() == null ? "" : background.getPath());
            this.renderOption.setSelected(background != null && background.getRenderOption() != null
                    ? background.getRenderOption() : BackgroundRenderOption.FILL);
            this.backgroundAnimation.setSelected(background != null && background.getAnimationType() != null
                    ? background.getAnimationType() : BackgroundAnimationType.NONE);
            this.preview.setPath(this.backgroundPath.value());
            this.audioPath.setValue(entry.getAudioPath() == null ? "" : entry.getAudioPath());
            List<PortraitInfo> portraits = entry.getPortraits() == null ? List.of() : entry.getPortraits();
            for (int i = 0; i < portraits.size(); i++) {
                if (portraits.get(i) != null) {
                    this.portraitRows.add(new PortraitRow(portraits.get(i), i, portraits.size()));
                }
            }
            this.noPortraits.setVisible(portraits.isEmpty());
        } finally {
            this.binding = false;
        }
    }

    /** Applied by the text field and by the asset pickers. */
    void setBackgroundPath(String value) {
        if (this.entry == null || this.binding) {
            return;
        }
        String path = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!path.equals(this.backgroundPath.value())) {
            this.backgroundPath.setValue(path);
        }
        if (path.isEmpty()) {
            this.entry.setBackgroundImage(null);
        } else if (this.entry.getBackgroundImage() == null) {
            this.entry.setBackgroundImage(new BackgroundImageInfo(path, this.renderOption.selected(),
                    this.backgroundAnimation.selected()));
        } else {
            this.entry.getBackgroundImage().setPath(path);
        }
        this.preview.setPath(path);
        this.context.touch(true);
    }

    void setAudioPath(String value) {
        if (this.entry == null) {
            return;
        }
        this.audioPath.setValue(value == null ? "" : value);
        this.entry.setAudioPath(value == null || value.isBlank() ? null : value);
        this.context.touch(false);
    }

    private void updateBackground(Consumer<BackgroundImageInfo> change) {
        if (this.entry == null || this.binding || this.entry.getBackgroundImage() == null) {
            return;
        }
        change.accept(this.entry.getBackgroundImage());
        this.context.touch(false);
    }

    private List<PortraitInfo> portraits() {
        return this.entry.getPortraits() == null ? new ArrayList<>() : new ArrayList<>(this.entry.getPortraits());
    }

    private void movePortrait(int index, int delta) {
        List<PortraitInfo> list = this.portraits();
        int target = index + delta;
        if (index < 0 || target < 0 || target >= list.size()) {
            return;
        }
        list.add(target, list.remove(index));
        this.entry.setPortraits(list);
        this.context.touchStructure();
    }

    private void removePortrait(int index) {
        List<PortraitInfo> list = this.portraits();
        if (index < 0 || index >= list.size()) {
            return;
        }
        list.remove(index);
        this.entry.setPortraits(list.isEmpty() ? null : list);
        this.context.touchStructure();
    }

    /** One portrait: path (opens the stage), position, reorder and delete. */
    private final class PortraitRow extends Row {
        PortraitRow(PortraitInfo portrait, int index, int count) {
            this.gap(2);
            String path = portrait.getPath() == null ? "—" : portrait.getPath();
            Button name = Button.of(Component.literal(path), () -> StagingTab.this.actions.openStage(portrait));
            name.flex(1);
            name.withTooltip(Theme.tr("staging.portrait_tip"));
            Button position = Button.of(Theme.tr("position." + portrait.getPosition().name().toLowerCase(Locale.ROOT)),
                    () -> {
                        top.yourzi.dialog.model.PortraitPosition[] values = top.yourzi.dialog.model.PortraitPosition.values();
                        portrait.setPosition(values[(portrait.getPosition().ordinal() + 1) % values.length]);
                        StagingTab.this.context.touchStructure();
                    });
            position.prefWidth(34);
            position.withTooltip(Theme.tr("staging.position_tip"));
            Button up = Button.of(Component.literal("▲"), () -> StagingTab.this.movePortrait(index, -1)).active(index > 0);
            Button down = Button.of(Component.literal("▼"), () -> StagingTab.this.movePortrait(index, 1))
                    .active(index < count - 1);
            Button remove = Button.of(Component.literal("✕"), () -> StagingTab.this.removePortrait(index))
                    .tone(Button.Tone.GHOST);
            up.prefWidth(15);
            down.prefWidth(15);
            remove.prefWidth(15);
            this.add(name);
            this.add(position);
            this.add(up);
            this.add(down);
            this.add(remove);
        }
    }

    /** Background thumbnail; the handle is resolved once per path, not every frame. */
    private static final class BackgroundPreview extends UiNode {
        private String path = "";
        private AssetService.Handle handle = AssetService.MISSING;

        void setPath(String path) {
            String clean = path == null ? "" : path;
            if (!clean.equals(this.path)) {
                this.path = clean;
                this.handle = null;
            }
        }

        @Override
        protected void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            if (this.handle == null) {
                this.handle = this.path.isBlank() ? AssetService.MISSING : AssetService.background(this.path);
            }
            if (this.handle.present()) {
                graphics.fill(this.x(), this.y(), this.right(), this.bottom(), Theme.FIELD);
                AssetService.blitFitted(graphics, this.handle, this.x() + 1, this.y() + 1, this.width() - 2,
                        this.height() - 2);
                Theme.border(graphics, this.x(), this.y(), this.width(), this.height(), Theme.BORDER);
            } else {
                AssetService.placeholder(graphics, this.path.isBlank() ? null : this.path, this.x(), this.y(),
                        this.width(), this.height(), Theme.FIELD, this.path.isBlank() ? Theme.BORDER : Theme.WARNING);
            }
        }
    }
}
