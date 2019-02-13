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

import com.google.inject.Guice;
import com.google.inject.Injector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import stroom.db.util.HikariUtil;
import stroom.persist.PersistService;
import stroom.task.api.TaskManager;
import stroom.test.common.util.test.TempDir;

import java.nio.file.Path;

public abstract class AbstractCoreIntegrationTest extends StroomIntegrationTest {
    private static final Injector injector;

    static {
        // Let all connections know that we are in testing mode.
        HikariUtil.setTesting(true);

        injector = Guice.createInjector(new CoreTestModule());

        // Start persistance
        injector.getInstance(PersistService.class).start();

        // Start task manager
        injector.getInstance(TaskManager.class).startup();
    }

    @BeforeEach
    void before(final TestInfo testInfo, @TempDir final Path tempDir) {
//        final Injector childInjector = injector.createChildInjector();
//        childInjector.injectMembers(this);

        injector.injectMembers(this);
        super.before(testInfo, tempDir);
    }
//
//    @After
//    public void after() {
//        // Stop task manager
//        injector.getInstance(TaskManager.class).shutdown();
//
//        // Stop persistance
//        injector.getInstance(PersistService.class).stop();
//    }
}
