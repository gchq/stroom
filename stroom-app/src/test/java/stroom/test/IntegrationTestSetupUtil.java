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


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.security.api.SecurityContext;
import stroom.test.common.util.test.TestState;
import stroom.test.common.util.test.TestState.State;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.function.Supplier;

@Singleton
public class IntegrationTestSetupUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(IntegrationTestSetupUtil.class);

    private static final boolean TEAR_DOWN_DATABASE_BETWEEEN_TESTS = true;
    private static boolean XML_SCHEMAS_DOWNLOADED = false;

    private final CommonTestControl commonTestControl;
    private final ContentImportService contentImportService;
    private final SecurityContext securityContext;

    @Inject
    IntegrationTestSetupUtil(final CommonTestControl commonTestControl,
                             final ContentImportService contentImportService,
                             final SecurityContext securityContext) {
        this.commonTestControl = commonTestControl;
        this.contentImportService = contentImportService;
        this.securityContext = securityContext;
    }

    public static void reset() {
        final State state = TestState.getState();
        state.reset();
    }

    public static int getTestCount() {
        final State state = TestState.getState();
        return state.getClassTestCount();
    }

    /**
     * Initialise required database entities.
     */
    public void cleanup(final Supplier<Boolean> onAfterSetup) {
        securityContext.asProcessingUser(() -> {
            final State state = TestState.getState();
            state.incrementTestCount();

            // Setup the database if this is the first test running for this test
            // class or if we always want to recreate the DB between tests.
            if (teardownEnabled() && (TEAR_DOWN_DATABASE_BETWEEEN_TESTS || getTestCount() == 1)) {
                if (!state.isDoneSetup()) {
                    LOGGER.info("before() - commonTestControl.setup()");
                    commonTestControl.teardown();
                    commonTestControl.setup();

                    // Some test classes only want the DB to be created once so they
                    // return true here.
                    state.setDoneSetup(onAfterSetup.get());
                }
            }
        });
    }

    /**
     * Remove all entities from the database and reinitialise required entities.
     */
    public final void clean() {
        clean(false);
    }

    /**
     * Remove all entities from the database and reinitialise required entities.
     */
    public final void clean(final boolean force) {
        // Only bother to clean the database if we have run at least one test in
        // this test class.
        if (force || getTestCount() > 1) {
            teardown(force);
            setup(force);
        }
    }

    /**
     * Initialise required database entities.
     */
    private void setup(final boolean force) {
        // Only bother to manually setup the database if we have run at least
        // one test in this test class.
        if (force || getTestCount() > 1) {
            commonTestControl.setup();
        }
    }

    public void importSchemas(final boolean force) {
        if (force || !XML_SCHEMAS_DOWNLOADED) {
            contentImportService.importStandardPacks();
            XML_SCHEMAS_DOWNLOADED = true;
        }
    }

    /**
     * Remove all entities from the database.
     */
    private void teardown(final boolean force) {
        // Only bother to tear down the database if we have run at least one
        // test in this test class.
        if (force || getTestCount() > 1) {
            commonTestControl.teardown();
        }
    }

    protected boolean teardownEnabled() {
        return true;
    }
}
