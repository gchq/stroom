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

package stroom.util.test;

import stroom.util.config.StroomProperties;
import stroom.util.io.FileUtil;

import java.nio.file.Files;
import java.nio.file.Path;

public class TestState {
    /**
     * Record and modify the test state in a static thread local as we want to
     * reset in static beforeClass() method.
     */
    private static ThreadLocal<State> stateThreadLocal = ThreadLocal.withInitial(() -> {
        final State state = new State();
        state.create();
        return state;
    });

    public static State getState() {
        return stateThreadLocal.get();
    }

    public static class State {
        private Path testDir;
        private int classTestCount;
        private int threadTestCount;
        private boolean doneSetup;

        public void create() {
            try {
                if (testDir == null) {
                    final Path testDir = Files.createTempDirectory("stroom");
//                    final Path rootTestDir = StroomTestUtil.createRootTestDir(initialTempPath);
//                    testDir = StroomTestUtil.createPerThreadTestDir(rootTestDir);

                    // Redirect the temp dir for the tests.
                    StroomProperties.setOverrideProperty(StroomProperties.STROOM_TEMP, FileUtil.getCanonicalPath(testDir), StroomProperties.Source.TEST);

                    FileUtil.forgetTempDir();

                    // Let tests update the database
//                    StroomProperties.setOverrideProperty("stroom.jpaHbm2DdlAuto", "update", "test");
//                    StroomProperties.setOverrideProperty("stroom.connectionTesterClassName",
//                            "stroom.entity.server.util.StroomConnectionTesterOkOnException", "test");
                }
            } catch (final Throwable t) {
                t.printStackTrace(System.err);
                throw new RuntimeException(t.getMessage(), t);
            }
        }

        public void destroy() {
            try {
                if (testDir != null) {
                    FileUtil.deleteDir(testDir);
                    FileUtil.forgetTempDir();
                    StroomProperties.removeOverrides();
                }
            } catch (final Throwable t) {
                t.printStackTrace(System.err);
                throw new RuntimeException(t.getMessage(), t);
            } finally {
                testDir = null;
            }
        }

        public void reset() {
            classTestCount = 0;
            doneSetup = false;
        }

        public void incrementTestCount() {
            classTestCount++;
            threadTestCount++;
        }

        public boolean isDoneSetup() {
            return doneSetup;
        }

        public void setDoneSetup(final boolean doneSetup) {
            this.doneSetup = doneSetup;
        }

        public int getClassTestCount() {
            return classTestCount;
        }
    }
}
