package top.yourzi.dialog.editor.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import top.yourzi.dialog.Dialog;
import top.yourzi.dialog.DialogManager;
import top.yourzi.dialog.editor.AssetService;
import top.yourzi.dialog.editor.AudioPreviewPlayer;
import top.yourzi.dialog.editor.DialogValidator;
import top.yourzi.dialog.editor.EditorConfig;
import top.yourzi.dialog.editor.EditorDocument;
import top.yourzi.dialog.editor.EditorStore;
import top.yourzi.dialog.editor.ui.Button;
import top.yourzi.dialog.editor.ui.ContextMenu;
import top.yourzi.dialog.editor.ui.Nodes;
import top.yourzi.dialog.editor.ui.Row;
import top.yourzi.dialog.editor.ui.Sheets;
import top.yourzi.dialog.editor.ui.Theme;
import top.yourzi.dialog.editor.ui.UiHost;
import top.yourzi.dialog.editor.ui.UiNode;
import top.yourzi.dialog.model.DialogEntry;
import top.yourzi.dialog.model.DialogSequence;
import top.yourzi.dialog.model.DisplayItemInfo;
import top.yourzi.dialog.model.PortraitInfo;
import top.yourzi.dialog.network.NetworkHandler;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * The dialogue editor.
 *
 * <p>One screen and one retained UI tree hold the whole workspace: file toolbar, open documents,
 * structure list, writing surface, scene stage, validation and inspector. Pickers, confirmations
 * and help are overlay layers of the same tree instead of separate screens, so nothing is rebuilt
 * when they close and no state has to be smuggled across screen switches.
 *
 * <p>The workspace adapts to the GUI-scaled width: three columns when there is room, the structure
 * list folded into the view bar on medium widths, and a single column on narrow ones. The writing
 * surface is never squeezed below a readable width.
 */
public final class DialogEditorScreen extends Screen implements EditorContext {
    private static final int TOP_H = 26;
    private static final int STATUS_H = 14;
    private static final int WIDE = 760;
    private static final int MEDIUM = 520;

    private static DialogEntry nodeClipboard;

    private enum View {
        FLOW,
        GRAPH,
        STAGE,
        VALIDATION,
        STRUCTURE,
        INSPECTOR
    }

    private final EditorStore store = new EditorStore(EditorConfig.DIALOG_JSON_DIR);
    private final List<EditorDocument> documents = new ArrayList<>();
    private final Workspace workspace = new Workspace();
    private final UiHost host;
    private final Row topBar = new Row().gap(4);
    private final Button documentMenu = Button.of(Component.empty(), () -> {
    });
    private final OutlinePanel outline;
    private final FlowPanel flow;
    private final StageView stage;
    private final GraphPanel graph;
    private final ValidationPanel validation = new ValidationPanel();
    private final InspectorPanel inspector;
    private final StatusBar statusBar;
    private final Button saveButton;
    private final Button undoButton;
    private final Button redoButton;
    private final Button flowView;
    private final Button stageView;
    private final Button graphView;
    private final Button validationView;
    private final Button structureView;
    private final Button inspectorView;

    private EditorDocument active;
    private String selectedId;
    private View view = View.FLOW;
    private boolean closing;

    public DialogEditorScreen() {
        super(Component.translatable("gui.vn_edit.title"));
        EditorConfig.createDirectories();
        this.outline = new OutlinePanel(this);
        this.flow = new FlowPanel(this);
        this.stage = new StageView(this);
        this.graph = new GraphPanel(this);
        this.inspector = new InspectorPanel(this);
        this.statusBar = new StatusBar(this::summary);

        this.saveButton = this.action("save", Button.Tone.NORMAL, this::save);
        this.undoButton = this.action("undo", Button.Tone.GHOST, this::undo);
        this.redoButton = this.action("redo", Button.Tone.GHOST, this::redo);
        this.flowView = this.viewButton("view.flow", View.FLOW);
        this.stageView = this.viewButton("view.stage", View.STAGE);
        this.graphView = this.viewButton("view.graph", View.GRAPH);
        this.validationView = this.viewButton("view.validation", View.VALIDATION);
        this.structureView = this.viewButton("view.structure", View.STRUCTURE);
        this.inspectorView = this.viewButton("view.inspector", View.INSPECTOR);
        this.documentMenu.setAction(this::openDocumentMenu);
        this.documentMenu.withTooltip(Theme.tr("document.menu_tip"));

        // One bar: which file, which view, then the actions that apply to the whole file.
        this.topBar.add(this.documentMenu);
        this.topBar.add(Nodes.spacer(8));
        this.topBar.add(this.structureView);
        this.topBar.add(this.flowView);
        this.topBar.add(this.graphView);
        this.topBar.add(this.stageView);
        this.topBar.add(this.validationView);
        this.topBar.add(this.inspectorView);
        this.topBar.add(Nodes.fill());
        this.topBar.add(this.undoButton);
        this.topBar.add(this.redoButton);
        this.topBar.add(this.saveButton);
        this.topBar.add(this.action("playtest", Button.Tone.PRIMARY, this::playtest));
        this.topBar.add(this.action("help", Button.Tone.GHOST, this::openHelp));

        this.workspace.add(this.topBar);
        this.workspace.add(this.outline);
        this.workspace.add(this.flow);
        this.workspace.add(this.stage);
        this.workspace.add(this.graph);
        this.workspace.add(this.validation);
        this.workspace.add(this.inspector);
        this.workspace.add(this.statusBar);
        this.host = new UiHost(this.workspace);
        FlowCommands commands = new FlowCommands();
        this.flow.setCommands(commands);
        this.graph.setCommands(commands);
        StagingActions staging = new StagingActions();
        this.inspector.setActions(this::pickNode, staging, this::pickInventoryItem);
        this.stage.setActions(staging);
        this.validation.setOnIssueSelected(this::focusIssue);
        this.graph.setOpenDocument(this::openDocument);
        this.graph.setAllFiles(this::allFiles);
        this.restoreSession();
    }

