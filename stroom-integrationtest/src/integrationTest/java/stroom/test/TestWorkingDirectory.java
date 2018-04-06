package stroom.test;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

public class TestWorkingDirectory {

    /**
     * Added this test as intellij seemed to be defaulting the working dir to $MODULE_DIR$ as opposed to
     * $MODULE_WORKING_DIR$ in the run configurations
     */
    @Test
    public void test() {
        Path workingDir = Paths.get(".");
        Assertions.assertThat(workingDir.toAbsolutePath().toString()).doesNotContain(".idea/modules");
    }
}
