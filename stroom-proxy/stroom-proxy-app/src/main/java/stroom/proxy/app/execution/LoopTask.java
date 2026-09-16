/*
 * Copyright 2026 Crown Copyright
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

package stroom.proxy.app.execution;

/**
 * One unit of a loop's work. The task does its own waiting - on a queue, on a poll - and says what
 * happened, so the loop can back off on failure and never sleep on top of a wait.
 */
@FunctionalInterface
public interface LoopTask {

    Outcome run() throws Exception;

    /**
     * A task that has no outcome to report: it blocks until it has done something, as a directory
     * queue transfer does. Its every return counts as processed.
     */
    static LoopTask ofRunnable(final Runnable runnable) {
        return () -> {
            runnable.run();
            return Outcome.PROCESSED;
        };
    }

    enum Outcome {
        /** Work was done; loop again at once. */
        PROCESSED,
        /** Nothing to do; the task already waited, so loop again at once. */
        NOTHING,
        /** The work failed; back off before the next attempt. */
        FAILED
    }
}