    private Button action(String key, Button.Tone tone, Runnable action) {
        Button button = Button.of(Theme.tr("action." + key), action).tone(tone).fit();
        button.withTooltip(Theme.tr("action." + key + ".tip"));
        return button;
    }

    private void openHelp() {
        HelpSheet.open(this.host);
    }

    private Button viewButton(String key, View target) {
        return Button.of(Theme.tr(key), () -> this.showView(target)).tone(Button.Tone.TAB).fit();
    }

    /** File switcher: open files first, then the file-level commands. */
    private void openDocumentMenu() {
        List<ContextMenu.Item> items = new ArrayList<>();
        for (EditorDocument document : this.documents) {
            String mark = document == this.active ? "● " : "   ";
            String dirty = document.dirty() ? "  *" : "";
            items.add(ContextMenu.Item.of(Component.literal(mark + document.id() + dirty), () -> this.activate(document)));
        }
        if (!this.documents.isEmpty()) {
            items.add(ContextMenu.Item.separator());
        }
        items.add(ContextMenu.Item.of(Theme.tr("document.new"), this::promptNewDocument));
        items.add(ContextMenu.Item.of(Theme.tr("document.open"), this::promptOpenDocument));
        items.add(ContextMenu.Item.of(Theme.tr("document.import"), this::promptImport));
        if (this.active != null) {
            EditorDocument current = this.active;
            items.add(ContextMenu.Item.separator());
            items.add(ContextMenu.Item.of(Theme.tr("document.settings"), this::editSequence));
            items.add(ContextMenu.Item.of(Theme.tr("document.close"), () -> this.closeDocument(current)));
        }
        ContextMenu.open(this.host, this.documentMenu.x(), this.documentMenu.bottom() + 2, null, items);
    }

    private void updateDocumentLabel() {
        String label = this.active == null ? Theme.tr("document.none").getString()
                : this.active.id() + (this.active.dirty() ? " *" : "");
        this.documentMenu.setLabel(Component.literal(label + "  ▾"));
        this.documentMenu.prefWidth(Mth.clamp(Theme.font().width(label) + 34, 90, 200));
    }

    // ===== EditorContext =====

    @Override
    public EditorDocument document() {
        return this.active;
    }

    @Override
    public String selectedId() {
        return this.selectedId;
    }

    @Override
    public void select(String nodeId) {
        if (Objects.equals(this.selectedId, nodeId)) {
            return;
        }
        this.selectedId = nodeId;
        this.outline.refresh();
        this.outline.revealSelected();
        this.flow.refresh(true);
        this.bindSelection(false);
        if (this.view == View.GRAPH) {
            this.graph.focusSelected();
        }
    }

    @Override
    public void touch(boolean merge) {
        if (this.active != null && this.active.touch(merge)) {
            this.outline.refresh();
            this.flow.refresh(false);
            this.refreshValidation();
            this.refreshGraph();
        }
    }

    @Override
    public void touchStructure() {
        if (this.active != null) {
            this.active.touch(false);
        }
        this.refreshAll();
    }

    @Override
    public void status(Component message, StatusKind kind) {
        this.statusBar.set(message, kind);
    }

    private DialogEntry selectedEntry() {
        return NodeGraph.byId(this.sequence(), this.selectedId);
    }

    private void bindSelection(boolean force) {
        DialogEntry entry = this.selectedEntry();
        this.inspector.bind(entry, this.sequence(), force);
        this.stage.bind(entry);
    }

    /** Rebuilds every view from the document; used after structural edits, undo and switching files. */
    private void refreshAll() {
        if (this.selectedId != null && this.selectedEntry() == null) {
            this.selectedId = null;
        }
        this.outline.refresh();
        this.flow.refresh(false);
        this.bindSelection(true);
        this.refreshValidation();
        this.refreshGraph();
    }

    private void refreshGraph() {
        if (this.view == View.GRAPH) {
            this.graph.refresh();
        }
    }

