package stroom.config.global.impl.db;

import org.junit.jupiter.api.Test;
import stroom.config.app.AppConfig;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

class TestYamlConfigurer {
    @Test
    void test() throws IOException {
        String expected;
        try (final InputStream inputStream = getClass().getResourceAsStream("expected.yaml")) {
            expected = new String(inputStream.readAllBytes());
        }

        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        final YamlConfigurer yamlConfigurer = new YamlConfigurer();
        yamlConfigurer.write(new AppConfig(), byteArrayOutputStream);
        final String actual = new String(byteArrayOutputStream.toByteArray());

        assertThat(actual).isEqualTo(expected);
    }
}
