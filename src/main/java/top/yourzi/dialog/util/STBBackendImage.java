package top.yourzi.dialog.util;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

public class STBBackendImage extends BufferedImage implements AutoCloseable {
    private STBBackendImage(int width, int height) {
        super(width, height, TYPE_INT_ARGB);
    }

    public static STBBackendImage read(InputStream stream) throws IOException {
        try (stream) {
            BufferedImage image = javax.imageio.ImageIO.read(stream);
            if (image == null) {
                throw new IOException("Could not load image");
            }
            STBBackendImage wrapped = new STBBackendImage(image.getWidth(), image.getHeight());
            wrapped.getGraphics().drawImage(image, 0, 0, null);
            return wrapped;
        }
    }

    public static STBBackendImage read(ResourceLocation location) throws IOException {
        return read(Minecraft.getInstance().getResourceManager().open(location));
    }

    @Override
    public void close() {
    }
}
