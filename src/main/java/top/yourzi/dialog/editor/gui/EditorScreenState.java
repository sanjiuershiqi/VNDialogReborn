package top.yourzi.dialog.editor.gui;

import java.util.HashMap;
import java.util.Map;

/**
 * 编辑器跨屏 UI 状态单例（借鉴 Sparkle-Morpher 的 ModelPanelState）。
 *
 * 集中管理子屏切换时容易丢失的少量 UI 状态（活动属性标签、选中节点、树滚动等），
 * 让 Screen 重建后能自动恢复，替代原先 recoverXxxTab → editor.setPropertyPanelTab 的 hack。
 *
 * 范围克制：只收编明确会丢失的少量状态，不一次性集中所有 UI 状态，按需扩展。
 * 编辑器整体关闭时调用 reset() 清空，下次打开为初始状态。
 */
public final class EditorScreenState {

    private static final EditorScreenState INSTANCE = new EditorScreenState();

    /** 当前活动属性页标签索引（0=文本 1=外观 2=逻辑）。 */
    private int activePropertyTab = 0;
    /** 当前选中的对话节点 ID（树面板重建后恢复选中）。 */
    private String selectedNodeId = null;
    /** 树面板滚动偏移（重建后恢复滚动位置）。 */
    private int treeScrollOffset = 0;
    /** 对话树搜索文本（重建后回填，借鉴 Sparkle ModelPanelState）。 */
    private String treeSearchText = "";
    /** 主编辑器当前视图：false=大纲（树列表） true=节点画布。 */
    private boolean canvasMode = false;
    /**
     * 画布节点坐标缓存：序列 ID → (节点 ID → [x, y] 世界坐标)。
     * 节点位置不属于 JSON 数据模型，仅作为编辑器会话状态保存在内存中；
     * 切换序列/重建控件后恢复，编辑器关闭即丢弃（reset 清空）。
     */
    private final Map<String, Map<String, int[]>> canvasLayouts = new HashMap<>();

    private EditorScreenState() {
    }

    public static EditorScreenState get() {
        return INSTANCE;
    }

    public int getActivePropertyTab() {
        return activePropertyTab;
    }

    public void setActivePropertyTab(int index) {
        this.activePropertyTab = index;
    }

    public String getSelectedNodeId() {
        return selectedNodeId;
    }

    public void setSelectedNodeId(String id) {
        this.selectedNodeId = id;
    }

    public int getTreeScrollOffset() {
        return treeScrollOffset;
    }

    public void setTreeScrollOffset(int offset) {
        this.treeScrollOffset = offset;
    }

    public String getTreeSearchText() {
        return treeSearchText;
    }

    public void setTreeSearchText(String text) {
        this.treeSearchText = text == null ? "" : text;
    }

    public boolean isCanvasMode() {
        return canvasMode;
    }

    public void setCanvasMode(boolean canvasMode) {
        this.canvasMode = canvasMode;
    }

    /** 获取指定序列的画布布局（无则返回空 Map，不创建）。 */
    public Map<String, int[]> getCanvasLayout(String sequenceId) {
        return canvasLayouts.getOrDefault(sequenceId, new HashMap<>());
    }

    /** 覆盖指定序列的画布布局（整体替换，由画布在节点移动后写回）。 */
    public void setCanvasLayout(String sequenceId, Map<String, int[]> layout) {
        if (sequenceId != null) {
            canvasLayouts.put(sequenceId, new HashMap<>(layout));
        }
    }

    /** 编辑器整体关闭时清空所有状态，下次打开为初始状态。 */
    public void reset() {
        this.activePropertyTab = 0;
        this.selectedNodeId = null;
        this.treeScrollOffset = 0;
        this.treeSearchText = "";
        this.canvasMode = false;
        this.canvasLayouts.clear();
    }
}
