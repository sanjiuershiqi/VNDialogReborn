package top.yourzi.dialog.model;

import com.google.gson.annotations.SerializedName;

/**
 * 撠???曄??楝敺葡?★??餌掩?? */
public class BackgroundImageInfo {
    private String path; // ?曄?頝臬?
    private BackgroundRenderOption renderOption; // 皜脫??★
    
    @SerializedName("animation_type")
    private BackgroundAnimationType animationType = BackgroundAnimationType.NONE; // ?函蝐餃?

    public BackgroundImageInfo(String path, BackgroundRenderOption renderOption) {
        this.path = path;
        this.renderOption = renderOption;
        this.animationType = BackgroundAnimationType.NONE;
    }
    
    public BackgroundImageInfo(String path, BackgroundRenderOption renderOption, BackgroundAnimationType animationType) {
        this.path = path;
        this.renderOption = renderOption;
        this.animationType = animationType != null ? animationType : BackgroundAnimationType.NONE;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public BackgroundRenderOption getRenderOption() {
        return renderOption;
    }

    public void setRenderOption(BackgroundRenderOption renderOption) {
        this.renderOption = renderOption;
    }
    
    public BackgroundAnimationType getAnimationType() {
        return animationType;
    }
    
    public void setAnimationType(BackgroundAnimationType animationType) {
        this.animationType = animationType != null ? animationType : BackgroundAnimationType.NONE;
    }
}