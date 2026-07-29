package top.yourzi.dialog.editor.util;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import top.yourzi.dialog.Dialog;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
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
     * 解码输入流为 NativeImage，支持 PNG / JPG / JPEG / BMP / GIF / PSD / TGA / HDR / PIC / PNM 等格式。
     * - 真正的 PNG 优先走 NativeImage.read 高效路径；若失败（如 16-bit PNG、不支持的色深/颜色类型）自动回退到 STBImage。
     * - 其它格式用 LWJGL STBImage 解码（MC 运行时自带）。
     * - 注意：WebP 不被 NativeImage / STBImage / JDK ImageIO 支持，需用户自行转换为 PNG/JPG。
     */
    public static NativeImage decodeToNativeImage(InputStream stream) throws IOException {
        byte[] bytes = stream.readAllBytes();
        // 先做格式识别，对 MC 工具链无法解码的常见格式给出清晰可操作的提示，
        // 避免用户看到一头雾水的 "STBImage failed to decode: Image not of any known type"。
        String unsupported = detectUnsupportedFormat(bytes);
        if (unsupported != null) {
            Dialog.LOGGER.warn("decodeToNativeImage: 不支持的图片格式 ({} bytes): {}", bytes.length, unsupported);
            throw new IOException(unsupported);
        }
        // 完整 PNG 签名: 89 50 4E 47 0D 0A 1A 0A
        boolean isPng = bytes.length >= 8
                && (bytes[0] & 0xFF) == 0x89
                && bytes[1] == (byte) 'P'
                && bytes[2] == (byte) 'N'
                && bytes[3] == (byte) 'G'
                && (bytes[4] & 0xFF) == 0x0D
                && (bytes[5] & 0xFF) == 0x0A
                && (bytes[6] & 0xFF) == 0x1A
                && (bytes[7] & 0xFF) == 0x0A;
        if (isPng) {
            try {
                Dialog.LOGGER.info("decodeToNativeImage: real PNG ({} bytes), using NativeImage.read", bytes.length);
                return NativeImage.read(new ByteArrayInputStream(bytes));
            } catch (Exception e) {
                // NativeImage.read 在某些 PNG 变体（16-bit/灰度+alpha/调色板透明等）下会抛
                // "Unsupported or unrecognized image format"，回退到 STBImage 兜底
                Dialog.LOGGER.warn("decodeToNativeImage: NativeImage.read failed for PNG ({} bytes), falling back to STBImage: {}",
                        bytes.length, e.toString());
            }
        }
        // 非 PNG 或 NativeImage.read 失败：用 STBImage 解码（支持 JPG/BMP/GIF/PSD/TGA/HDR 等）
        Dialog.LOGGER.info("decodeToNativeImage: decoding via STBImage ({} bytes, isPng={}, header={} {} {} {})",
                bytes.length, isPng,
                String.format("%02X", bytes[0] & 0xFF),
                String.format("%02X", bytes[1] & 0xFF),
                String.format("%02X", bytes[2] & 0xFF),
                String.format("%02X", bytes[3] & 0xFF));
        return decodeWithSTB(bytes);
    }

    /**
     * 识别 MC 工具链（NativeImage / LWJGL STBImage / JDK ImageIO）均不支持的图片格式，
     * 返回清晰可操作的错误信息；若非已知不支持的格式则返回 null（交由后续解码器尝试）。
     * 重点处理"扩展名是 .png 但实际是 WebP"这类用户常见误用。
     */
    private static String detectUnsupportedFormat(byte[] bytes) {
        if (bytes.length < 12) {
            return null;
        }
        // WebP: "RIFF" (52 49 46 46) + 4 字节大小 + "WEBP" (57 45 42 50)
        if (bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return "该文件实际是 WebP 格式（仅改了 .png 扩展名）。WebP 不被 Minecraft 支持，"
                    + "请用看图软件将其另存/转换为 PNG 或 JPG 后再使用。";
        }
        // AVIF: "....ftyp" + "avif"/"avis" —— 同样不被支持
        if (bytes[4] == 'f' && bytes[5] == 't' && bytes[6] == 'y' && bytes[7] == 'p'
                && ((bytes[8] == 'a' && bytes[9] == 'v' && bytes[10] == 'i')
                        || (bytes[8] == 'a' && bytes[9] == 'v' && bytes[10] == 'i' && bytes[11] == 's'))) {
            return "该文件实际是 AVIF 格式。AVIF 不被 Minecraft 支持，请转换为 PNG 或 JPG 后再使用。";
        }
        // HEIC: "....ftyp" + "heic"/"heix"/"mif1" —— 同样不被支持
        if (bytes[4] == 'f' && bytes[5] == 't' && bytes[6] == 'y' && bytes[7] == 'p'
                && bytes[8] == 'h' && bytes[9] == 'e' && bytes[10] == 'i' && (bytes[11] == 'c' || bytes[11] == 'x')) {
            return "该文件实际是 HEIC 格式。HEIC 不被 Minecraft 支持，请转换为 PNG 或 JPG 后再使用。";
        }
        return null;
    }

    /**
     * 用 LWJGL STBImage 解码图片字节为 NativeImage。强制 4 通道 RGBA。
     * STBImage 支持 JPG/PNG/BMP/PSD/TGA/GIF/PIC/PNM/HDR 等格式（不支持 WebP/AVIF/HEIC）。
     */
    private static NativeImage decodeWithSTB(byte[] bytes) throws IOException {
        ByteBuffer input = MemoryUtil.memAlloc(bytes.length);
        ByteBuffer output = null;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            input.put(bytes);
            input.flip();
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            IntBuffer channels = stack.mallocInt(1);
            // STBI_rgb_alpha = 4，强制输出 RGBA
            output = STBImage.stbi_load_from_memory(input, w, h, channels, STBImage.STBI_rgb_alpha);
            if (output == null) {
                String reason = STBImage.stbi_failure_reason();
                throw new IOException("STBImage failed to decode: " + (reason != null ? reason : "unknown"));
            }
            int width = w.get(0);
            int height = h.get(0);
            Dialog.LOGGER.info("decodeToNativeImage: STBImage decoded {}x{} ({} channels), building NativeImage",
                    width, height, channels.get(0));
            // 将 STB 解码的 RGBA 像素拷贝到 NativeImage
            // STB 输出 RGBA 字节序；NativeImage.setPixelRGBA 期望 ABGR (little-endian RGBA)，二者内存一致
            NativeImage image = new NativeImage(width, height, false);
            int pixelCount = width * height;
            for (int i = 0; i < pixelCount; i++) {
                int r = output.get() & 0xFF;
                int g = output.get() & 0xFF;
                int b = output.get() & 0xFF;
                int a = output.get() & 0xFF;
                int argb = (a << 24) | (b << 16) | (g << 8) | r;
                image.setPixelRGBA(i % width, i / width, argb);
            }
            return image;
        } finally {
            if (output != null) {
                STBImage.stbi_image_free(output);
            }
            MemoryUtil.memFree(input);
        }
    }
}