    /** Every dialogue file, using the in-memory version of open files so unsaved edits show. */
    private List<DialogSequence> allFiles() {
        List<DialogSequence> files = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        for (EditorDocument document : this.documents) {
            files.add(document.sequence());
            seen.add(document.id());
        }
        for (String id : this.store.listIds()) {
            if (seen.contains(id)) {
                continue;
            }
            try {
                files.add(this.store.read(id));
            } catch (IOException e) {
                Dialog.LOGGER.warn("Graph skipped unreadable dialogue '{}'", id);
            }
        }
        return files;
    }

    private void refreshValidation() {
        if (this.view == View.VALIDATION) {
            this.validation.setIssues(this.sequence() == null ? List.of() : DialogValidator.validate(this.sequence()));
        }
    }

    private String summary() {
        if (this.active == null) {
            return Theme.tr("status.no_document").getString();
        }
        String state = Theme.tr(this.active.dirty() ? "status.modified" : "status.clean").getString();
        String selection = this.selectedId == null ? "" : "  ·  " + this.selectedId;
        return this.active.id() + "  ·  " + Theme.tr("status.nodes", NodeGraph.entries(this.sequence()).size()).getString()
                + "  ·  " + state + selection;
    }

    // ===== documents =====

    private void restoreSession() {
        EditorStore.Session session = this.store.readSession();
        List<String> ids = new ArrayList<>(session.openIds() == null ? List.of() : session.openIds());
        if (ids.isEmpty()) {
            // First launch: open one file rather than every file in the folder.
            List<String> available = this.store.listIds();
            if (!available.isEmpty()) {
                ids.add(available.get(0));
            }
        }
        for (String id : ids) {
            if (this.find(id) != null) {
                continue;
            }
            try {
                this.documents.add(new EditorDocument(this.store.read(id)));
            } catch (IOException e) {
                Dialog.LOGGER.warn("Editor skipped dialogue '{}': {}", id, e.getMessage());
            }
        }
        EditorDocument preferred = this.find(session.activeId());
        this.active = preferred != null ? preferred : this.documents.isEmpty() ? null : this.documents.get(0);
        this.refreshAll();
        if (this.active == null) {
            this.status(Theme.tr("status.welcome"), StatusKind.INFO);
        }
    }

    private EditorDocument find(String id) {
        for (EditorDocument document : this.documents) {
            if (Objects.equals(document.id(), id)) {
                return document;
            }
        }
        return null;
    }

    private void activate(EditorDocument document) {
        if (document == this.active) {
            return;
        }
        this.active = document;
        this.selectedId = null;
        this.refreshAll();
        this.persistSession();
    }

    private void persistSession() {
        List<String> ids = new ArrayList<>();
        for (EditorDocument document : this.documents) {
            ids.add(document.id());
        }
        this.store.writeSession(ids, this.active == null ? null : this.active.id());
    }

    private boolean idTaken(String id) {
        if (this.find(id) != null) {
            return true;
        }
        try {
            return Files.exists(this.store.pathForId(id));
        } catch (IOException e) {
            return true;
        }
    }

    private void promptNewDocument() {
        Sheets.prompt(this.host, Theme.tr("new_dialog.title"), Theme.tr("new_dialog.hint"), "new_dialog",
                Theme.tr("create"), false, this::createDocument);
    }

    private void createDocument(String requested) {
        String id = requested == null ? "" : requested.trim();
        if (!EditorStore.isSafeId(id)) {
            this.status(Theme.tr("status.id_invalid", id), StatusKind.ERROR);
            return;
        }
        if (this.idTaken(id)) {
            this.status(Theme.tr("status.id_exists", id), StatusKind.ERROR);
            return;
        }
        DialogSequence sequence = new DialogSequence();
        sequence.setId(id);
        DialogEntry first = NodeGraph.create("start");
        sequence.setEntries(new DialogEntry[]{first});
        sequence.setStartId(first.getId());
        EditorDocument document = new EditorDocument(sequence);
        this.documents.add(document);
        this.activate(document);
        this.select(first.getId());
        this.status(Theme.tr("status.created", id), StatusKind.SUCCESS);
    }

    private void promptOpenDocument() {
        PickerSheet.open(this.host, Theme.tr("open.title"), this.store.listIds(),
                id -> this.find(id) != null ? Theme.tr("open.already_open").getString() : "",
                this::openDocument, null);
    }

    private void openDocument(String id) {
        if (id == null || id.isBlank()) {
            return;
        }
        EditorDocument existing = this.find(id);
        if (existing != null) {
            this.activate(existing);
            return;
        }
        try {
            EditorDocument document = new EditorDocument(this.store.read(id));
            this.documents.add(document);
            this.activate(document);
            this.status(Theme.tr("status.opened", id), StatusKind.SUCCESS);
        } catch (IOException e) {
            this.status(Theme.tr("status.open_failed", id), StatusKind.ERROR);
        }
    }

