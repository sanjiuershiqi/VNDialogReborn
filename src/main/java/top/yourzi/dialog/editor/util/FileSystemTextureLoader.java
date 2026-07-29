package top.yourzi.dialog.editor.util;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import top.yourzi.dialog.Dialog;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
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
            NativeImage image = decodeToNativeImage(fis);
            int width = image.getWidth();
            int height = image.getHeight();
            DynamicTexture dynamicTexture = new DynamicTexture(image);
            ResourceLocation rl = ResourceLocation.fromNamespaceAndPath(namespace,
                    prefix + "/" + UUID.randomUUID().toString().toLowerCase(Locale.ROOT));
            Minecraft.getInstance().getTextureManager().register(rl, dynamicTexture);
            return new LoadedTexture(rl, width, height);
        } catch (Exception e) {
            Dialog.LOGGER.error("Failed to load texture from file: {}", file, e);
            return null;
        }
    }

    /**
     * 解码输入流为 NativeImage。支持 PNG / JPG / JPEG / BMP / GIF 等格式，
     * 解决"扩展名为 .png 但实际是 JPG"导致 NativeImage.read 抛 Bad PNG Signature 的问题。
     * 真正的 PNG 直接走 NativeImage.read 高效路径；其它格式用 ImageIO 解码后转 PNG 再读。
     */
    public static NativeImage decodeToNativeImage(InputStream stream) throws IOException {
        byte[] bytes = stream.readAllBytes();
        // PNG 签名: 89 50 4E 47 0D 0A 1A 0A
        if (bytes.length >= 8
                && (bytes[0] & 0xFF) == 0x89
                && bytes[1] == (byte) 'P'
                && bytes[2] == (byte) 'N'
                && bytes[3] == (byte) 'G') {
            return NativeImage.read(new ByteArrayInputStream(bytes));
        }
        // 非 PNG：用 ImageIO 解码（支持 JPG/BMP/GIF 等），再重新编码为 PNG 交给 NativeImage
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
        if (img == null) {
            throw new IOException("Unsupported or unrecognized image format");
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        if (!ImageIO.write(img, "png", baos)) {
            throw new IOException("Failed to re-encode image to PNG");
        }
        img.flush();
        return NativeImage.read(new ByteArrayInputStream(baos.toByteArray()));
    }
}

