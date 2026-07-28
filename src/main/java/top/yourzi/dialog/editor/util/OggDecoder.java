package top.yourzi.dialog.editor.util;

import org.lwjgl.stb.STBVorbis;
import org.lwjgl.stb.STBVorbisInfo;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

/**
 * OGG Vorbis 音频解码器，使用 LWJGL STB 库。融合自 visual_mod_edit_vndialog。
 */
public class OggDecoder {

    public record OggAudio(byte[] audioData, AudioFormat format) {
    }

    public static OggAudio decode(InputStream inputStream) throws IOException {
        ByteBuffer oggData = readAll(inputStream);
        long decoder = 0L;
        ShortBuffer pcm = null;
        MemoryStack stack = null;
        try {
            stack = MemoryStack.stackPush();
            int[] error = new int[1];
            decoder = STBVorbis.stb_vorbis_open_memory(oggData, error, null);
            if (decoder == 0L) {
                throw new IOException("Failed to open OGG, error: " + error[0]);
            }
            STBVorbisInfo info = STBVorbisInfo.malloc(stack);
            STBVorbis.stb_vorbis_get_info(decoder, info);
            int channels = info.channels();
            int sampleRate = info.sample_rate();
            int totalSamplesEstimate = STBVorbis.stb_vorbis_stream_length_in_samples(decoder);
            if (totalSamplesEstimate <= 0) {
                totalSamplesEstimate = 2646000;
            }
            int bufferSize = totalSamplesEstimate * channels;
            pcm = MemoryUtil.memAllocShort(bufferSize);
            int totalWritten = 0;
            while (true) {
                int remaining = pcm.remaining();
                if (remaining < channels * 512) {
                    pcm.flip();
                    ShortBuffer newBuf = MemoryUtil.memAllocShort(pcm.capacity() * 2);
                    newBuf.put(pcm);
                    MemoryUtil.memFree(pcm);
                    pcm = newBuf;
                    pcm.position(totalWritten);
                }
                int samples = STBVorbis.stb_vorbis_get_samples_short_interleaved(decoder, channels, pcm);
                if (samples == 0) break;
                pcm.position(totalWritten += samples * channels);
            }
            pcm.flip();
            ByteBuffer byteBuf = ByteBuffer.allocateDirect(pcm.remaining() * 2);
            byteBuf.order(ByteOrder.nativeOrder());
            byteBuf.asShortBuffer().put(pcm);
            byteBuf.rewind();
            byte[] audioData = new byte[byteBuf.remaining()];
            byteBuf.get(audioData);
            AudioFormat format = new AudioFormat(sampleRate, 16, channels, true, false);
            return new OggAudio(audioData, format);
        } finally {
            if (stack != null) {
                stack.close();
            }
            if (pcm != null) {
                MemoryUtil.memFree(pcm);
            }
            if (decoder != 0L) {
                STBVorbis.stb_vorbis_close(decoder);
            }
            MemoryUtil.memFree(oggData);
        }
    }

    private static ByteBuffer readAll(InputStream input) throws IOException {
        ReadableByteChannel channel = Channels.newChannel(input);
        ByteBuffer buffer = MemoryUtil.memAlloc(8192);
        while (channel.read(buffer) != -1) {
            if (buffer.remaining() == 0) {
                buffer = MemoryUtil.memRealloc(buffer, buffer.capacity() * 2);
            }
        }
        buffer.flip();
        return buffer;
    }
}
