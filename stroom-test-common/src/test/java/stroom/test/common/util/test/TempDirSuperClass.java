package stroom.test.common.util.test;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import stroom.test.common.util.test.TempDir;
import stroom.test.common.util.test.TempDirExtension;

import java.nio.file.Path;

@ExtendWith(TempDirExtension.class)
public abstract class TempDirSuperClass {

    private Path instanceTempDir;

    @TempDir
    private Path instanceTempDir2;

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
