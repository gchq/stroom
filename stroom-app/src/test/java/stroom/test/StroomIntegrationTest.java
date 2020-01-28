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


import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.io.TempDir;
import stroom.test.common.util.test.StroomTest;

import javax.inject.Inject;
import java.nio.file.Path;

/**
 * This class should be common to all component and integration tests.
 */
public abstract class StroomIntegrationTest implements StroomTest {
    private Path testTempDir;

    @Inject
    private IntegrationTestSetupUtil integrationTestSetupUtil;

    @BeforeAll
    public static void beforeClass() {
        IntegrationTestSetupUtil.reset();
    }

    @AfterAll
    public static void afterClass() {
    }

    protected void onBefore() {
    }

    protected void onAfter() {
    }

    /**
     * Initialise required database entities.
     */
    @BeforeEach
    void before(final TestInfo testInfo, @TempDir final Path tempDir) {
        if (tempDir == null) {
            throw new NullPointerException("Temp dir is null");
        }
        this.testTempDir = tempDir;
        integrationTestSetupUtil.cleanup(this::onAfterSetup);
        onBefore();
    }

    /**
     * Remove all entities from the database.
     */
    @AfterEach
    public void after() {
        onAfter();
    }

    /**
     * Some tests only want some setup to be performed before the first test is
     * executed and then no teardown to occur. If this is the case then they
     * should override this method, perform their one time setup task and then
     * return true.
     */
    protected boolean onAfterSetup() {
        return false;
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
        integrationTestSetupUtil.clean(force);
    }

    @Override
    public Path getCurrentTestDir() {
        return testTempDir;
    }

    protected boolean teardownEnabled() {
        return integrationTestSetupUtil.teardownEnabled();
    }
}
