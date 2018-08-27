package stroom.config.app;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import stroom.util.io.FileUtil;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

class TestYamlUtil {
    @Test
    void test() throws IOException {
        String expected;
        try (final InputStream inputStream = getClass().getResourceAsStream("expected.yaml")) {
            expected = new String(inputStream.readAllBytes());
        }

        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        YamlUtil.write(new AppConfig(), byteArrayOutputStream);
        final String actual = new String(byteArrayOutputStream.toByteArray());

        Assertions.assertThat(actual).isEqualTo(expected);
    }

    @Test
    void testDevYaml() throws FileNotFoundException {
        // Load dev.yaml
        final String codeSourceLocation = this.getClass().getProtectionDomain().getCodeSource().getLocation().getPath();

        Path path = Paths.get(codeSourceLocation);
        while (path != null && !path.getFileName().toString().equals("stroom-config")) {
            path = path.getParent();
        }
        if (path != null) {
            path = path.getParent();
            path = path.resolve("stroom-app");
            path = path.resolve("dev.yml");
        }

        if (path == null) {
            throw new FileNotFoundException("Unable to find dev.yaml");
        }

        try (final InputStream inputStream = Files.newInputStream(path)) {
            final AppConfig appConfig = YamlUtil.read(inputStream);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
