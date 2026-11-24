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

package stroom.pipeline.stepping.capture;

import stroom.task.api.TaskContext;

/// Gives tests outside this package access to package-private [StreamSweep] state that a real
/// producer would set via [SweepRun].
public final class StreamSweepTestUtil {

    private StreamSweepTestUtil() {
        // Utility class.
    }

    /// Publish a task context on a sweep, as [SweepRun] does when a producer task starts.
    ///
    /// @param sweep       The sweep to publish the task context on.
    /// @param taskContext The task context of the producer task.
    public static void setTaskContext(final StreamSweep sweep, final TaskContext taskContext) {
        sweep.setTaskContext(taskContext);
    }
}
