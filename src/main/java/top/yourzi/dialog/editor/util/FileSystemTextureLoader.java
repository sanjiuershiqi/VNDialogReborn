package top.yourzi.dialog.editor.util;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import top.yourzi.dialog.Dialog;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.UUID;

/**
 * 从文件系统加载纹理并注册到 Minecraft 纹理管理器。融合自 visual_mod_edit_vndialog。
 *
 * 解码策略（纯 Java，无 native STB 依赖，避免 native 内存崩溃）：
 * - PNG：优先走 NativeImage.read（MC 内部高效路径，基于 stb 但由 MC 管理生命周期）。
 * - 非 PNG（JPG/BMP/GIF/TIFF）：用 JDK ImageIO 解码为 BufferedImage，再转换为 NativeImage。
 *   ImageIO 是纯 Java 实现，不会导致 native 崩溃。
 * - WebP/AVIF/HEIC：JDK ImageIO 和 MC 均不支持，检测后给出清晰提示，要求用户转换为 PNG/JPG。
 */
public class FileSystemTextureLoader {

    /** 单边最大像素数，超过此尺寸的图片拒绝加载，避免 GPU 显存溢出和 OOM。 */
    private static final int MAX_TEXTURE_DIMENSION = 8192;
    /** 解码后总像素数上限（约 16384x16384），防止恶意/超大图片导致 OOM。 */
    private static final long MAX_TOTAL_PIXELS = 256L * 1024L * 1024L;

    public record LoadedTexture(ResourceLocation location, int width, int height) {
    }

    public static LoadedTexture loadAndRegister(File file, String namespace, String prefix) {
        if (!file.exists()) {
            return null;
        }
        try (FileInputStream fis = new FileInputStream(file)) {
            NativeImage image = decodeToNativeImage(fis);
            // 资源安全管理：image 创建成功后，任何后续步骤失败都必须关闭 image 避免泄漏
            try {
                int width = image.getWidth();
                int height = image.getHeight();
                ResourceLocation rl = ResourceLocation.fromNamespaceAndPath(namespace,
                        prefix + "/" + UUID.randomUUID().toString().toLowerCase(Locale.ROOT));
                DynamicTexture dynamicTexture = new DynamicTexture(image);
                try {
                    dynamicTexture.upload();
                    Minecraft.getInstance().getTextureManager().register(rl, dynamicTexture);
                } catch (Exception registerEx) {
                    // register 失败时关闭 dynamicTexture（会同时关闭底层 image）
                    dynamicTexture.close();
                    throw registerEx;
                }
                // register 成功后，dynamicTexture 由 textureManager 管理，不要关闭
                return new LoadedTexture(rl, width, height);
            } catch (Exception ex) {
                // 任何失败都确保 image 被关闭，避免 native 内存泄漏
                try {
                    image.close();
                } catch (Exception closeEx) {
                    Dialog.LOGGER.warn("Failed to close NativeImage after load error: {}", closeEx.toString());
                }
                throw ex;
            }
        } catch (Exception e) {
            Dialog.LOGGER.error("Failed to load texture from file: {}", file, e);
            return null;
        }
    }

