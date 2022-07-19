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
import stroom.util.io.TempDirProvider;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;

import java.nio.file.Path;
import javax.inject.Inject;

/**
 * This class should be common to all component and integration tests.
 */
public abstract class StroomIntegrationTest implements StroomTest {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StroomIntegrationTest.class);

    private static final ThreadLocal<StroomIntegrationTest> CURRENT_TEST_CLASS_THREAD_LOCAL = new ThreadLocal<>();

    @Inject
    private CommonTestControl commonTestControl;
    @Inject
    private SecurityContext securityContext;
    @Inject
    private TempDirProvider tempDirProvider;

    private Path testTempDir;

    /**
     * Initialise required database entities.
     * <p>
     * Note this method is public to prevent subclasses from hiding.
     */
    @BeforeEach
    public final void setup(final TestInfo testInfo) {
        debug("setup", testInfo);
        if (CURRENT_TEST_CLASS_THREAD_LOCAL.get() == null) {
            testTempDir = tempDirProvider.get();
            if (testTempDir == null) {
                throw new NullPointerException("Temp dir is null");
            }
            securityContext.asProcessingUser(() -> commonTestControl.setup(testTempDir));
            CURRENT_TEST_CLASS_THREAD_LOCAL.set(this);
        }
    }

    /**
     * Cleanup the database and caches
     * <p>
     * Note this method is public to prevent subclasses from hiding.
     */
    @AfterEach
    public final void cleanup(final TestInfo testInfo) {
        debug("cleanup", testInfo);
        if (CURRENT_TEST_CLASS_THREAD_LOCAL.get() == null) {
            throw new IllegalStateException("Cleanup called without setup");
        } else if (cleanupBetweenTests()) {
            cleanup(securityContext, commonTestControl, testTempDir);
        }
    }

    /**
     * Ensure final cleanup even if we aren't clearing between tests.
     */
    @AfterAll
    public static void finalCleanup() {
        final StroomIntegrationTest stroomIntegrationTest = CURRENT_TEST_CLASS_THREAD_LOCAL.get();
        if (stroomIntegrationTest != null) {
            cleanup(stroomIntegrationTest.securityContext,
                    stroomIntegrationTest.commonTestControl,
                    stroomIntegrationTest.testTempDir);
        }
    }

    private void debug(final String message,
                       final TestInfo testInfo) {
        LOGGER.debug(() -> message + " " +
                testInfo.getTestClass()
                        .map(Class::getSimpleName)
                        .orElse("") +
                " " +
                testInfo.getDisplayName());
    }

    private static void cleanup(final SecurityContext securityContext,
                                final CommonTestControl commonTestControl,
                                final Path tempDir) {
        securityContext.asProcessingUser(commonTestControl::cleanup);
        // We need to delete the contents of the temp dir here as it is the same for the whole of a test class.
        FileUtil.deleteContents(tempDir);
        CURRENT_TEST_CLASS_THREAD_LOCAL.set(null);
    }

    @Override
    public final Path getCurrentTestDir() {
        return testTempDir;
    }

    protected boolean cleanupBetweenTests() {
        return true;
    }
}
