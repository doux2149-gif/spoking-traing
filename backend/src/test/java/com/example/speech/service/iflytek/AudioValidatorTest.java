package com.example.speech.service.iflytek;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class AudioValidatorTest {

    @Test
    void removesId3v2Header() {
        byte[] tag = new byte[] { 'I', 'D', '3', 4, 0, 0, 0, 0, 0, 4, 1, 2, 3, 4 };
        byte[] audio = "AUDIO".getBytes(StandardCharsets.UTF_8);
        byte[] source = new byte[tag.length + audio.length];
        System.arraycopy(tag, 0, source, 0, tag.length);
        System.arraycopy(audio, 0, source, tag.length, audio.length);

        assertThat(AudioValidator.stripId3v2(source)).isEqualTo(audio);
    }

    @Test
    void rejectsPcmLongerThanSixtySeconds() {
        AudioValidator validator = new AudioValidator();
        byte[] audio = new byte[16000 * 2 * 60 + 1];

        assertThatThrownBy(() -> validator.validate(audio, "long.pcm", "raw", 16000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("PCM 音频不能超过 60 秒");
    }
}
