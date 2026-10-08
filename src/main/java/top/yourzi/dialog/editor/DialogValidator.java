package top.yourzi.dialog.editor;

import com.google.gson.JsonElement;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import top.yourzi.dialog.editor.screen.NodeGraph;
import top.yourzi.dialog.model.DialogEntry;
import top.yourzi.dialog.model.DialogOption;
import top.yourzi.dialog.model.DialogSequence;
import top.yourzi.dialog.model.DisplayItemInfo;
import top.yourzi.dialog.model.PortraitInfo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Checks one dialogue for the things a writer would otherwise only find by playing it.
 *
 * <p>The flow is read exactly as the game follows it (see {@link NodeGraph#successors}), so a node
 * that is simply the next line in the file counts as connected. Errors are things the game cannot
 * follow - a jump to a node that does not exist. Warnings are things that play but are probably not
 * meant: lines nothing leads to, loops the player cannot leave, missing images, sounds, items and
 * translations.
 */
public final class DialogValidator {
    public enum Severity {
        ERROR,
        WARNING
    }

    /** Inspector tabs an issue is fixed on. */
    public static final int TAB_CONTENT = 0;
    public static final int TAB_BRANCH = 1;
    public static final int TAB_STAGING = 2;
    public static final int TAB_LOGIC = 3;

    /**
     * @param code   message key suffix, {@code gui.vn_edit.issue.<code lower-case>}
     * @param nodeId node to jump to, or null for file-level issues
     * @param detail the specific thing that is wrong (a target id, a file name), or empty
     * @param tab    inspector tab where it is fixed
     */
    public record Issue(Severity severity, String code, String nodeId, String detail, int tab) {
    }

    private DialogValidator() {
    }

    public static int count(List<Issue> issues, Severity severity) {
        int total = 0;
        for (Issue issue : issues) {
            if (issue.severity() == severity) {
                total++;
            }
        }
        return total;
    }

    public static List<Issue> validate(DialogSequence sequence) {
        List<Issue> issues = new ArrayList<>();
        List<DialogEntry> entries = NodeGraph.entries(sequence);
        if (entries.isEmpty()) {
            issues.add(new Issue(Severity.ERROR, "EMPTY_ENTRIES", null, "", TAB_CONTENT));
            return issues;
        }

        Map<String, DialogEntry> byId = new HashMap<>();
        for (DialogEntry entry : entries) {
            if (entry.getId() == null || entry.getId().isBlank()) {
                issues.add(new Issue(Severity.ERROR, "INVALID_ID", null, "", TAB_CONTENT));
            } else if (byId.putIfAbsent(entry.getId(), entry) != null) {
                issues.add(new Issue(Severity.ERROR, "DUPLICATE_ID", entry.getId(), entry.getId(), TAB_CONTENT));
            }
        }
        String startId = sequence.getStartId();
        if (startId != null && !startId.isBlank() && !byId.containsKey(startId)) {
            issues.add(new Issue(Severity.ERROR, "INVALID_START", null, startId, TAB_CONTENT));
        }

        Set<String> unreachable = NodeGraph.unreachable(sequence);
        Set<String> trapped = trapped(sequence, entries, byId);
        for (DialogEntry entry : entries) {
            String id = entry.getId();
            if (id == null || id.isBlank()) {
                continue;
            }
            checkFlow(sequence, entry, byId, issues);
            if (unreachable.contains(id)) {
                issues.add(new Issue(Severity.WARNING, "UNREACHABLE_NODE", id, "", TAB_BRANCH));
            } else if (trapped.contains(id)) {
                issues.add(new Issue(Severity.WARNING, "NO_EXIT", id, "", TAB_BRANCH));
            }
            checkText(entry, issues);
            checkAssets(entry, issues);
            checkItems(entry, issues);
        }
        issues.sort(Comparator.comparing(Issue::severity));
        return issues;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static void checkFlow(DialogSequence sequence, DialogEntry entry, Map<String, DialogEntry> byId,
                                  List<Issue> issues) {
        String id = entry.getId();
        boolean hasOptions = NodeGraph.hasOptions(entry);
        if (!blank(entry.getNextId())) {
            if (!byId.containsKey(entry.getNextId())) {
                issues.add(new Issue(Severity.ERROR, "DANGLING_NEXT", id, entry.getNextId(), TAB_BRANCH));
            } else if (hasOptions) {
                // The game only follows the choices, so this jump never happens.
                issues.add(new Issue(Severity.WARNING, "NEXT_IGNORED", id, entry.getNextId(), TAB_BRANCH));
            } else if (entry.isEndDialog()) {
                issues.add(new Issue(Severity.WARNING, "NEXT_AFTER_END", id, entry.getNextId(), TAB_BRANCH));
            }
        }
        if (!hasOptions) {
            return;
        }
        for (int i = 0; i < entry.getOptions().length; i++) {
            DialogOption option = entry.getOptions()[i];
            if (option == null) {
                continue;
            }
            String label = TextCodec.preview(option.getText());
            String name = label.isEmpty() ? "#" + (i + 1) : label;
            if (label.isEmpty()) {
                issues.add(new Issue(Severity.WARNING, "EMPTY_OPTION_TEXT", id, "#" + (i + 1), TAB_BRANCH));
            }
            if (!blank(option.getTargetId()) && !byId.containsKey(option.getTargetId())) {
                issues.add(new Issue(Severity.ERROR, "DANGLING_OPTION_TARGET", id,
                        name + " → " + option.getTargetId(), TAB_BRANCH));
            }
            checkTranslation(option.getText(), id, TAB_BRANCH, issues);
        }
    }

    /**
     * Reachable nodes from which the dialogue can never end: every path from them runs in a loop.
     * Found by walking backwards from all the places a dialogue can end.
     */
    private static Set<String> trapped(DialogSequence sequence, List<DialogEntry> entries, Map<String, DialogEntry> byId) {
        Map<String, List<String>> incoming = new HashMap<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        Set<String> canEnd = new HashSet<>();
        for (DialogEntry entry : entries) {
            String id = entry.getId();
            if (blank(id) || byId.get(id) != entry) {
                continue;
            }
            for (String target : NodeGraph.successors(sequence, entry)) {
                incoming.computeIfAbsent(target, key -> new ArrayList<>()).add(id);
            }
            if (NodeGraph.canEndAt(sequence, entry) && canEnd.add(id)) {
                queue.add(id);
            }
        }
        while (!queue.isEmpty()) {
            for (String previous : incoming.getOrDefault(queue.poll(), List.of())) {
                if (canEnd.add(previous)) {
                    queue.add(previous);
                }
            }
        }
        Set<String> trapped = new HashSet<>(byId.keySet());
        trapped.removeAll(canEnd);
        return trapped;
    }

    private static void checkText(DialogEntry entry, List<Issue> issues) {
        String id = entry.getId();
        if (TextCodec.preview(entry.getText()).isEmpty()) {
            issues.add(new Issue(Severity.WARNING, "EMPTY_TEXT", id, "", TAB_CONTENT));
        }
        checkTranslation(entry.getText(), id, TAB_CONTENT, issues);
        checkTranslation(entry.getSpeaker(), id, TAB_CONTENT, issues);
    }

    /** A translation key that resolves nowhere shows the raw key to the player. */
    private static void checkTranslation(JsonElement element, String id, int tab, List<Issue> issues) {
        if (!TextCodec.isTranslation(element)) {
            return;
        }
        String key = TextCodec.translationKey(element);
        if (blank(key) || TextCodec.ConfigLang.get(key) != null || TextCodec.fallback(element) != null
                || I18n.exists(key)) {
            return;
        }
        issues.add(new Issue(Severity.WARNING, "MISSING_TRANSLATION", id, key, tab));
    }

    private static void checkAssets(DialogEntry entry, List<Issue> issues) {
        String id = entry.getId();
        if (entry.getBackgroundImage() != null) {
            String path = entry.getBackgroundImage().getPath();
            if (!blank(path) && !imageExists(path, EditorConfig.BACKGROUNDS_DIR, "textures/backgrounds/")) {
                issues.add(new Issue(Severity.WARNING, "MISSING_BACKGROUND", id, path, TAB_STAGING));
            }
        }
        if (entry.getPortraits() != null) {
            for (PortraitInfo portrait : entry.getPortraits()) {
                String path = portrait == null ? null : portrait.getPath();
                if (!blank(path) && !imageExists(path, EditorConfig.PORTRAITS_DIR, "textures/portraits/")) {
                    issues.add(new Issue(Severity.WARNING, "MISSING_PORTRAIT", id, path, TAB_STAGING));
                }
            }
        }
        if (!blank(entry.getAudioPath()) && !fileExists(EditorConfig.SOUNDS_DIR, entry.getAudioPath())) {
            issues.add(new Issue(Severity.WARNING, "MISSING_AUDIO", id, entry.getAudioPath(), TAB_STAGING));
        }
    }

    private static boolean imageExists(String path, Path directory, String builtinPrefix) {
        return AssetService.builtin(builtinPrefix + path) != null || fileExists(directory, path);
    }

    private static boolean fileExists(Path directory, String path) {
        Path file = EditorConfig.resolveInside(directory, path);
        return file != null && Files.isRegularFile(file);
    }

    private static void checkItems(DialogEntry entry, List<Issue> issues) {
        if (entry.getDisplayItems() == null) {
            return;
        }
        for (DisplayItemInfo item : entry.getDisplayItems()) {
            if (item == null) {
                continue;
            }
            String itemId = item.getItemId() == null ? "" : item.getItemId().trim();
            ResourceLocation key = ResourceLocation.tryParse(itemId);
            if (itemId.isEmpty() || key == null || !BuiltInRegistries.ITEM.containsKey(key)) {
                issues.add(new Issue(Severity.WARNING, "UNKNOWN_ITEM", entry.getId(), itemId, TAB_LOGIC));
            }
            if (!blank(item.getNbt())) {
                try {
                    TagParser.parseTag(item.getNbt());
                } catch (Exception e) {
                    issues.add(new Issue(Severity.WARNING, "BAD_ITEM_NBT", entry.getId(), itemId, TAB_LOGIC));
                }
            }
        }
    }
}
