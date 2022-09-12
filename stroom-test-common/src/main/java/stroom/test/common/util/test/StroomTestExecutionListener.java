package stroom.test.common.util.test;

import stroom.test.common.util.TestClassLogger;
import stroom.test.common.util.db.DbTestUtil;
import stroom.util.NullSafe;

import org.flywaydb.core.internal.util.ExceptionUtils;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestPlan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;

/**
 * This test listener is registered by service loader using the file
 * stroom-test-common/src/main/resources/META-INF/services/org.junit.platform.launcher.TestExecutionListener
 */
public class StroomTestExecutionListener implements TestExecutionListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(StroomTestExecutionListener.class);
    private static final String KEEP_TEST_DATABASES = "KEEP_TEST_DATABASES";
    private static volatile boolean DROPPED_TEST_DATABASES = false;

    @Override
    public void testPlanExecutionStarted(TestPlan testPlan) {
        // Note this may be run in a different thread to the actual test(s)
        if (!keepTestDatabases()) {
            // Tidy up any test databases from previous runs
            LOGGER.info("Dropping all test databases. Set {}=true to prevent this.", KEEP_TEST_DATABASES);
            if (!DROPPED_TEST_DATABASES) {
                synchronized (StroomTestExecutionListener.class) {
                    if (!DROPPED_TEST_DATABASES) {
                        try {
                            DbTestUtil.dropUnusedTestDatabases();
                        } catch (Exception e) {
                            final Throwable rootCause = ExceptionUtils.getRootCause(e);
                            // TODO: 12/09/2022 There ought to be a better way to do this so we can avoid logging
                            //  stuff about a DB for a pure unit test, but not sure we can tell what is a db
                            //  test and what is a unit test.
                            if (rootCause instanceof SQLException
                                    && rootCause.getMessage().contains("No suitable driver found")) {
                                LOGGER.info("No DB connection to drop test databases. " +
                                        "Assuming this is not an integration test");
                            } else {
                                throw e;
                            }
                        }
                        DROPPED_TEST_DATABASES = true;
                    }
                }
            }
        }

        LOGGER.info("Starting test plan");
    }

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        LOGGER.info("Finished test plan");

        TestClassLogger.writeTestClassesLogToDisk();

        if (keepTestDatabases()) {
            LOGGER.info("{}=true so won't drop all test databases.", KEEP_TEST_DATABASES);
        } else {
            LOGGER.info("Dropping test database for current gradle worker. Set {}=true to prevent this.",
                    KEEP_TEST_DATABASES);
            DbTestUtil.dropUnusedTestDatabases();
        }
    }

    private boolean keepTestDatabases() {
        return NullSafe.test(
                System.getenv(KEEP_TEST_DATABASES),
                str -> str.equalsIgnoreCase("true"));
    }
}
