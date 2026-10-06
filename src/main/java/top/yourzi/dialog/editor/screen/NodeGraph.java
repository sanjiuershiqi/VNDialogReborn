package top.yourzi.dialog.editor.screen;

import com.google.gson.JsonPrimitive;
import top.yourzi.dialog.model.DialogEntry;
import top.yourzi.dialog.model.DialogOption;
import top.yourzi.dialog.model.DialogSequence;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Structural operations on one dialogue sequence: reading the graph, and the mutations that keep
 * every reference consistent.
 *
 * <p>A dialogue is an ordered array where each node points on with {@code next} and optional
 * branches. Everything that renames, deletes, reorders or connects nodes has to update the order,
 * the explicit references and the start marker together, so those edits live here rather than being
 * spread across the panels that trigger them.
 */
public final class NodeGraph {
    private NodeGraph() {
    }

    public static List<DialogEntry> entries(DialogSequence sequence) {
        List<DialogEntry> result = new ArrayList<>();
        if (sequence != null && sequence.getEntries() != null) {
            for (DialogEntry entry : sequence.getEntries()) {
                if (entry != null) {
                    result.add(entry);
                }
            }
        }
        return result;
    }

    public static int indexOf(DialogSequence sequence, DialogEntry entry) {
        return entries(sequence).indexOf(entry);
    }

    public static DialogEntry byId(DialogSequence sequence, String id) {
        return sequence == null ? null : sequence.findEntryById(id);
    }

    /**
     * The node the runtime advances to from {@code entry}: an explicit {@code next} when present,
     * otherwise the following array position.
     */
    public static DialogEntry implicitNext(DialogSequence sequence, DialogEntry entry) {
        if (sequence == null || entry == null) {
            return null;
        }
        if (entry.getNextId() != null && !entry.getNextId().isBlank()) {
            return byId(sequence, entry.getNextId());
        }
        List<DialogEntry> list = entries(sequence);
        int index = list.indexOf(entry);
        return index >= 0 && index < list.size() - 1 ? list.get(index + 1) : null;
    }

    public static boolean hasOptions(DialogEntry entry) {
        return entry != null && entry.getOptions() != null && entry.getOptions().length > 0;
    }

    /** Entries that can never be reached from the start node. */
    public static Set<String> unreachable(DialogSequence sequence) {
        Set<String> reachable = new HashSet<>();
        Set<String> all = new HashSet<>();
        List<DialogEntry> list = entries(sequence);
        for (DialogEntry entry : list) {
            if (entry != null && entry.getId() != null) {
                all.add(entry.getId());
            }
        }
        DialogEntry start = sequence == null ? null : sequence.getFirstEntry();
        if (start == null || start.getId() == null) {
            return all;
        }
        ArrayDeque<String> queue = new ArrayDeque<>();
        queue.add(start.getId());
        reachable.add(start.getId());
        while (!queue.isEmpty()) {
            DialogEntry entry = byId(sequence, queue.poll());
            if (entry == null) {
                continue;
            }
            List<String> targets = new ArrayList<>();
            if (entry.getNextId() != null && !entry.getNextId().isBlank()) {
                targets.add(entry.getNextId());
            }
            if (entry.getOptions() != null) {
                for (DialogOption option : entry.getOptions()) {
                    if (option != null && option.getTargetId() != null && !option.getTargetId().isBlank()) {
                        targets.add(option.getTargetId());
                    }
                }
            }
            for (String target : targets) {
                if (all.contains(target) && reachable.add(target)) {
                    queue.add(target);
                }
            }
        }
        all.removeAll(reachable);
        return all;
    }

    /** How many {@code next} / option references point at each node. */
    public static Map<String, Integer> referenceCounts(DialogSequence sequence) {
        Map<String, Integer> counts = new HashMap<>();
        for (DialogEntry entry : entries(sequence)) {
            if (entry == null) {
                continue;
            }
            String next = entry.getNextId();
            if (next != null && !next.isBlank()) {
                counts.merge(next, 1, Integer::sum);
            }
            if (entry.getOptions() == null) {
                continue;
            }
            for (DialogOption option : entry.getOptions()) {
                if (option != null && option.getTargetId() != null && !option.getTargetId().isBlank()) {
                    counts.merge(option.getTargetId(), 1, Integer::sum);
                }
            }
        }
        return counts;
    }

    public static String uniqueId(DialogSequence sequence, String base) {
        String root = base == null || base.isBlank() ? "node" : base;
        if (!root.matches("[A-Za-z0-9_\\-]+")) {
            root = root.replaceAll("[^A-Za-z0-9_\\-]", "_");
        }
        if (byId(sequence, root) == null) {
            return root;
        }
        String candidate = root + "_copy";
        if (byId(sequence, candidate) == null) {
            return candidate;
        }
        int n = 2;
        while (byId(sequence, candidate + n) != null) {
            n++;
        }
        return candidate + n;
    }

    public static DialogEntry create(String id) {
        return DialogEntry.builder().id(id).text(new JsonPrimitive("")).build();
    }

    private static void setEntries(DialogSequence sequence, List<DialogEntry> list) {
        sequence.setEntries(list.toArray(new DialogEntry[0]));
    }

    /** Appends a node and returns it. */
    public static DialogEntry add(DialogSequence sequence, DialogEntry entry) {
        List<DialogEntry> list = new ArrayList<>(entries(sequence));
        list.add(entry);
        setEntries(sequence, list);
        return entry;
    }

