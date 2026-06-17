package top.yourzi.dialog.model;

import com.google.gson.annotations.SerializedName;

public class PortraitInfo {
    @SerializedName("path")
    private String path;
    @SerializedName("position")
    private PortraitPosition position = PortraitPosition.RIGHT;
    @SerializedName("brightness")
    private float brightness = 1.0f;
    @SerializedName("animationType")
    private PortraitAnimationType animationType = PortraitAnimationType.NONE;
    @SerializedName("size")
    private float size = 1.0f;

    public PortraitInfo() {
    }

    public PortraitInfo(String path, PortraitPosition position, float brightness, PortraitAnimationType animationType) {
        this(path, position, brightness, animationType, 1.0f);
    }

    public PortraitInfo(String path, PortraitPosition position, float brightness, PortraitAnimationType animationType, float size) {
        this.path = path;
        this.position = position;
        this.brightness = brightness;
        this.animationType = animationType;
        this.size = Math.max(0.0f, Math.min(5.0f, size));
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public PortraitPosition getPosition() {
        return position;
    }

    public void setPosition(PortraitPosition position) {
        this.position = position;
    }

    public float getBrightness() {
        return brightness;
    }

    public void setBrightness(float brightness) {
        this.brightness = brightness;
    }

    public PortraitAnimationType getAnimationType() {
        return animationType;
    }

    public void setAnimationType(PortraitAnimationType animationType) {
        this.animationType = animationType;
    }

    public float getSize() {
        return size;
    }

    public void setSize(float size) {
        this.size = Math.max(0.0f, Math.min(5.0f, size));
    }
}
