package com.example.speech.service.iflytek;

import com.mpatric.mp3agic.Mp3File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.springframework.stereotype.Component;

@Component
public class AudioValidator {
    private static final int MAX_SECONDS = 60;

    public ValidatedAudio validate(byte[] audio, String filename, String encoding, int sampleRate) {
        if (audio.length == 0) {
            throw new IllegalArgumentException("音频文件不能为空");
        }
        if (sampleRate != 8000 && sampleRate != 16000) {
            throw new IllegalArgumentException("采样率仅支持 8000Hz 或 16000Hz");
        }
        if (!"raw".equals(encoding) && !"lame".equals(encoding)) {
            throw new IllegalArgumentException("音频编码仅支持 raw 或 lame");
        }
        String lowerName = filename == null ? "" : filename.toLowerCase();
        if ("raw".equals(encoding)) {
            if (!(lowerName.endsWith(".pcm") || lowerName.endsWith(".raw"))) {
                throw new IllegalArgumentException("raw 编码需要上传 .pcm 或 .raw 文件");
            }
            long maxBytes = (long) sampleRate * 2 * MAX_SECONDS;
            if (audio.length > maxBytes) {
                throw new IllegalArgumentException("PCM 音频不能超过 60 秒");
            }
            int frameSize = sampleRate * 2 * 40 / 1000;
            return new ValidatedAudio(audio, encoding, frameSize);
        }
        if (!lowerName.endsWith(".mp3")) {
            throw new IllegalArgumentException("lame 编码需要上传 .mp3 文件");
        }
        byte[] cleanAudio = stripId3v2(audio);
        long durationMillis = readMp3Duration(cleanAudio);
        if (durationMillis <= 0) {
            throw new IllegalArgumentException("无法确定 MP3 音频时长");
        }
        if (durationMillis > MAX_SECONDS * 1000L) {
            throw new IllegalArgumentException("MP3 音频不能超过 60 秒");
        }
        int frameSize = Math.max(1, (int) Math.ceil(cleanAudio.length * 40.0 / durationMillis));
        return new ValidatedAudio(cleanAudio, encoding, frameSize);
    }

    static byte[] stripId3v2(byte[] audio) {
        if (audio.length < 10 || audio[0] != 'I' || audio[1] != 'D' || audio[2] != '3') {
            return audio;
        }
        int flags = audio[5] & 0xff;
        int tagSize = ((audio[6] & 0x7f) << 21)
                | ((audio[7] & 0x7f) << 14)
                | ((audio[8] & 0x7f) << 7)
                | (audio[9] & 0x7f);
        int totalSize = 10 + tagSize + ((flags & 0x10) == 0x10 ? 10 : 0);
        if (totalSize >= audio.length) {
            throw new IllegalArgumentException("MP3 文件仅包含无效的 ID3 标签");
        }
        return Arrays.copyOfRange(audio, totalSize, audio.length);
    }

    private static long readMp3Duration(byte[] audio) {
        Path temporaryFile = null;
        try {
            temporaryFile = Files.createTempFile("iat-audio-", ".mp3");
            Files.write(temporaryFile, audio);
            return new Mp3File(temporaryFile.toFile()).getLengthInMilliseconds();
        } catch (Exception exception) {
            throw new IllegalArgumentException("无法读取 MP3，请确认文件未损坏", exception);
        } finally {
            if (temporaryFile != null) {
                try {
                    Files.deleteIfExists(temporaryFile);
                } catch (IOException ignored) {
                    temporaryFile.toFile().deleteOnExit();
                }
            }
        }
    }

    public record ValidatedAudio(byte[] bytes, String encoding, int frameSize) {
    }
}
