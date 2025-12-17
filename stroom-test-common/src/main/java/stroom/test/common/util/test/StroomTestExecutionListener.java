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

package stroom.test.common.util.test;

import stroom.test.common.util.TestClassLogger;
import stroom.test.common.util.db.DbTestUtil;
import stroom.util.shared.NullSafe;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestPlan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;

/**
 * This test listener is registered by service loader using the file
 * stroom-test-common/src/main/resources/META-INF/services/org.junit.platform.launcher.TestExecutionListener
 * Its methods will be called for any tests that include stroom-test-common as a dependency.
 */
public class StroomTestExecutionListener implements TestExecutionListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(StroomTestExecutionListener.class);
    private static final String KEEP_TEST_DATABASES = "KEEP_TEST_DATABASES";
    private static volatile boolean DROPPED_TEST_DATABASES = false;

    @Override
    public void testPlanExecutionStarted(final TestPlan testPlan) {
//        initDropwizardMetricsRegistry();

        // If we don't have a DB conn don't bother doing anything, e.g. is a pure unit test
        // TODO: 12/09/2022 There ought to be a better way to do this so we can avoid logging
        //  stuff about a DB for a pure unit test, but not sure we can tell what is a db
        //  test and what is a unit test.
        if (DbTestUtil.isDbAvailable()) {
            // Called before any tests have started on this JVM.
            // Note this may be run in a different thread to the actual test(s)
            if (!keepTestDatabases()) {
                // Tidy up any test databases from previous runs
                LOGGER.info("Dropping all test databases. Set {}=true to prevent this.", KEEP_TEST_DATABASES);
                if (!DROPPED_TEST_DATABASES) {
                    synchronized (StroomTestExecutionListener.class) {
                        if (!DROPPED_TEST_DATABASES) {
                            try {
                                DbTestUtil.dropUnusedTestDatabases();
                            } catch (final Exception e) {
                                final Throwable rootCause = ExceptionUtils.getRootCause(e);
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
        }

        // hibernate-validator seems to use jboss-logging which spits the following ERROR
        // out to the console if this prop is not set:
        //   ERROR StatusLogger Log4j2 could not find a logging implementation.
        //   Please add log4j-core to the classpath. Using SimpleLogger to log to the console...
        System.setProperty("org.jboss.logging.provider", "slf4j");

        LOGGER.info("Starting test plan");
    }

    @Override
    public void testPlanExecutionFinished(final TestPlan testPlan) {
        // Called after all tests have run on this JVM.
        LOGGER.info("Finished test plan");

//        clearDropwizardMetricsRegistry();

        TestClassLogger.writeTestClassesLogToDisk();

        if (DbTestUtil.isDbAvailable()) {
            if (keepTestDatabases()) {
                LOGGER.info("{}=true so won't drop all test databases.", KEEP_TEST_DATABASES);
            } else {
                LOGGER.info("Dropping test database for current gradle worker. Set {}=true to prevent this.",
                        KEEP_TEST_DATABASES);
                DbTestUtil.dropUnusedTestDatabases();
            }
        }
    }

    private boolean keepTestDatabases() {
        return NullSafe.test(
                System.getenv(KEEP_TEST_DATABASES),
                str -> str.equalsIgnoreCase("true"));
    }

    private void initDropwizardMetricsRegistry() {
        // This normally gets done by dropwizard on boot
        try {
            SharedMetricRegistries.getDefault();
            LOGGER.info("Using existing static metrics registry");
        } catch (final IllegalStateException e) {
            LOGGER.info("Creating new static metrics registry for testing");
            SharedMetricRegistries.setDefault("defaultRegistry", new MetricRegistry());
        }
    }

    private void clearDropwizardMetricsRegistry() {
        LOGGER.info("Clearing metrics registry");
        final MetricRegistry registry = SharedMetricRegistries.getDefault();
        if (registry != null) {
            registry.getNames()
                    .forEach(registry::remove);
        }
    }
}