    /** Copies a dialogue from the loaded resource packs into the editor directory and opens it. */
    private void promptImport() {
        List<String> ids = new ArrayList<>(DialogManager.getInstance().getAllDialogSequences().keySet());
        ids.sort(String.CASE_INSENSITIVE_ORDER);
        PickerSheet.open(this.host, Theme.tr("import.title"), ids,
                id -> this.idTaken(id) ? Theme.tr("import.exists").getString() : "", id -> {
                    if (id == null || id.isBlank()) {
                        return;
                    }
                    if (this.idTaken(id)) {
                        Sheets.confirm(this.host, Theme.tr("import.title"), Theme.tr("import.overwrite", id),
                                Theme.tr("import.overwrite_confirm"), true, () -> this.importDocument(id));
                    } else {
                        this.importDocument(id);
                    }
                }, null);
    }

    private void importDocument(String id) {
        DialogSequence sequence = DialogManager.getInstance().getDialogSequence(id);
        if (sequence == null || !EditorStore.isSafeId(sequence.getId())) {
            this.status(Theme.tr("import.failed", id), StatusKind.ERROR);
            return;
        }
        try {
            this.store.write(sequence);
        } catch (IOException e) {
            this.status(Theme.tr("import.failed", id), StatusKind.ERROR);
            return;
        }
        EditorDocument open = this.find(sequence.getId());
        if (open != null) {
            this.documents.remove(open);
            if (this.active == open) {
                this.active = null;
            }
        }
        this.openDocument(sequence.getId());
        this.status(Theme.tr("import.success", sequence.getId()), StatusKind.SUCCESS);
    }

    private void closeDocument(EditorDocument document) {
        Runnable close = () -> {
            int index = this.documents.indexOf(document);
            this.documents.remove(document);
            if (this.active == document) {
                this.active = null;
                this.selectedId = null;
                if (!this.documents.isEmpty()) {
                    this.active = this.documents.get(Mth.clamp(index, 0, this.documents.size() - 1));
                }
            }
            this.refreshAll();
            this.persistSession();
        };
        if (document.dirty()) {
            Sheets.confirm(this.host, Theme.tr("close_tab.title"), Theme.tr("close_tab.dirty", document.id()),
                    Theme.tr("close_tab.discard"), true, close);
        } else {
            close.run();
        }
    }

    private void deleteDocument(EditorDocument document) {
        Sheets.confirm(this.host, Theme.tr("delete_dialog.title"), Theme.tr("delete_dialog.message", document.id()),
                Theme.tr("action.delete"), true, () -> {
                    try {
                        this.store.delete(document.id());
                    } catch (IOException e) {
                        this.status(Theme.tr("status.delete_failed", document.id()), StatusKind.ERROR);
                        return;
                    }
                    document.markSaved();
                    this.closeDocument(document);
                    this.status(Theme.tr("status.deleted", document.id()), StatusKind.SUCCESS);
                });
    }

    private void editSequence() {
        if (this.active == null) {
            return;
        }
        EditorDocument document = this.active;
        SequenceSheet.open(this.host, document.sequence(), this::pickNode, result -> {
            DialogSequence sequence = document.sequence();
            if (!result.id().equals(document.id())) {
                if (this.idTaken(result.id())) {
                    this.status(Theme.tr("status.id_exists", result.id()), StatusKind.ERROR);
                    return;
                }
                try {
                    // The file moves first, so a failed rename never leaves the model ahead of disk.
                    this.store.moveFile(document.id(), result.id());
                } catch (IOException e) {
                    this.status(Theme.tr("status.rename_failed"), StatusKind.ERROR);
                    return;
                }
                top.yourzi.dialog.editor.LayoutStore.rename(document.id(), result.id());
                sequence.setId(result.id());
            }
            sequence.setTitle(result.title());
            sequence.setDescription(result.description());
            sequence.setEffect(result.effect());
            sequence.setStartId(result.startId());
            sequence.setAllowClose(result.allowClose() ? Boolean.TRUE : null);
            this.touchStructure();
            this.persistSession();
            this.status(Theme.tr("props.saved"), StatusKind.SUCCESS);
        }, () -> this.deleteDocument(document));
    }

    // ===== file actions =====

    private boolean write(EditorDocument document) {
        try {
            this.store.write(document.sequence());
            document.markSaved();
            return true;
        } catch (IOException e) {
            Dialog.LOGGER.error("Failed to save dialogue '{}'", document.id(), e);
            this.status(Theme.tr("status.save_failed", document.id()), StatusKind.ERROR);
            return false;
        }
    }

    private void save() {
        if (this.active == null || !this.write(this.active)) {
            return;
        }
        int errors = DialogValidator.count(DialogValidator.validate(this.active.sequence()), DialogValidator.Severity.ERROR);
        if (errors == 0) {
            this.status(Theme.tr("status.saved", this.active.id()), StatusKind.SUCCESS);
        } else {
            this.status(Theme.tr("status.saved_with_errors", this.active.id(), errors), StatusKind.WARNING);
        }
        if (Minecraft.getInstance().player != null) {
            NetworkHandler.sendExecuteCommandToServer("dialog reload");
        }
    }

