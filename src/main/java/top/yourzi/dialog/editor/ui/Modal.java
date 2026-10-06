package top.yourzi.dialog.editor.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Modal sheet: dims the workspace, centers a card and pins its title, body and footer.
 *
 * <p>The card is re-laid out from the body's measured height, so a prompt with one field and a file
 * picker with three hundred rows use the same skeleton.
 */
public abstract class Modal extends UiNode {
    private static final int HEADER_H = 22;
    private static final int PAD = 10;

    private final Component title;
    private final List<UiNode> footerNodes = new ArrayList<>();
    private Column body;
    private Row footer;
    private int cardWidth = 320;
    private int cardX;
    private int cardY;
    private int cardHeight;
    private ScrollView scroller;

    protected Modal(Component title) {
        this.title = title;
    }

    protected Column body() {
        return this.body;
    }

    /** Adds a footer button; footer buttons are laid out left to right in call order. */
    protected Button footerButton(Component label, Button.Tone tone, Runnable action) {
        Button button = Button.of(label, action).tone(tone);
        button.prefWidth(84);
        this.footerNodes.add(button);
        return button;
    }

    protected final void setCardWidth(int width) {
        this.cardWidth = width;
    }

    /** Builds the body and footer once, right after the subclass constructor ran. */
    public void build() {
        this.body = new Column().gap(6);
        this.buildBody(this.body);
        this.scroller = new ScrollView(this.body);
        this.add(this.scroller);
        this.footer = new Row().gap(6);
        this.footer.add(Nodes.fill());
        Button cancel = Button.of(Theme.tr(this.footerNodes.isEmpty() ? "close" : "cancel"), this::dismissLayer);
        cancel.prefWidth(70);
        this.footer.add(cancel);
        for (UiNode node : this.footerNodes) {
            this.footer.add(node);
        }
        this.add(this.footer);
    }

    /** Modals are always sized by the host, never by a parent container. */
    @Override
    public final int measureHeight(int width) {
        return 0;
    }

    protected abstract void buildBody(Column body);

    @Override
    protected void onLayout() {
        int width = Math.min(this.cardWidth, Math.max(200, this.width() - 40));
        int innerWidth = width - PAD * 2;
        int maxBodyHeight = Math.max(40, this.height() - 150);
        int bodyHeight = Math.min(this.body.measureHeight(innerWidth - Theme.SCROLLBAR - 2), maxBodyHeight);
        int footerHeight = Math.max(Theme.ROW + 4, this.footer.measureHeight(innerWidth));
        int cardHeight = HEADER_H + PAD + bodyHeight + PAD + footerHeight + PAD;
        this.cardX = this.x() + (this.width() - width) / 2;
        this.cardY = this.y() + Math.max(15, (this.height() - cardHeight) / 2);
        this.cardHeight = cardHeight;
        int bodyY = this.cardY + HEADER_H + PAD;
        int bodyX = this.cardX + PAD;
        this.scroller.setBounds(bodyX, bodyY, innerWidth, bodyHeight);
        this.footer.setBounds(bodyX, bodyY + bodyHeight + PAD, innerWidth, footerHeight);
    }

    @Override
    protected void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(this.x(), this.y(), this.right(), this.bottom(), Theme.SCRIM);
        graphics.fill(this.cardX, this.cardY, this.cardX + this.cardWidth(), this.cardY + this.cardHeight, Theme.SURFACE);
        Theme.border(graphics, this.cardX, this.cardY, this.cardWidth(), this.cardHeight, Theme.BORDER_STRONG);
        graphics.fill(this.cardX, this.cardY, this.cardX + 4, this.cardY + HEADER_H, Theme.ACCENT);
        Theme.textIn(graphics, this.title.getString(), this.cardX + 12, this.cardY, this.cardWidth() - 20, HEADER_H,
                Theme.TEXT);
        graphics.fill(this.cardX, this.cardY + HEADER_H, this.cardX + this.cardWidth(), this.cardY + HEADER_H + 1,
                Theme.BORDER);
    }

    private int cardWidth() {
        return Math.min(this.cardWidth, Math.max(200, this.width() - 40));
    }
}
