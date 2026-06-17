package top.yourzi.dialog.model;

import com.google.gson.annotations.SerializedName;

public class DisplayItemInfo {
    @SerializedName("item")
    private String itemId;
    @SerializedName("count")
    private int count = 1;
    @SerializedName("nbt")
    private String nbt;

    public DisplayItemInfo() {
    }

    public DisplayItemInfo(String itemId, int count, String nbt) {
        this.itemId = itemId;
        this.count = count;
        this.nbt = nbt;
    }

    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public String getNbt() {
        return nbt;
    }

    public void setNbt(String nbt) {
        this.nbt = nbt;
    }
}
