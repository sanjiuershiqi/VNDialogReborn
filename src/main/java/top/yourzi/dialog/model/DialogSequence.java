package top.yourzi.dialog.model;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

public class DialogSequence {
    private String id;
    private String title;
    private String description;
    private String effect;
    private DialogEntry[] entries;
    @SerializedName("start")
    private String startId;
    @SerializedName("allowClose")
    private Boolean allowClose;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getEffect() {
        return effect;
    }

    public void setEffect(String effect) {
        this.effect = effect;
    }

    public DialogEntry[] getEntries() {
        return entries;
    }

    public void setEntries(DialogEntry[] entries) {
        this.entries = entries;
    }

    public String getStartId() {
        return startId;
    }

    public void setStartId(String startId) {
        this.startId = startId;
    }

    public Boolean getAllowClose() {
        return allowClose;
    }

    public void setAllowClose(Boolean allowClose) {
        this.allowClose = allowClose;
    }

    public boolean isCloseAllowed() {
        return allowClose != null && allowClose;
    }

    public DialogEntry getFirstEntry() {
        if (entries == null || entries.length == 0) {
            return null;
        }

        if (startId != null && !startId.isEmpty()) {
            for (DialogEntry entry : entries) {
                if (entry != null && startId.equals(entry.getId())) {
                    return entry;
                }
            }
        }

        return entries[0];
    }

    public DialogEntry findEntryById(String id) {
        if (id == null || id.isEmpty() || entries == null) {
            return null;
        }

        for (DialogEntry entry : entries) {
            if (entry != null && id.equals(entry.getId())) {
                return entry;
            }
        }

        return null;
    }

    public DialogEntry getNextEntry(DialogEntry currentEntry) {
        if (currentEntry == null || entries == null || entries.length == 0) {
            return null;
        }

        if (currentEntry.getNextId() != null && !currentEntry.getNextId().isEmpty()) {
            return findEntryById(currentEntry.getNextId());
        }

        for (int i = 0; i < entries.length - 1; i++) {
            if (entries[i] == currentEntry) {
                return entries[i + 1];
            }
        }

        return null;
    }

    public List<DialogEntry> getRemainingEntries(DialogEntry currentEntry) {
        List<DialogEntry> remainingEntries = new ArrayList<>();
        if (currentEntry == null || entries == null || entries.length == 0) {
            return remainingEntries;
        }

        DialogEntry nextEntry = getNextEntry(currentEntry);
        while (nextEntry != null) {
            remainingEntries.add(nextEntry);
            nextEntry = getNextEntry(nextEntry);
        }

        return remainingEntries;
    }
}
