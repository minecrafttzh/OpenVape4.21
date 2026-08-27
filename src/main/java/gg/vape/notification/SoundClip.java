package gg.vape.notification;

import gg.vape.Vape;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.sound.sampled.spi.AudioFileReader;
import javax.sound.sampled.spi.FormatConversionProvider;

public class SoundClip {
    private static final String[] SUPPORTED_EXTENSIONS = new String[]{".wav", ".au", ".aif", ".aiff", ".ogg"};
    private static final Object SPI_LOCK = new Object();
    private static AudioFileReader vorbisFileReader;
    private static FormatConversionProvider vorbisFormatProvider;
    private static boolean vorbisLookupAttempted;
    private final byte[] audioData;

    public ByteArrayInputStream openStream() {
        return new ByteArrayInputStream(this.audioData);
    }

    private static byte[] loadAudioData(String resourceName) {
        if (resourceName.contains(".")) {
            String resourcePath = "sounds/" + resourceName;
            byte[] data = Vape.readResource(resourcePath);
            if (data != null) {
                return data;
            }
            throw new IllegalArgumentException("Missing sound resource: " + resourceName);
        }
        for (String extension : SUPPORTED_EXTENSIONS) {
            String resourcePath = "sounds/" + resourceName + extension;
            byte[] data = Vape.readResource(resourcePath);
            if (data == null) continue;
            return data;
        }
        throw new IllegalArgumentException(
                "Missing sound resource with supported extensions: " + resourceName);
    }

    private static void ensureVorbisProviders() {
        synchronized (SPI_LOCK) {
            if (vorbisLookupAttempted) {
                return;
            }
            vorbisLookupAttempted = true;
            String[] readerCandidates = new String[]{
                    "javazoom.spi.vorbis.sampled.file.VorbisAudioFileReader",
                    "javazoom.spi.ogg.vorbis.sampled.file.OggVorbisAudioFileReader",
                    "org.tritonus.sampled.file.vorbis.VorbisAudioFileReader"};
            for (String className : readerCandidates) {
                try {
                    Class<?> readerClass = Class.forName(className, true, SoundClip.class.getClassLoader());
                    AudioFileReader reader = (AudioFileReader)readerClass.getDeclaredConstructor(new Class[0]).newInstance(new Object[0]);
                    vorbisFileReader = reader;
                    break;
                }
                catch (Throwable ignored) {
                }
            }
            String[] converterCandidates = new String[]{
                    "javazoom.spi.vorbis.sampled.convert.VorbisFormatConversionProvider",
                    "javazoom.spi.ogg.vorbis.sampled.convert.VorbisFormatConversionProvider",
                    "org.tritonus.sampled.convert.vorbis.VorbisFormatConversionProvider"};
            for (String className : converterCandidates) {
                try {
                    Class<?> providerClass = Class.forName(className, true, SoundClip.class.getClassLoader());
                    FormatConversionProvider provider = (FormatConversionProvider)providerClass.getDeclaredConstructor(new Class[0]).newInstance(new Object[0]);
                    vorbisFormatProvider = provider;
                    break;
                }
                catch (Throwable ignored) {
                }
            }
            if (vorbisFileReader == null || vorbisFormatProvider == null) {
                Vape.logError("Vorbis provider lookup incomplete: reader=" + vorbisFileReader
                        + ", converter=" + vorbisFormatProvider + "; .ogg sounds may not play.");
            }
        }
    }

    private static AudioInputStream openAudioStream(ByteArrayInputStream rawStream)
            throws UnsupportedAudioFileException, Exception {
        rawStream.mark(Integer.MAX_VALUE);
        try {
            return AudioSystem.getAudioInputStream((InputStream)rawStream);
        }
        catch (UnsupportedAudioFileException viaSystem) {
            SoundClip.ensureVorbisProviders();
            if (vorbisFileReader != null) {
                rawStream.reset();
                try {
                    return vorbisFileReader.getAudioInputStream((InputStream)rawStream);
                }
                catch (UnsupportedAudioFileException ignored) {
                }
            }
            throw viaSystem;
        }
    }

    private static AudioInputStream decodeToPcm(AudioInputStream encodedStream) throws Exception {
        AudioFormat baseFormat = encodedStream.getFormat();
        if (AudioFormat.Encoding.PCM_SIGNED.equals(baseFormat.getEncoding())
                || AudioFormat.Encoding.PCM_UNSIGNED.equals(baseFormat.getEncoding())) {
            return encodedStream;
        }
        int sampleSizeInBits = baseFormat.getSampleSizeInBits() > 0 ? baseFormat.getSampleSizeInBits() : 16;
        AudioFormat pcmTarget = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                baseFormat.getSampleRate(),
                sampleSizeInBits,
                baseFormat.getChannels(),
                baseFormat.getChannels() * (sampleSizeInBits / 8),
                baseFormat.getSampleRate(),
                false);
        try {
            AudioInputStream converted = AudioSystem.getAudioInputStream(pcmTarget, encodedStream);
            if (converted != null) {
                return converted;
            }
        }
        catch (IllegalArgumentException noSystemProvider) {
            // AudioSystem 没注册 FormatConversionProvider；走手动实例化的 vorbis 提供者。
        }
        SoundClip.ensureVorbisProviders();
        if (vorbisFormatProvider != null && vorbisFormatProvider.isConversionSupported(pcmTarget, baseFormat)) {
            return vorbisFormatProvider.getAudioInputStream(pcmTarget, encodedStream);
        }
        throw new IllegalArgumentException("Cannot convert sound encoding "
                + baseFormat.getEncoding() + " to PCM (sampleRate=" + baseFormat.getSampleRate()
                + ", channels=" + baseFormat.getChannels() + ")");
    }

    public SoundClip(String resourceName) {
        this.audioData = SoundClip.loadAudioData(resourceName);
    }

    /**
     * 非阻塞地播放当前音效。允许同一 SoundClip 或不同 SoundClip 的多次
     * play() 调用完全重叠（各自创建独立 Clip 实例，不会相互 stop，
     * 播放结束通过 LineListener 自动 close 以释放 native 音频资源）。
     */
    public void play(float volumePercent) {
        try {
            AudioInputStream encodedStream = SoundClip.openAudioStream(this.openStream());
            AudioInputStream pcmStream = SoundClip.decodeToPcm(encodedStream);
            final Clip clip = AudioSystem.getClip();
            clip.open(pcmStream);
            try {
                FloatControl gainControl = (FloatControl)clip.getControl(FloatControl.Type.MASTER_GAIN);
                double linear = Math.max((double)volumePercent / 100.0, 1.0E-4);
                gainControl.setValue(20.0f * (float)Math.log10(linear));
            }
            catch (Exception gainError) {
                // 极少数驱动或虚拟线不提供 MASTER_GAIN；不致命，默认音量继续。
                Vape.logThrowable(gainError);
            }
            // 播放结束/主动停止时释放 line，避免 Mixer 句柄泄漏。
            clip.addLineListener((LineEvent event) -> {
                if (event.getType() == LineEvent.Type.STOP
                        || event.getType() == LineEvent.Type.CLOSE) {
                    try {
                        event.getLine().close();
                    }
                    catch (Exception ignored) {
                    }
                }
            });
            clip.start();
        }
        catch (Exception error) {
            Vape.logThrowable(error);
        }
    }

    private static Exception propagateException(Exception error) {
        return error;
    }
}
