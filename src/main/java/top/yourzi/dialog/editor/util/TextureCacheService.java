package top.yourzi.dialog.editor.util;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import top.yourzi.dialog.Dialog;

import java.io.File;
import java.io.FileInputStream;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 文件纹理缓存服务（静态 LRU）。
 * 统一原 AppearancePropertyPage 与 PortraitListScreen 各自维护的重复缓存，
 * 集中加载/缓存/尺寸恢复/释放逻辑，避免两份几乎相同的代码。
 *
 * 缓存 key：文件绝对路径小写 + "|" + 最后修改时间，再替换非法字符为 "_"，
 * 保证中文文件名等不会冲突。LRU 上限 MAX_CACHE_SIZE，超出时释放最旧纹理。
 */
public final class TextureCacheService {

    private static final int MAX_CACHE_SIZE = 30;

    private static final Map<String, ResourceLocation> textureCache = new LinkedHashMap<String, ResourceLocation>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, ResourceLocation> eldest) {
            if (this.size() > MAX_CACHE_SIZE) {
                Minecraft.getInstance().getTextureManager().release(eldest.getValue());
                sizeCache.remove(eldest.getKey());
                return true;
            }
            return false;
        }
    };
    private static final Map<String, int[]> sizeCache = new LinkedHashMap<>();

    private TextureCacheService() {
    }

    /** 缓存命中的纹理 + 尺寸；未命中或加载失败返回 null。 */
    public static record CachedTexture(ResourceLocation location, int width, int height) {
    }

    /**
     * 计算文件的稳定缓存 key（路径小写 + 修改时间 + 非法字符替换）。
     * 文件不存在时仍返回基于绝对路径的 key（调用方负责存在性检查）。
     */
    public static String cacheKey(File file) {
        String stable = file.getAbsolutePath().toLowerCase(Locale.ROOT) + "|" + file.lastModified();
        return stable.replaceAll("[^a-z0-9/._-]", "_");
    }

    /**
     * 加载文件纹理并缓存，返回 ResourceLocation + 宽高；命中缓存直接返回。
     * 文件不存在时尝试同目录大小写不敏感匹配（兼容 Windows/资源包大小写差异）。
     *
     * @return 命中或加载成功返回 CachedTexture；失败返回 null
     */
    public static CachedTexture load(File file) {
        String safeKey = cacheKey(file);
        if (textureCache.containsKey(safeKey)) {
            int[] size = sizeCache.get(safeKey);
            if (size != null) {
                return new CachedTexture(textureCache.get(safeKey), size[0], size[1]);
            }
            return new CachedTexture(textureCache.get(safeKey), 0, 0);
        }
        // 文件不存在时尝试同目录大小写不敏感匹配
        if (!file.exists()) {
            File parent = file.getParentFile();
            if (parent != null && parent.isDirectory()) {
                File finalFile = file;
                File[] matches = parent.listFiles((dir, name) -> name.equalsIgnoreCase(finalFile.getName()));
                if (matches != null && matches.length > 0) {
                    file = matches[0];
                } else {
                    Dialog.LOGGER.warn("Texture file not found: {}", file.getAbsolutePath());
                    return null;
                }
            } else {
                Dialog.LOGGER.warn("Texture file not found: {}", file.getAbsolutePath());
                return null;
            }
        }
        try (FileInputStream fis = new FileInputStream(file)) {
            NativeImage image = FileSystemTextureLoader.decodeToNativeImage(fis);
            try {
                int w = image.getWidth();
                int h = image.getHeight();
                ResourceLocation rl = ResourceLocation.fromNamespaceAndPath(Dialog.MODID, "editor_preview/" + safeKey);
                DynamicTexture dynamicTexture = new DynamicTexture(image);
                try {
                    dynamicTexture.upload();
                    Minecraft.getInstance().getTextureManager().register(rl, dynamicTexture);
                } catch (Exception registerEx) {
                    dynamicTexture.close();
                    throw registerEx;
                }
                textureCache.put(safeKey, rl);
                sizeCache.put(safeKey, new int[]{w, h});
                return new CachedTexture(rl, w, h);
            } catch (Exception ex) {
                try {
                    image.close();
                } catch (Exception closeEx) {
                    Dialog.LOGGER.warn("Failed to close NativeImage: {}", closeEx.toString());
                }
                throw ex;
            }
        } catch (Exception e) {
            Dialog.LOGGER.error("Failed to load preview texture: {}", file, e);
            return null;
        }
    }

    /**
     * 仅查询已缓存纹理的尺寸（不加载），用于外部在缓存命中时恢复宽高字段。
     * 未缓存返回 null。
     */
    public static int[] getSize(String safeKey) {
        return sizeCache.get(safeKey);
    }

    /** 释放所有缓存纹理。编辑器关闭时调用。 */
    public static void releaseAll() {
        for (ResourceLocation rl : textureCache.values()) {
            Minecraft.getInstance().getTextureManager().release(rl);
        }
        textureCache.clear();
        sizeCache.clear();
    }
}