    /**
     * 解码输入流为 NativeImage。
     * - PNG 优先走 NativeImage.read 高效路径；若失败（如 16-bit PNG）回退到 ImageIO。
     * - 非 PNG 用 JDK ImageIO 解码（纯 Java，支持 JPG/BMP/GIF/TIFF，无 native 崩溃风险）。
     * - WebP/AVIF/HEIC 检测后抛异常提示用户转换。
     */
    public static NativeImage decodeToNativeImage(InputStream stream) throws IOException {
        byte[] bytes = stream.readAllBytes();
        // 先检测明确不支持的格式，给出清晰可操作的提示
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
                return NativeImage.read(new ByteArrayInputStream(bytes));
            } catch (Exception e) {
                // NativeImage.read 在某些 PNG 变体（16-bit/灰度+alpha 等）下会失败，回退到 ImageIO
                Dialog.LOGGER.warn("decodeToNativeImage: NativeImage.read failed for PNG ({} bytes), falling back to ImageIO: {}",
                        bytes.length, e.toString());
            }
        }
        // 非 PNG 或 NativeImage.read 失败：用 JDK ImageIO 解码（纯 Java，无 native 崩溃风险）
        Dialog.LOGGER.info("decodeToNativeImage: decoding via ImageIO ({} bytes, isPng={}, header={} {} {} {})",
                bytes.length, isPng,
                String.format("%02X", bytes[0] & 0xFF),
                String.format("%02X", bytes[1] & 0xFF),
                String.format("%02X", bytes[2] & 0xFF),
                String.format("%02X", bytes[3] & 0xFF));
        return decodeWithImageIO(bytes);
    }

    /**
     * 识别 JDK ImageIO 和 MC 均不支持的图片格式，返回清晰可操作的错误信息。
     * 重点处理"扩展名是 .png 但实际是 WebP/AVIF/HEIC"这类用户常见误用。
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
        // AVIF: "....ftyp" + "avif"/"avis"
        if (bytes[4] == 'f' && bytes[5] == 't' && bytes[6] == 'y' && bytes[7] == 'p'
                && ((bytes[8] == 'a' && bytes[9] == 'v' && bytes[10] == 'i')
                        || (bytes[8] == 'a' && bytes[9] == 'v' && bytes[10] == 'i' && bytes[11] == 's'))) {
            return "该文件实际是 AVIF 格式。AVIF 不被 Minecraft 支持，请转换为 PNG 或 JPG 后再使用。";
        }
        // HEIC: "....ftyp" + "heic"/"heix"/"mif1"
        if (bytes[4] == 'f' && bytes[5] == 't' && bytes[6] == 'y' && bytes[7] == 'p'
                && bytes[8] == 'h' && bytes[9] == 'e' && bytes[10] == 'i' && (bytes[11] == 'c' || bytes[11] == 'x')) {
            return "该文件实际是 HEIC 格式。HEIC 不被 Minecraft 支持，请转换为 PNG 或 JPG 后再使用。";
        }
        return null;
    }

    /**
     * 用 JDK ImageIO 解码图片字节为 NativeImage。纯 Java 实现，无 native 内存操作。
     * ImageIO 内置支持 JPG/PNG/BMP/GIF/TIFF（不支持 WebP/AVIF/HEIC）。
     *
     * JPG 颜色模型复杂（灰度1分量、RGB 3分量、CMYK 4分量等），直接操作 raster 易越界。
     * 这里先把任意颜色模型的 BufferedImage 统一绘制到 TYPE_INT_ARGB 的目标图，
     * 再用 getRGB 批量获取像素（内部已处理颜色转换），稳定可靠。
     */
    private static NativeImage decodeWithImageIO(byte[] bytes) throws IOException {
        BufferedImage src = ImageIO.read(new ByteArrayInputStream(bytes));
        if (src == null) {
            throw new IOException("ImageIO 无法解码该图片格式（可能是不支持的格式或损坏的文件）");
        }
        int width = src.getWidth();
        int height = src.getHeight();
        // 尺寸上限检查，防止超大图片导致 OOM 或 GPU 显存溢出
        if (width > MAX_TEXTURE_DIMENSION || height > MAX_TEXTURE_DIMENSION) {
            throw new IOException("图片尺寸 " + width + "x" + height + " 超过上限 "
                    + MAX_TEXTURE_DIMENSION + "x" + MAX_TEXTURE_DIMENSION + "，请缩小后再使用");
        }
        if ((long) width * height > MAX_TOTAL_PIXELS) {
            throw new IOException("图片总像素数 " + (width * height) + " 超过上限 " + MAX_TOTAL_PIXELS + "，请缩小后再使用");
        }
        Dialog.LOGGER.info("decodeToNativeImage: ImageIO decoded {}x{}", width, height);
        // 统一转换为 TYPE_INT_ARGB，避免 JPG 灰度/CMYK 等复杂颜色模型导致的分量越界
        BufferedImage argb = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = argb.createGraphics();
        try {
            g.drawImage(src, 0, 0, null);
        } finally {
            g.dispose();
        }
        NativeImage image = new NativeImage(width, height, false);
        try {
            // getRGB 返回 ARGB（int 高位到低位：A R G B），批量获取整行减少调用次数
            int[] rowPixels = new int[width];
            for (int y = 0; y < height; y++) {
                argb.getRGB(0, y, width, 1, rowPixels, 0, width);
                for (int x = 0; x < width; x++) {
                    int argbPixel = rowPixels[x];
                    int a = (argbPixel >> 24) & 0xFF;
                    int r = (argbPixel >> 16) & 0xFF;
                    int g2 = (argbPixel >> 8) & 0xFF;
                    int b = argbPixel & 0xFF;
                    // NativeImage.setPixelRGBA 期望 ABGR (little-endian RGBA)
                    int abgr = (a << 24) | (b << 16) | (g2 << 8) | r;
                    image.setPixelRGBA(x, y, abgr);
                }
            }
            return image;
        } catch (Exception ex) {
            // 解码失败时关闭已创建的 NativeImage，避免泄漏
            try {
                image.close();
            } catch (Exception closeEx) {
                Dialog.LOGGER.warn("Failed to close NativeImage after decode error: {}", closeEx.toString());
            }
            throw ex;
        }
    }
}