    private void undo() {
        if (this.active != null && this.active.undo() != null) {
            this.refreshAll();
            this.status(Theme.tr("status.undone"), StatusKind.INFO);
        }
    }

    private void redo() {
        if (this.active != null && this.active.redo() != null) {
            this.refreshAll();
            this.status(Theme.tr("status.redone"), StatusKind.INFO);
        }
    }

    private void playtest() {
        if (this.active == null) {
            return;
        }
        List<DialogValidator.Issue> issues = DialogValidator.validate(this.active.sequence());
        int errors = DialogValidator.count(issues, DialogValidator.Severity.ERROR);
        if (errors > 0) {
            this.showView(View.VALIDATION);
            this.status(Theme.tr("status.playtest_blocked", errors), StatusKind.ERROR);
            return;
        }
        if (!this.write(this.active)) {
            return;
        }
        DialogSequence preview = DialogManager.GSON.fromJson(DialogManager.GSON.toJson(this.active.sequence()),
                DialogSequence.class);
        // Only the playtest copy may be closed with Escape; the authored setting is left untouched.
        preview.setAllowClose(true);
        DialogEntry from = this.selectedEntry();
        if (from != null && Screen.hasShiftDown()) {
            preview.setStartId(from.getId());
        }
        DialogManager.getInstance().setTestReturnScreen(this);
        DialogManager.getInstance().receiveAndShowPlayerSpecificDialog(this.active.id(), DialogManager.GSON.toJson(preview));
    }

    // ===== node actions =====

    private void addNodeAfterSelection() {
        if (this.active == null) {
            this.promptNewDocument();
            return;
        }
        DialogSequence sequence = this.active.sequence();
        DialogEntry anchor = this.selectedEntry();
        String base = anchor == null ? "node_" + (NodeGraph.entries(sequence).size() + 1) : anchor.getId() + "_next";
        DialogEntry entry = NodeGraph.create(NodeGraph.uniqueId(sequence, base));
        if (anchor == null) {
            NodeGraph.add(sequence, entry);
        } else {
            NodeGraph.insertAfter(sequence, anchor, entry);
        }
        this.selectedId = entry.getId();
        this.touchStructure();
        this.flow.refresh(true);
        this.inspector.setActiveTab(0);
        this.status(Theme.tr("status.node_added", entry.getId()), StatusKind.SUCCESS);
    }

    private void duplicateSelected() {
        DialogEntry entry = this.selectedEntry();
        if (entry == null) {
            return;
        }
        DialogEntry copy = NodeGraph.duplicate(this.sequence(), entry);
        this.selectedId = copy.getId();
        this.touchStructure();
        this.flow.refresh(true);
        this.status(Theme.tr("status.node_duplicated", copy.getId()), StatusKind.SUCCESS);
    }

    private void copySelected() {
        DialogEntry entry = this.selectedEntry();
        if (entry != null) {
            nodeClipboard = entry.deepCopy();
            this.status(Theme.tr("status.node_copied", entry.getId()), StatusKind.SUCCESS);
        }
    }

    private void paste() {
        if (this.active == null || nodeClipboard == null) {
            this.status(Theme.tr("status.clipboard_empty"), StatusKind.WARNING);
            return;
        }
        DialogEntry copy = NodeGraph.paste(this.sequence(), this.selectedEntry(), nodeClipboard);
        this.selectedId = copy.getId();
        this.touchStructure();
        this.flow.refresh(true);
        this.status(Theme.tr("status.node_pasted", copy.getId()), StatusKind.SUCCESS);
    }

    private void deleteSelected() {
        DialogEntry entry = this.selectedEntry();
        if (entry == null) {
            return;
        }
        int references = NodeGraph.referenceCounts(this.sequence()).getOrDefault(entry.getId(), 0);
        Component message = references > 0
                ? Theme.tr("delete_node.message_refs", entry.getId(), references)
                : Theme.tr("delete_node.message", entry.getId());
        Sheets.confirm(this.host, Theme.tr("delete_node.title"), message, Theme.tr("action.delete"), true, () -> {
            List<DialogEntry> entries = NodeGraph.entries(this.sequence());
            int index = entries.indexOf(entry);
            NodeGraph.remove(this.sequence(), entry);
            List<DialogEntry> remaining = NodeGraph.entries(this.sequence());
            this.selectedId = remaining.isEmpty() ? null
                    : remaining.get(Mth.clamp(index, 0, remaining.size() - 1)).getId();
            this.touchStructure();
            this.status(Theme.tr("status.node_deleted", entry.getId()), StatusKind.INFO);
        });
    }

    private void moveSelected(int delta) {
        DialogEntry entry = this.selectedEntry();
        if (entry != null && NodeGraph.move(this.sequence(), entry, delta)) {
            this.touchStructure();
            this.flow.refresh(true);
        }
    }

