package top.yourzi.dialog.editor.util;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import top.yourzi.dialog.Dialog;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.UUID;

/**
 * 从文件系统加载纹理并注册到 Minecraft 纹理管理器。融合自 visual_mod_edit_vndialog。
 */
public class FileSystemTextureLoader {

    public record LoadedTexture(ResourceLocation location, int width, int height) {
    }

    public static LoadedTexture loadAndRegister(File file, String namespace, String prefix) {
        if (!file.exists()) {
            return null;
        }
        try (FileInputStream fis = new FileInputStream(file)) {
            NativeImage image = NativeImage.read(fis);
            int width = image.getWidth();
            int height = image.getHeight();
            DynamicTexture dynamicTexture = new DynamicTexture(image);
            ResourceLocation rl = ResourceLocation.fromNamespaceAndPath(namespace,
                    prefix + "/" + UUID.randomUUID().toString().toLowerCase(Locale.ROOT));
            Minecraft.getInstance().getTextureManager().register(rl, dynamicTexture);
            return new LoadedTexture(rl, width, height);
        } catch (IOException e) {
            Dialog.LOGGER.error("Failed to load texture from file: {}", file, e);
            return null;
        }
    }
}
