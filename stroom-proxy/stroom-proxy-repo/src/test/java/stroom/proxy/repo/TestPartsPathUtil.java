package stroom.proxy.repo;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestPartsPathUtil {
    @Test
    public void test() throws IOException {
        Path uniqueTestDir = Files.createTempDirectory("stroom");

        final Path zipFile = uniqueTestDir.resolve("test.zip");
        final Path partsDir = PartsPathUtil.createPartsDir(zipFile);
        assertThat(PartsPathUtil.isPartsDir(partsDir)).isTrue();
        final Path parentZipFile = PartsPathUtil.createParentPartsZipFile(partsDir);
        assertThat(parentZipFile).isEqualTo(zipFile);

        final Path part = PartsPathUtil.createPart(partsDir, zipFile, "001");
        assertThat(PartsPathUtil.isPart(part)).isTrue();

        assertThatThrownBy(() -> PartsPathUtil.checkPath(partsDir.getFileName().toString())).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> PartsPathUtil.checkPath(part.getFileName().toString())).isInstanceOf(IOException.class);
    }
}