    private void renameSelected() {
        DialogEntry entry = this.selectedEntry();
        if (entry == null) {
            return;
        }
        Sheets.prompt(this.host, Theme.tr("rename.title"), Theme.tr("rename.hint"), entry.getId(), Theme.tr("apply"),
                false, requested -> {
                    String newId = requested.trim();
                    if (newId.equals(entry.getId())) {
                        return;
                    }
                    String oldId = entry.getId();
                    if (newId.isEmpty() || !newId.matches("[A-Za-z0-9_\\-.]+")
                            || !NodeGraph.rename(this.sequence(), entry, newId)) {
                        this.status(Theme.tr("status.rename_failed"), StatusKind.ERROR);
                        return;
                    }
                    this.graph.renameNode(oldId, newId);
                    this.selectedId = newId;
                    this.touchStructure();
                    this.status(Theme.tr("status.renamed", newId), StatusKind.SUCCESS);
                });
    }

    private void stepSelection(int delta) {
        List<DialogEntry> entries = NodeGraph.entries(this.sequence());
        if (entries.isEmpty()) {
            return;
        }
        int index = entries.indexOf(this.selectedEntry());
        int next = index < 0 ? 0 : Mth.clamp(index + delta, 0, entries.size() - 1);
        this.select(entries.get(next).getId());
    }

    private void focusIssue(DialogValidator.Issue issue) {
        if (issue == null) {
            return;
        }
        this.showView(View.FLOW);
        if (issue.nodeId() != null && NodeGraph.byId(this.sequence(), issue.nodeId()) != null) {
            this.select(issue.nodeId());
            boolean routing = issue.code().contains("NEXT") || issue.code().contains("OPTION")
                    || issue.code().contains("TARGET");
            this.inspector.setActiveTab(routing ? 1 : 0);
        }
    }

    private void showView(View target) {
        this.view = target;
        if (target == View.VALIDATION) {
            this.validation.setIssues(this.sequence() == null ? List.of() : DialogValidator.validate(this.sequence()));
        }
        if (target == View.GRAPH) {
            this.graph.refresh();
        }
        this.workspace.invalidateLayout();
    }

    // ===== pickers =====

    private void pickNode(Consumer<String> onSelected) {
        List<String> ids = new ArrayList<>();
        for (DialogEntry entry : NodeGraph.entries(this.sequence())) {
            ids.add(entry.getId());
        }
        PickerSheet.open(this.host, Theme.tr("picker.node"), ids, id -> {
            DialogEntry entry = NodeGraph.byId(this.sequence(), id);
            return entry == null ? "" : Theme.ellipsize(top.yourzi.dialog.editor.TextCodec.preview(entry.getText()), 150);
        }, onSelected, Theme.tr("picker.none"));
    }

    private void pickInventoryItem() {
        List<String> ids = new ArrayList<>();
        if (Minecraft.getInstance().player != null) {
            for (int slot = 0; slot < Minecraft.getInstance().player.getInventory().getContainerSize(); slot++) {
                ItemStack stack = Minecraft.getInstance().player.getInventory().getItem(slot);
                if (stack.isEmpty()) {
                    continue;
                }
                ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
                String value = key + " ×" + stack.getCount();
                if (!ids.contains(value)) {
                    ids.add(value);
                }
            }
        }
        PickerSheet.open(this.host, Theme.tr("picker.item"), ids, value -> {
            if (value == null || value.isBlank()) {
                return;
            }
            int separator = value.lastIndexOf(" ×");
            String id = separator < 0 ? value : value.substring(0, separator);
            int count = 1;
            if (separator >= 0) {
                try {
                    count = Integer.parseInt(value.substring(separator + 2));
                } catch (NumberFormatException ignored) {
                    count = 1;
                }
            }
            this.inspector.addItem(new DisplayItemInfo(id, count, null));
        });
    }

