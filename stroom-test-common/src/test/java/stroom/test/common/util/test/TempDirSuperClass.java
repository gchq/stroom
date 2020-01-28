package stroom.test.common.util.test;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

public abstract class TempDirSuperClass {

    private Path instanceTempDir;

    @TempDir
    Path instanceTempDir2;

    @BeforeEach
    public void setup(@TempDir Path tempDir) {
        Assertions.assertThat(tempDir).isNotNull();
        Assertions.assertThat(instanceTempDir2).isNotNull();

        this.instanceTempDir = tempDir;
    }

    Path getInstanceTempDir() {
        return instanceTempDir;
    }
}
