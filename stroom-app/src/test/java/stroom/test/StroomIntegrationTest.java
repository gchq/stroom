/*
 * Copyright 2016 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.test;

import stroom.security.api.SecurityContext;
import stroom.test.common.util.test.StroomTest;
import stroom.util.io.FileUtil;
import stroom.util.io.TempDirProviderImpl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.io.TempDir;

import javax.inject.Inject;
import java.nio.file.Path;
import java.util.Objects;

/**
 * This class should be common to all component and integration tests.
 */
public abstract class StroomIntegrationTest implements StroomTest {
    private Path testTempDir;

    @Inject
    private CommonTestControl commonTestControl;
    @Inject
    private ContentImportService contentImportService;
    @Inject
    private SecurityContext securityContext;
    @Inject
    private TempDirProviderImpl tempDirProvider;
    @TempDir
    static Path tempDir; // Static makes the temp dir remain constant for the life of the test class.

    private static Class<?> currentTestClass;

    /**
     * Initialise required database entities.
     */
    @BeforeEach
    final void setup(final TestInfo testInfo) {
        if (setupBetweenTests() || !Objects.equals(testInfo.getTestClass().orElse(null), currentTestClass)) {
            currentTestClass = testInfo.getTestClass().orElse(null);

            if (tempDir == null) {
                throw new NullPointerException("Temp dir is null");
            }
            this.testTempDir = tempDir;
            tempDirProvider.setTempDir(tempDir);
            securityContext.asProcessingUser(() -> {
                commonTestControl.cleanup();
                commonTestControl.setup(tempDir);
            });
        }
    }

    @AfterEach
    final void cleanup(final TestInfo testInfo) {
        if (setupBetweenTests() || !Objects.equals(testInfo.getTestClass().orElse(null), currentTestClass)) {
            securityContext.asProcessingUser(() -> commonTestControl.cleanup());
            // We need to delete the contents of the temp dir here as it is the same for the whole of a test class.
            FileUtil.deleteContents(tempDir);
        }
    }

    @Override
    public Path getCurrentTestDir() {
        return testTempDir;
    }

    protected boolean setupBetweenTests() {
        return true;
    }
}