    private static List<String> listFiles(Path directory, String... extensions) {
        List<String> names = new ArrayList<>();
        if (!Files.isDirectory(directory)) {
            return names;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path path : stream) {
                String name = path.getFileName().toString();
                String lower = name.toLowerCase(Locale.ROOT);
                for (String extension : extensions) {
                    if (Files.isRegularFile(path) && lower.endsWith("." + extension)) {
                        names.add(name);
                        break;
                    }
                }
            }
        } catch (IOException e) {
            Dialog.LOGGER.error("Failed to list {}", directory, e);
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    /** Asset pickers shared by the staging tab and the stage view. */
    private final class StagingActions implements StagingTab.Actions {
        @Override
        public void pickBackgroundFile() {
            PickerSheet.openFiles(DialogEditorScreen.this.host, Theme.tr("picker.background"), EditorConfig.BACKGROUNDS_DIR,
                    listFiles(EditorConfig.BACKGROUNDS_DIR, "png", "jpg", "jpeg"),
                    DialogEditorScreen.this.inspector::setBackgroundPath);
        }

        @Override
        public void pickBuiltinBackground() {
            PickerSheet.open(DialogEditorScreen.this.host, Theme.tr("picker.builtin"),
                    AssetService.listBuiltin("textures/backgrounds/"), DialogEditorScreen.this.inspector::setBackgroundPath);
        }

        @Override
        public void pickPortraitFile() {
            PickerSheet.openFiles(DialogEditorScreen.this.host, Theme.tr("picker.portrait"), EditorConfig.PORTRAITS_DIR,
                    listFiles(EditorConfig.PORTRAITS_DIR, "png", "jpg", "jpeg"), DialogEditorScreen.this.stage::addPortrait);
        }

        @Override
        public void pickBuiltinPortrait() {
            PickerSheet.open(DialogEditorScreen.this.host, Theme.tr("picker.builtin"),
                    AssetService.listBuiltin("textures/portraits/"), DialogEditorScreen.this.stage::addPortrait);
        }

        @Override
        public void pickAudio() {
            PickerSheet.openFiles(DialogEditorScreen.this.host, Theme.tr("picker.audio"), EditorConfig.SOUNDS_DIR,
                    listFiles(EditorConfig.SOUNDS_DIR, "ogg", "wav"), DialogEditorScreen.this.inspector::setAudioPath);
        }

        @Override
        public void playAudio() {
            DialogEntry entry = DialogEditorScreen.this.selectedEntry();
            Path file = entry == null ? null : EditorConfig.resolveInside(EditorConfig.SOUNDS_DIR, entry.getAudioPath());
            if (file == null || !Files.isRegularFile(file)) {
                DialogEditorScreen.this.status(Theme.tr("status.audio_missing"), StatusKind.WARNING);
                return;
            }
            AudioPreviewPlayer.play(file.toFile());
        }

        @Override
        public void openStage(PortraitInfo portrait) {
            DialogEditorScreen.this.stage.focus(portrait);
            DialogEditorScreen.this.showView(View.STAGE);
        }
    }

    private final class FlowCommands implements FlowPanel.Commands {
        @Override
        public void addNode() {
            DialogEditorScreen.this.addNodeAfterSelection();
        }

        @Override
        public void duplicateSelected() {
            DialogEditorScreen.this.duplicateSelected();
        }

        @Override
        public void deleteSelected() {
            DialogEditorScreen.this.deleteSelected();
        }

        @Override
        public void moveSelected(int delta) {
            DialogEditorScreen.this.moveSelected(delta);
        }

        @Override
        public void renameSelected() {
            DialogEditorScreen.this.renameSelected();
        }
    }

    // ===== layout =====

    /**
     * Root of the tree. Places every region explicitly so each width class is readable in one
     * method: three columns when wide, the outline folded into the view tabs when medium, and only
     * the centre column (with outline and properties as tabs) when narrow.
     */
    private final class Workspace extends UiNode {
        private String documentLabel;

        @Override
        protected void onLayout() {
            DialogEditorScreen screen = DialogEditorScreen.this;
            int width = this.width();
            int height = this.height();
            screen.topBar.setBounds(6, 4, width - 12, TOP_H - 8);
            screen.statusBar.setBounds(0, height - STATUS_H, width, STATUS_H);

            int top = TOP_H;
            int bottom = height - STATUS_H;
            boolean wide = width >= WIDE;
            boolean medium = !wide && width >= MEDIUM;
            int treeWidth = wide ? Mth.clamp(width / 5, 180, 260) : 0;
            int inspectorWidth = wide ? Mth.clamp(width * 3 / 10, 260, 380) : medium ? Mth.clamp(width * 2 / 5, 240, 320) : 0;
            int gutter = 1;

            if (screen.view == View.STRUCTURE && treeWidth > 0 || screen.view == View.INSPECTOR && inspectorWidth > 0) {
                screen.view = View.FLOW;
            }
            View current = screen.view;
            int centerX = treeWidth == 0 ? 0 : treeWidth + gutter;
            int centerRight = inspectorWidth == 0 ? width : width - inspectorWidth - gutter;
            int centerWidth = centerRight - centerX;

            screen.structureView.setVisible(treeWidth == 0);
            screen.inspectorView.setVisible(inspectorWidth == 0);
            screen.structureView.selected(current == View.STRUCTURE);
            screen.flowView.selected(current == View.FLOW);
            screen.stageView.selected(current == View.STAGE);
            screen.graphView.selected(current == View.GRAPH);
            screen.validationView.selected(current == View.VALIDATION);
            screen.inspectorView.selected(current == View.INSPECTOR);

            place(screen.outline, treeWidth > 0 || current == View.STRUCTURE,
                    treeWidth > 0 ? 0 : centerX, top, treeWidth > 0 ? treeWidth : centerWidth, bottom - top);
            place(screen.inspector, inspectorWidth > 0 || current == View.INSPECTOR,
                    inspectorWidth > 0 ? width - inspectorWidth : centerX, top,
                    inspectorWidth > 0 ? inspectorWidth : centerWidth, bottom - top);
            place(screen.flow, current == View.FLOW, centerX, top, centerWidth, bottom - top);
            place(screen.stage, current == View.STAGE, centerX, top, centerWidth, bottom - top);
            place(screen.graph, current == View.GRAPH, centerX, top, centerWidth, bottom - top);
            place(screen.validation, current == View.VALIDATION, centerX, top, centerWidth, bottom - top);
        }

        private static void place(UiNode node, boolean visible, int x, int y, int width, int height) {
            node.setVisible(visible);
            if (visible) {
                node.setBounds(x, y, width, height);
            }
        }

        @Override
        protected void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            DialogEditorScreen screen = DialogEditorScreen.this;
            graphics.fill(0, 0, this.width(), this.height(), Theme.BG);
            graphics.fill(0, TOP_H - 1, this.width(), TOP_H, Theme.BORDER);
            String label = screen.active == null ? "" : screen.active.id() + screen.active.dirty();
            if (!label.equals(this.documentLabel)) {
                this.documentLabel = label;
                screen.updateDocumentLabel();
            }
            screen.undoButton.active(screen.active != null && screen.active.canUndo());
            screen.redoButton.active(screen.active != null && screen.active.canRedo());
            screen.saveButton.active(screen.active != null && screen.active.dirty());
        }
    }
    // ===== Screen plumbing =====

