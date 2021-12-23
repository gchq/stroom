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

    @Test
    void testMethodDirTwoArgs(@TempDir Path tempDir1) {
        assertThat(getInstanceTempDir()).isNotNull();
        assertThat(getInstanceTempDir()).exists();
        assertThat(getInstanceTempDir()).isDirectory();

        assertThat(tempDir1).isNotNull();
        assertThat(tempDir1).exists();
        assertThat(tempDir1).isDirectory();

        // As of Junit 5.8.0 each use of @TempDir will get a new dir, previous releases
        // used the same dir for all @TempDir uses.
        assertThat(tempDir1).isNotEqualTo(getInstanceTempDir());
    }

}
