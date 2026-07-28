package top.yourzi.dialog.editor.util;

import top.yourzi.dialog.Dialog;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.DataLine;
import java.io.File;
import java.io.FileInputStream;

/**
 * 音频预览播放器，支持 OGG 和 WAV。融合自 visual_mod_edit_vndialog。
 */
public class AudioPreviewPlayer {
    private static Clip currentClip;
    private static final Object LOCK = new Object();

    public static void play(File file) {
        synchronized (LOCK) {
            stop();
            try {
                String name = file.getName().toLowerCase();
                if (name.endsWith(".ogg")) {
                    try (FileInputStream is = new FileInputStream(file)) {
                        OggDecoder.OggAudio ogg = OggDecoder.decode(is);
                        AudioFormat format = ogg.format();
                        DataLine.Info info = new DataLine.Info(Clip.class, format);
                        currentClip = (Clip) AudioSystem.getLine(info);
                        currentClip.open(format, ogg.audioData(), 0, ogg.audioData().length);
                        currentClip.start();
                    }
                } else if (name.endsWith(".wav")) {
                    try (AudioInputStream audioInput = AudioSystem.getAudioInputStream(file);
                         AudioInputStream decodedInput = AudioSystem.getAudioInputStream(decodedFormat(audioInput.getFormat()), audioInput)) {
                        AudioFormat decodedFormat = decodedInput.getFormat();
                        DataLine.Info info = new DataLine.Info(Clip.class, decodedFormat);
                        currentClip = (Clip) AudioSystem.getLine(info);
                        currentClip.open(decodedInput);
                        currentClip.start();
                    }
                }
            } catch (Exception e) {
                Dialog.LOGGER.error("Failed to play audio: {}", file, e);
            }
        }
    }

    public static void stop() {
        synchronized (LOCK) {
            if (currentClip != null) {
                if (currentClip.isRunning()) {
                    currentClip.stop();
                }
                currentClip.close();
                currentClip = null;
            }
        }
    }

    public static boolean isRunning() {
        synchronized (LOCK) {
            return currentClip != null && currentClip.isRunning();
        }
    }

    private static AudioFormat decodedFormat(AudioFormat baseFormat) {
        return new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                baseFormat.getSampleRate(), 16,
                baseFormat.getChannels(),
                baseFormat.getChannels() * 2,
                baseFormat.getSampleRate(), false);
    }
}
