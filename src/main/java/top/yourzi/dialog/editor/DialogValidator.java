package top.yourzi.dialog.editor;

import top.yourzi.dialog.model.DialogEntry;
import top.yourzi.dialog.model.DialogOption;
import top.yourzi.dialog.model.DialogSequence;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Structural diagnostics for one dialogue sequence.
 *
 * <p>Errors are things the runtime cannot follow: a missing target, a duplicate id, a start node that
 * does not exist. Warnings are legal but suspicious: text that never runs because nothing reaches
 * it, a branch that silently ends the conversation. Both are reported per node so the editor can
 * jump straight to the line that needs attention.
 */
public final class DialogValidator {
    public enum Severity {
        ERROR,
        WARNING
    }

    public record Issue(Severity severity, String code, String nodeId, String message) {
    }

    private DialogValidator() {
    }

    public static List<Issue> validate(DialogSequence sequence) {
        List<Issue> issues = new ArrayList<>();
        if (sequence == null) {
            issues.add(new Issue(Severity.ERROR, "NULL_SEQUENCE", null, "No dialogue is loaded"));
            return issues;
        }
        DialogEntry[] array = sequence.getEntries();
        if (array == null || array.length == 0) {
            issues.add(new Issue(Severity.ERROR, "EMPTY_ENTRIES", null, "The dialogue has no nodes"));
            return issues;
        }

        Map<String, DialogEntry> byId = new HashMap<>();
        for (DialogEntry entry : array) {
            if (entry == null || entry.getId() == null || entry.getId().isBlank()) {
                issues.add(new Issue(Severity.ERROR, "INVALID_ID", null, "A node has an empty id"));
                continue;
            }
            if (byId.putIfAbsent(entry.getId(), entry) != null) {
                issues.add(new Issue(Severity.ERROR, "DUPLICATE_ID", entry.getId(),
                        "Duplicate node id: " + entry.getId()));
            }
        }

        if (sequence.getStartId() != null && !sequence.getStartId().isBlank()
                && sequence.findEntryById(sequence.getStartId()) == null) {
            issues.add(new Issue(Severity.ERROR, "INVALID_START", sequence.getStartId(),
                    "Start node does not exist: " + sequence.getStartId()));
        }

        // Every node is checked, reachable or not: an unreferenced node with a broken target would
        // otherwise stay silent until it is linked back in.
        for (DialogEntry entry : array) {
            if (entry == null || entry.getId() == null) {
                continue;
            }
            if (entry.getNextId() != null && !entry.getNextId().isBlank() && !byId.containsKey(entry.getNextId())) {
                issues.add(new Issue(Severity.ERROR, "DANGLING_NEXT", entry.getId(),
                        "next points at a missing node: " + entry.getNextId()));
            }
            if (entry.getOptions() == null) {
                continue;
            }
            for (DialogOption option : entry.getOptions()) {
                if (option != null && option.getTargetId() != null && !option.getTargetId().isBlank()
                        && !byId.containsKey(option.getTargetId())) {
                    issues.add(new Issue(Severity.ERROR, "DANGLING_OPTION_TARGET", entry.getId(),
                            "A branch points at a missing node: " + option.getTargetId()));
                }
            }
        }

        Set<String> reachable = reachable(sequence, byId);
        for (DialogEntry entry : array) {
            if (entry == null || entry.getId() == null) {
                continue;
            }
            if (!reachable.contains(entry.getId())) {
                issues.add(new Issue(Severity.WARNING, "UNREACHABLE_NODE", entry.getId(),
                        "The node cannot be reached from the start node"));
            }
            if (entry.getText() == null || entry.getText().isJsonNull()) {
                issues.add(new Issue(Severity.WARNING, "EMPTY_TEXT", entry.getId(), "The node has no text"));
            }
            if (entry.getOptions() == null) {
                continue;
            }
            for (DialogOption option : entry.getOptions()) {
                if (option != null && (option.getTargetId() == null || option.getTargetId().isBlank())) {
                    issues.add(new Issue(Severity.WARNING, "OPTION_WITHOUT_TARGET", entry.getId(),
                            "A branch has no target and will end the dialogue"));
                }
            }
        }

        if (containsCycle(byId)) {
            issues.add(new Issue(Severity.WARNING, "CYCLE", null, "The dialogue graph contains a cycle"));
        }
        return issues;
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

    private static Set<String> reachable(DialogSequence sequence, Map<String, DialogEntry> byId) {
        Set<String> reachable = new HashSet<>();
        DialogEntry start = sequence.getFirstEntry();
        if (start == null || start.getId() == null) {
            return reachable;
        }
        ArrayDeque<String> queue = new ArrayDeque<>();
        queue.add(start.getId());
        reachable.add(start.getId());
        while (!queue.isEmpty()) {
            DialogEntry entry = byId.get(queue.poll());
            if (entry == null) {
                continue;
            }
            for (String target : targets(entry)) {
                if (byId.containsKey(target) && reachable.add(target)) {
                    queue.add(target);
                }
            }
        }
        return reachable;
    }

    private static List<String> targets(DialogEntry entry) {
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
        return targets;
    }

    private static boolean containsCycle(Map<String, DialogEntry> byId) {
        Set<String> visiting = new HashSet<>();
        Set<String> done = new HashSet<>();
        for (String id : byId.keySet()) {
            if (visit(id, byId, visiting, done)) {
                return true;
            }
        }
        return false;
    }

    private static boolean visit(String id, Map<String, DialogEntry> byId, Set<String> visiting, Set<String> done) {
        if (done.contains(id)) {
            return false;
        }
        if (!visiting.add(id)) {
            return true;
        }
        DialogEntry entry = byId.get(id);
        if (entry != null) {
            for (String target : targets(entry)) {
                if (byId.containsKey(target) && visit(target, byId, visiting, done)) {
                    return true;
                }
            }
        }
        visiting.remove(id);
        done.add(id);
        return false;
    }
}
