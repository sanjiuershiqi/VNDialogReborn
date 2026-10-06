package top.yourzi.dialog.editor.screen;

import net.minecraft.client.gui.GuiGraphics;
import top.yourzi.dialog.editor.ui.Button;
import top.yourzi.dialog.editor.ui.Row;
import top.yourzi.dialog.editor.ui.ScrollView;
import top.yourzi.dialog.editor.ui.Theme;
import top.yourzi.dialog.editor.ui.UiNode;
import top.yourzi.dialog.model.DialogEntry;
import top.yourzi.dialog.model.DialogSequence;
import top.yourzi.dialog.model.DisplayItemInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Property inspector: everything about the selected node, in four tabs that follow how a writer
 * thinks - what it says, where it goes, how it looks, what else it does.
 *
 * <p>Every field writes through to the model as it changes, so switching tabs or nodes never loses
 * an edit. Tabs are rebuilt only on selection or structural changes; plain typing never rebinds,
 * which keeps the caret where the writer left it.
 */
public final class InspectorPanel extends EditorPanel {
    private static final String[] TAB_KEYS = {"tab.content", "tab.branch", "tab.staging", "tab.logic"};

    private final ContentTab content;
    private final BranchTab branch;
    private final StagingTab staging;
    private final LogicTab logic;
    private final Row tabBar = new Row().gap(1);
    private final List<Button> tabButtons = new ArrayList<>();
    private final List<ScrollView> pages = new ArrayList<>();
    private int activeTab;
    private DialogEntry bound;

    public InspectorPanel(EditorContext context) {
        super(null);
        this.content = new ContentTab(context);
        this.branch = new BranchTab(context);
        this.staging = new StagingTab(context);
        this.logic = new LogicTab(context);
        UiNode[] tabs = {this.content, this.branch, this.staging, this.logic};
        for (int i = 0; i < tabs.length; i++) {
            int index = i;
            Button tab = Button.of(Theme.tr(TAB_KEYS[i]), () -> this.setActiveTab(index)).tone(Button.Tone.TAB);
            tab.flex(1);
            this.tabButtons.add(tab);
            this.tabBar.add(tab);
            ScrollView page = new ScrollView(tabs[i]);
            this.pages.add(page);
            this.add(page);
        }
        this.add(this.tabBar);
        this.setActiveTab(0);
    }

    public void setActiveTab(int index) {
        if (index < 0 || index >= this.pages.size()) {
            return;
        }
        this.activeTab = index;
        for (int i = 0; i < this.pages.size(); i++) {
            this.pages.get(i).setVisible(i == index && this.bound != null);
            this.tabButtons.get(i).selected(i == index);
        }
    }

    /** Wires everything that needs screen-level overlays. */
    public void setActions(Consumer<Consumer<String>> nodePicker, StagingTab.Actions stagingActions,
                           Runnable inventoryPicker) {
        this.branch.setTargetPicker(nodePicker);
        this.staging.setActions(stagingActions);
        this.logic.setInventoryPicker(inventoryPicker);
    }

    @Override
    protected void onLayout() {
        super.onLayout();
        int x = this.content().x();
        int width = this.content().width();
        int y = this.content().y();
        this.tabBar.setBounds(x + 4, y, width - 8, Theme.ROW + 2);
        int bodyY = y + Theme.ROW + 10;
        for (ScrollView page : this.pages) {
            page.setBounds(x + 8, bodyY, width - 10, Math.max(0, this.content().bottom() - bodyY - 4));
        }
    }

    /**
     * Rebinds the tabs.
     *
     * @param force rebuild even when the entry is unchanged, after structural edits
     */
    public void bind(DialogEntry entry, DialogSequence sequence, boolean force) {
        if (!force && entry == this.bound) {
            return;
        }
        boolean switched = entry != this.bound;
        this.bound = entry;
        this.content.bind(entry);
        this.branch.bind(entry, sequence);
        this.staging.bind(entry);
        this.logic.bind(entry);
        this.tabBar.setVisible(entry != null);
        if (switched) {
            for (ScrollView page : this.pages) {
                page.setOffset(0);
            }
        }
        this.setActiveTab(this.activeTab);
    }

    public void setBackgroundPath(String path) {
        this.staging.setBackgroundPath(path);
    }

    public void setAudioPath(String path) {
        this.staging.setAudioPath(path);
    }

    public void addItem(DisplayItemInfo info) {
        this.logic.addItem(info);
    }

    @Override
    protected void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (this.bound != null) {
            graphics.fill(this.tabBar.x(), this.tabBar.bottom(), this.tabBar.right(), this.tabBar.bottom() + 1, Theme.BORDER);
        }
        if (this.bound == null) {
            int centerY = this.content().y() + this.content().height() / 2;
            Theme.centered(graphics, Theme.tr("inspector.hint").getString(), this.x() + this.width() / 2, centerY - 4,
                    Theme.TEXT_MUTED);
        }
    }
}
