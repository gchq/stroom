/*
 * Copyright 2016-2025 Crown Copyright
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
import stroom.test.common.util.TestClassLogger;
import stroom.test.common.util.db.DbTestUtil;
import stroom.test.common.util.test.StroomTest;
import stroom.util.io.CommonDirSetup;
import stroom.util.io.FileUtil;
import stroom.util.io.HomeDirProvider;
import stroom.util.io.TempDirProvider;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import com.google.inject.Injector;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;

import java.nio.file.Path;

/**
 * This class should be common to all component and integration tests that need a DB.
 * <p>
 * Each test class gets a new Guice {@link Injector} instance.
 * Unless @Execution is used each test method will run sequentially in the same thread
 * but with a new test class instance (and injector).
 * Each test thread get a new empty DB with a randomly generated name, which ensures
 * test isolation when running tests concurrently. The DB tables and in-memory state
 * are cleared down after each test method (unless cleanupBetweenTests is overridden).
 * Each test class also gets its own temporary directory for any file system state, e.g.
 * streams/indexes which is cleared down after each test method (unless
 * cleanupBetweenTests is overridden), and this dir is re-used in all test methods.
 * Gradle provides concurrency by running tests in multiple JVMs so any state outside the JVM,
 * e.g. files/database must be namespaced that JVM somehow, e.g. using
 * {@link DbTestUtil#getGradleWorker()} in the name.
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
    @Inject
    private HomeDirProvider homeDirProvider;
    @Inject
    private Injector injector;

    private Path testTempDir;

    static {
        CommonDirSetup.setup();
    }

    /**
     * Initialise required database entities.
     * <p>
     * Note this method is public to prevent subclasses from hiding.
     */
    @BeforeEach
    public final void setup(final TestInfo testInfo) {
        info("BeforeEach setup", testInfo);
        if (CURRENT_TEST_CLASS_THREAD_LOCAL.get() == null) {
            testTempDir = tempDirProvider.get();
            if (testTempDir == null) {
                throw new NullPointerException("Temp dir is null");
            }
            securityContext.asProcessingUser(() ->
                    commonTestControl.setup(testTempDir));

            CURRENT_TEST_CLASS_THREAD_LOCAL.set(this);
            LOGGER.debug("Set CURRENT_TEST_CLASS_THREAD_LOCAL to {} ({})",
                    this.getClass().getSimpleName(), System.identityHashCode(this));
        } else {
            final StroomIntegrationTest current = CURRENT_TEST_CLASS_THREAD_LOCAL.get();
            LOGGER.info("Previous test class on this thread: {}",
                    current.getClass().getSimpleName());
            if (!current.getClass().getName().equals(this.getClass().getName())) {
                throw new RuntimeException("Unexpected change of test without cleanup");
            }
        }

        // Record the test class and method
        TestClassLogger.logTestClasses(testInfo);
    }

    /**
     * Cleanup the database and caches
     * <p>
     * Note this method is public to prevent subclasses from hiding.
     */
    @AfterEach
    public final void cleanup(final TestInfo testInfo) {
        info("AfterEach cleanup", testInfo);
        if (CURRENT_TEST_CLASS_THREAD_LOCAL.get() == null) {
            throw new IllegalStateException("Cleanup called without setup. Did setup fail part way through?");
        } else if (cleanupBetweenTests()) {
            cleanup();
//            cleanup(securityContext, commonTestControl, testTempDir);
        } else {
            LOGGER.info("Cleanup not required between tests");
        }
    }

    /**
     * Ensure final cleanup even if we aren't clearing between tests.
     */
    @AfterAll
    public static void finalCleanup(final TestInfo testInfo) {

        final StroomIntegrationTest stroomIntegrationTest = CURRENT_TEST_CLASS_THREAD_LOCAL.get();
        if (stroomIntegrationTest != null) {
            stroomIntegrationTest.info("AfterAll cleanup", testInfo);
            stroomIntegrationTest.cleanup();
        } else {
            LOGGER.info("CURRENT_TEST_CLASS_THREAD_LOCAL is null");
        }
    }

    private void info(final String message,
                      final TestInfo testInfo) {
        LOGGER.info(() -> {
            final String className = testInfo.getTestClass()
                    .map(Class::getSimpleName)
                    .orElse("");
            final String testName = testInfo.getDisplayName();
            final String threadName = Thread.currentThread().getName();
            final long threadId = Thread.currentThread().getId();
            final String injectorInstance = NullSafe.get(
                    injector,
                    System::identityHashCode, i -> Integer.toString(i));

            return LogUtil.message("{} {}.{}, test instance: {}, thread: '{}' ({}), " +
                                   "injector instance: {}, cleanupBetweenTests: {}",
                    message,
                    className,
                    testName,
                    System.identityHashCode(this),
                    threadName,
                    threadId,
                    injectorInstance,
                    cleanupBetweenTests());
        });
    }

    private void cleanup() {
        securityContext.asProcessingUser(commonTestControl::cleanup);
        // We need to delete the contents of the temp dir here as it is the same for the whole of a test class.
        LOGGER.info("Deleting contents of {}", testTempDir);
        FileUtil.deleteContents(testTempDir);
        CURRENT_TEST_CLASS_THREAD_LOCAL.set(null);
        LOGGER.debug("Set CURRENT_TEST_CLASS_THREAD_LOCAL to null");
    }

    @Override
    public final Path getCurrentTestDir() {
        return testTempDir;
    }

    protected boolean cleanupBetweenTests() {
        return true;
    }
}
