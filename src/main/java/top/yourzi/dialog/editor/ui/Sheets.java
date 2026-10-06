package top.yourzi.dialog.editor.ui;

import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;

/**
 * Ready-made sheets: confirmation, single-field prompt, option chooser and read-only information.
 * Each closes itself before running the primary action so the action may open the next sheet.
 */
public final class Sheets {
    private Sheets() {
    }

    public static void confirm(UiHost host, Component title, Component message, Component confirmLabel,
                               boolean danger, Runnable onConfirm) {
        open(host, new ConfirmSheet(title, message, confirmLabel, danger, onConfirm));
    }

    public static void prompt(UiHost host, Component title, Component hint, String initial, Component confirmLabel,
                              boolean danger, Consumer<String> onConfirm) {
        PromptSheet sheet = new PromptSheet(title, hint, initial, confirmLabel, danger, onConfirm);
        open(host, sheet);
        host.focus(sheet.field());
        sheet.field().model().selectAll();
    }

    public static void choose(UiHost host, Component title, Component message, List<Component> options,
                              Consumer<Integer> onChoose) {
        open(host, new ChooseSheet(title, message, options, onChoose));
    }

    private static void open(UiHost host, Modal sheet) {
        sheet.build();
        host.open(sheet, true, false);
    }

    private static final class ConfirmSheet extends Modal {
        private final Component message;
        private final Component confirmLabel;
        private final boolean danger;
        private final Runnable onConfirm;

        ConfirmSheet(Component title, Component message, Component confirmLabel, boolean danger, Runnable onConfirm) {
            super(title);
            this.message = message;
            this.confirmLabel = confirmLabel;
            this.danger = danger;
            this.onConfirm = onConfirm;
        }

        @Override
        protected void buildBody(Column body) {
            body.add(Paragraph.of(this.message));
        }

        @Override
        public void build() {
            this.footerButton(this.confirmLabel, this.danger ? Button.Tone.DANGER : Button.Tone.PRIMARY, () -> {
                this.dismissLayer();
                this.onConfirm.run();
            });
            super.build();
        }
    }

    private static final class PromptSheet extends Modal {
        private final Component hint;
        private final Component confirmLabel;
        private final boolean danger;
        private final Consumer<String> onConfirm;
        private final TextBox field;

        PromptSheet(Component title, Component hint, String initial, Component confirmLabel, boolean danger,
                    Consumer<String> onConfirm) {
            super(title);
            this.hint = hint;
            this.confirmLabel = confirmLabel;
            this.danger = danger;
            this.onConfirm = onConfirm;
            this.field = TextBox.of(initial).placeholder(hint).onSubmit(this::accept);
        }

        TextBox field() {
            return this.field;
        }

        @Override
        protected void buildBody(Column body) {
            body.add(Paragraph.of(this.hint).color(Theme.TEXT_MUTED));
            body.add(this.field);
        }

        @Override
        public void build() {
            this.footerButton(this.confirmLabel, this.danger ? Button.Tone.DANGER : Button.Tone.PRIMARY, this::accept);
            super.build();
        }

        private void accept() {
            String value = this.field.value();
            this.dismissLayer();
            this.onConfirm.accept(value);
        }
    }

    private static final class ChooseSheet extends Modal {
        private final Component message;
        private final List<Component> options;
        private final Consumer<Integer> onChoose;

        ChooseSheet(Component title, Component message, List<Component> options, Consumer<Integer> onChoose) {
            super(title);
            this.message = message;
            this.options = options;
            this.onChoose = onChoose;
        }

        @Override
        protected void buildBody(Column body) {
            if (this.message != null) {
                body.add(Paragraph.of(this.message));
            }
            for (int i = 0; i < this.options.size(); i++) {
                int index = i;
                Button button = Button.of(this.options.get(i), () -> {
                    this.dismissLayer();
                    this.onChoose.accept(index);
                });
                button.prefHeight(20);
                body.add(button);
            }
        }
    }
}
