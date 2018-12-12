package stroom.test;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

class TestWorkingDirectory {

    /**
     * Added this test as intellij seemed to be defaulting the working dir to $MODULE_DIR$ as opposed to
     * $MODULE_WORKING_DIR$ in the run configurations
     */
    @Test
    void test() {
        Path workingDir = Paths.get(".");
        assertThat(workingDir.toAbsolutePath().toString()).doesNotContain(".idea/modules");
    }
}
