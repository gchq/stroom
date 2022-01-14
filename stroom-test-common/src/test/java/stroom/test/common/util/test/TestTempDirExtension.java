package stroom.test.common.util.test;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TestTempDirExtension extends TempDirSuperClass {

    @Test
    void testInstanceDir() {
        assertThat(getInstanceTempDir()).isNotNull();
        assertThat(getInstanceTempDir()).exists();
        assertThat(getInstanceTempDir()).isDirectory();
    }

    @Test
    void testMethodDir(@TempDir Path tempDir) {
        assertThat(tempDir).isNotNull();
        assertThat(tempDir).exists();
        assertThat(tempDir).isDirectory();
    }
}