    /** Inserts a node at a position without touching any reference. */
    public static DialogEntry insertAt(DialogSequence sequence, int index, DialogEntry entry) {
        List<DialogEntry> list = new ArrayList<>(entries(sequence));
        list.add(Math.max(0, Math.min(index, list.size())), entry);
        setEntries(sequence, list);
        return entry;
    }

    /**
     * Inserts a node directly after {@code anchor} so that it runs right after it.
     *
     * <p>When the anchor falls through by file order, placing the node after it is enough. When the
     * anchor jumps explicitly, the new node takes over that jump and the anchor points at the new
     * node, so the existing chain stays intact either way.
     */
    public static DialogEntry insertAfter(DialogSequence sequence, DialogEntry anchor, DialogEntry entry) {
        int index = indexOf(sequence, anchor);
        insertAt(sequence, index < 0 ? entries(sequence).size() : index + 1, entry);
        if (anchor != null && !hasOptions(anchor) && !anchor.isEndDialog()
                && anchor.getNextId() != null && !anchor.getNextId().isBlank()) {
            entry.setNextId(anchor.getNextId());
            anchor.setNextId(entry.getId());
        }
        return entry;
    }

    /** Moves a node one slot; the array order is the implicit flow, so this changes the script. */
    public static boolean move(DialogSequence sequence, DialogEntry entry, int delta) {
        List<DialogEntry> list = new ArrayList<>(entries(sequence));
        int index = list.indexOf(entry);
        int target = index + delta;
        if (index < 0 || target < 0 || target >= list.size()) {
            return false;
        }
        list.remove(index);
        list.add(target, entry);
        setEntries(sequence, list);
        return true;
    }

    /**
     * Removes a node and clears every reference that pointed at it.
     *
     * <p>References are cleared rather than rewritten: an option that lost its target still shows as
     * a choice that ends the dialogue, which the writer can see and fix, whereas silently deleting
     * the option would lose authored text.
     */
    public static void remove(DialogSequence sequence, DialogEntry entry) {
        if (entry == null) {
            return;
        }
        List<DialogEntry> list = new ArrayList<>(entries(sequence));
        list.remove(entry);
        setEntries(sequence, list);
        clearReferences(sequence, entry.getId());
        if (entry.getId() != null && entry.getId().equals(sequence.getStartId())) {
            sequence.setStartId(null);
        }
    }

    public static void clearReferences(DialogSequence sequence, String id) {
        if (id == null) {
            return;
        }
        for (DialogEntry candidate : entries(sequence)) {
            if (candidate == null) {
                continue;
            }
            if (id.equals(candidate.getNextId())) {
                candidate.setNextId(null);
            }
            if (candidate.getOptions() == null) {
                continue;
            }
            for (DialogOption option : candidate.getOptions()) {
                if (option != null && id.equals(option.getTargetId())) {
                    option.setTargetId(null);
                }
            }
        }
    }

    /** Renames a node and repoints every reference; returns false when the target id is taken. */
    public static boolean rename(DialogSequence sequence, DialogEntry entry, String newId) {
        if (entry == null || newId == null || newId.isBlank() || newId.equals(entry.getId())) {
            return false;
        }
        if (byId(sequence, newId) != null) {
            return false;
        }
        String oldId = entry.getId();
        entry.setId(newId);
        for (DialogEntry candidate : entries(sequence)) {
            if (candidate == null) {
                continue;
            }
            if (oldId != null && oldId.equals(candidate.getNextId())) {
                candidate.setNextId(newId);
            }
            if (candidate.getOptions() == null) {
                continue;
            }
            for (DialogOption option : candidate.getOptions()) {
                if (option != null && oldId != null && oldId.equals(option.getTargetId())) {
                    option.setTargetId(newId);
                }
            }
        }
        if (oldId != null && oldId.equals(sequence.getStartId())) {
            sequence.setStartId(newId);
        }
        return true;
    }

    /** Duplicates a node under a fresh id right after the original; references are left alone. */
    public static DialogEntry duplicate(DialogSequence sequence, DialogEntry entry) {
        DialogEntry copy = entry.deepCopy();
        copy.setId(uniqueId(sequence, entry.getId()));
        insertAt(sequence, indexOf(sequence, entry) + 1, copy);
        return copy;
    }

    /** Pastes a copied node after {@code anchor} (or at the end) under a fresh id. */
    public static DialogEntry paste(DialogSequence sequence, DialogEntry anchor, DialogEntry template) {
        DialogEntry copy = template.deepCopy();
        copy.setId(uniqueId(sequence, template.getId()));
        int index = anchor == null ? entries(sequence).size() : indexOf(sequence, anchor) + 1;
        insertAt(sequence, index, copy);
        return copy;
    }

    public static List<DialogEntry> search(DialogSequence sequence, String query) {
        List<DialogEntry> result = new ArrayList<>();
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        for (DialogEntry entry : entries(sequence)) {
            if (entry == null) {
                continue;
            }
            if (needle.isEmpty() || matches(entry, needle)) {
                result.add(entry);
            }
        }
        return result;
    }

    private static boolean matches(DialogEntry entry, String needle) {
        for (String field : top.yourzi.dialog.editor.TextCodec.searchFields(entry)) {
            if (field.toLowerCase(Locale.ROOT).contains(needle)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isStart(DialogSequence sequence, DialogEntry entry) {
        return sequence != null && entry != null && entry.getId() != null && entry.getId().equals(sequence.getStartId());
    }
}
