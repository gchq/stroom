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

        // No matter how many times you use the annotation in a test (as an instance variable
        // of as a method arg) they will all get the same value
        assertThat(tempDir1).isEqualTo(getInstanceTempDir());
    }

}