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

public class TestState {
    /**
     * Record and modify the test state in a static thread local as we want to
     * reset in static beforeClass() method.
     */
    private static ThreadLocal<State> stateThreadLocal = ThreadLocal.withInitial(() -> {
        return new State();
    });

    public static State getState() {
        return stateThreadLocal.get();
    }

    public static class State {
        private int classTestCount;
        private int threadTestCount;
        private boolean doneSetup;

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