    @Override
    protected void init() {
        super.init();
        this.host.resize(this.width, this.height);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.host.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, Theme.BG);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return this.host.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return this.host.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return this.host.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        this.host.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return this.host.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return this.host.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean overlay = this.host.hasLayers();
        if (this.host.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (overlay) {
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && this.host.focusedNode() != null) {
            this.host.focus(null);
            return true;
        }
        boolean control = Screen.hasControlDown();
        boolean text = this.host.hasTextFocus();
        if (control) {
            switch (keyCode) {
                case GLFW.GLFW_KEY_S -> this.save();
                case GLFW.GLFW_KEY_N -> this.promptNewDocument();
                case GLFW.GLFW_KEY_O -> this.promptOpenDocument();
                case GLFW.GLFW_KEY_Z -> {
                    if (Screen.hasShiftDown()) {
                        this.redo();
                    } else {
                        this.undo();
                    }
                }
                case GLFW.GLFW_KEY_Y -> this.redo();
                case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> this.playtest();
                case GLFW.GLFW_KEY_TAB -> this.cycleDocument(Screen.hasShiftDown() ? -1 : 1);
                case GLFW.GLFW_KEY_D -> {
                    if (text) {
                        return false;
                    }
                    this.duplicateSelected();
                }
                case GLFW.GLFW_KEY_C -> {
                    if (text) {
                        return false;
                    }
                    this.copySelected();
                }
                case GLFW.GLFW_KEY_V -> {
                    if (text) {
                        return false;
                    }
                    this.paste();
                }
                default -> {
                    return super.keyPressed(keyCode, scanCode, modifiers);
                }
            }
            return true;
        }
        if (text) {
            return false;
        }
        switch (keyCode) {
            case GLFW.GLFW_KEY_F1 -> this.openHelp();
            case GLFW.GLFW_KEY_INSERT -> this.addNodeAfterSelection();
            case GLFW.GLFW_KEY_DELETE -> this.deleteSelected();
            case GLFW.GLFW_KEY_F2 -> this.renameSelected();
            case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_DOWN -> {
                int delta = keyCode == GLFW.GLFW_KEY_UP ? -1 : 1;
                if (Screen.hasAltDown()) {
                    this.moveSelected(delta);
                } else {
                    this.stepSelection(delta);
                }
            }
            default -> {
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
        }
        return true;
    }

    private void cycleDocument(int delta) {
        if (this.documents.size() > 1) {
            int index = Math.floorMod(this.documents.indexOf(this.active) + delta, this.documents.size());
            this.activate(this.documents.get(index));
        }
    }

    @Override
    public void onClose() {
        if (this.closing) {
            return;
        }
        List<EditorDocument> dirty = new ArrayList<>();
        for (EditorDocument document : this.documents) {
            if (document.dirty()) {
                dirty.add(document);
            }
        }
        if (dirty.isEmpty()) {
            this.close();
            return;
        }
        Sheets.choose(this.host, Theme.tr("unsaved.title"), Theme.tr("unsaved.message", dirty.size()),
                List.of(Theme.tr("unsaved.save_all"), Theme.tr("unsaved.discard")), choice -> {
                    if (choice == 0) {
                        for (EditorDocument document : dirty) {
                            if (!this.write(document)) {
                                return;
                            }
                        }
                    }
                    this.close();
                });
    }

    private void close() {
        this.closing = true;
        this.persistSession();
        AudioPreviewPlayer.stop();
        AssetService.releaseAll();
        Minecraft.getInstance().setScreen(null);
    }
}
