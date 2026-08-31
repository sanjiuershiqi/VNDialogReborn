package top.yourzi.dialog.editor.validation;

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
 * UI-independent validation for an editable dialog document.
 * The editor and future tooling can share the same graph diagnostics.
 */
public final class DialogValidator {
    public enum Severity { ERROR, WARNING }

    public record Issue(Severity severity, String code, String nodeId, String message) {
    }

    private DialogValidator() {
    }

    public static List<Issue> validate(DialogSequence sequence) {
        List<Issue> issues = new ArrayList<>();
        if (sequence == null) {
            issues.add(new Issue(Severity.ERROR, "NULL_SEQUENCE", null, "Dialog sequence is null"));
            return issues;
        }
        DialogEntry[] entries = sequence.getEntries();
        if (entries == null || entries.length == 0) {
            issues.add(new Issue(Severity.ERROR, "EMPTY_ENTRIES", null, "Dialog sequence has no entries"));
            return issues;
        }

        Map<String, DialogEntry> byId = new HashMap<>();
        for (DialogEntry entry : entries) {
            if (entry == null || entry.getId() == null || entry.getId().isBlank()) {
                issues.add(new Issue(Severity.ERROR, "INVALID_ID", null, "Entry has an empty ID"));
                continue;
            }
            if (byId.putIfAbsent(entry.getId(), entry) != null) {
                issues.add(new Issue(Severity.ERROR, "DUPLICATE_ID", entry.getId(), "Duplicate entry ID: " + entry.getId()));
            }
        }

        DialogEntry start = sequence.getFirstEntry();
        if (sequence.getStartId() != null && !sequence.getStartId().isBlank()
                && sequence.findEntryById(sequence.getStartId()) == null) {
            issues.add(new Issue(Severity.ERROR, "INVALID_START", sequence.getStartId(),
                    "Start node does not exist: " + sequence.getStartId()));
        }

        // Validate references for every node, including nodes currently unreachable
        // from the start node. Otherwise a broken orphan can remain silent forever.
        for (DialogEntry entry : entries) {
            if (entry == null || entry.getId() == null) {
                continue;
            }
            if (entry.getNextId() != null && !entry.getNextId().isBlank()
                    && !byId.containsKey(entry.getNextId())) {
                issues.add(new Issue(Severity.ERROR, "DANGLING_NEXT", entry.getId(),
                        "Reference points to missing node: " + entry.getNextId()));
            }
            if (entry.getOptions() != null) {
                for (DialogOption option : entry.getOptions()) {
                    if (option != null && option.getTargetId() != null && !option.getTargetId().isBlank()
                            && !byId.containsKey(option.getTargetId())) {
                        issues.add(new Issue(Severity.ERROR, "DANGLING_OPTION_TARGET", entry.getId(),
                                "Reference points to missing node: " + option.getTargetId()));
                    }
                }
            }
        }

        Set<String> reachable = new HashSet<>();
        if (start != null && start.getId() != null) {
            ArrayDeque<String> queue = new ArrayDeque<>();
            queue.add(start.getId());
            reachable.add(start.getId());
            while (!queue.isEmpty()) {
                DialogEntry entry = byId.get(queue.removeFirst());
                if (entry == null) {
                    continue;
                }
                List<String> targets = targets(entry);
                for (String target : targets) {
                    if (target == null || target.isBlank()) {
                        continue;
                    }
                    if (byId.containsKey(target) && reachable.add(target)) {
                        queue.add(target);
                    }
                }
            }
        }

        for (DialogEntry entry : entries) {
            if (entry == null || entry.getId() == null) {
                continue;
            }
            if (!reachable.contains(entry.getId())) {
                issues.add(new Issue(Severity.WARNING, "UNREACHABLE_NODE", entry.getId(),
                        "Node is not reachable from the start node"));
            }
            if (entry.getText() == null || entry.getText().isJsonNull()) {
                issues.add(new Issue(Severity.WARNING, "EMPTY_TEXT", entry.getId(),
                        "Node has no dialogue text"));
            }
            if (entry.getOptions() != null) {
                for (DialogOption option : entry.getOptions()) {
                    if (option != null && (option.getTargetId() == null || option.getTargetId().isBlank())) {
                        issues.add(new Issue(Severity.WARNING, "OPTION_WITHOUT_TARGET", entry.getId(),
                                "Option has no target and will end the dialog"));
                    }
                }
            }
        }

        if (containsCycle(sequence, byId)) {
            issues.add(new Issue(Severity.WARNING, "CYCLE", null,
                    "Dialog graph contains a cycle"));
        }
        return issues;
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

    private static boolean containsCycle(DialogSequence sequence, Map<String, DialogEntry> byId) {
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        for (String id : byId.keySet()) {
            if (detectCycle(id, byId, visiting, visited)) {
                return true;
            }
        }
        return false;
    }

    private static boolean detectCycle(String id, Map<String, DialogEntry> byId,
                                       Set<String> visiting, Set<String> visited) {
        if (visited.contains(id)) {
            return false;
        }
        if (!visiting.add(id)) {
            return true;
        }
        DialogEntry entry = byId.get(id);
        if (entry != null) {
            for (String target : targets(entry)) {
                if (byId.containsKey(target) && detectCycle(target, byId, visiting, visited)) {
                    return true;
                }
            }
        }
        visiting.remove(id);
        visited.add(id);
        return false;
    }
}
