package stroom.config.global.impl.db;

import org.junit.jupiter.api.Test;
import stroom.config.app.AppConfig;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

class TestYamlConfigurer {
    @Test
    void test() throws URISyntaxException, IOException {
        final URL url = getClass().getResource("/expected.yaml");
        final Path exampleFile = Paths.get(url.toURI());
        final String expected = new String(Files.readAllBytes(exampleFile));

        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        final YamlConfigurer yamlConfigurer = new YamlConfigurer();
        yamlConfigurer.write(new AppConfig(), byteArrayOutputStream);
        final String actual = new String(byteArrayOutputStream.toByteArray());

        assertThat(actual).isEqualTo(expected);
    }
}
