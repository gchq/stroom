package stroom.util.test;

import org.junit.jupiter.api.Test;

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
    void testMethodDirTwoArgs(@TempDir Path tempDir1, @TempDir Path tempDir2) {
        assertThat(getInstanceTempDir()).isNotNull();
        assertThat(getInstanceTempDir()).exists();
        assertThat(getInstanceTempDir()).isDirectory();

        assertThat(tempDir1).isNotNull();
        assertThat(tempDir1).exists();
        assertThat(tempDir1).isDirectory();

        assertThat(tempDir1).isNotEqualTo(getInstanceTempDir());

        assertThat(tempDir2).isNotNull();
        assertThat(tempDir2).exists();
        assertThat(tempDir2).isDirectory();

        assertThat(tempDir2).isNotEqualTo(tempDir1);
    }

}