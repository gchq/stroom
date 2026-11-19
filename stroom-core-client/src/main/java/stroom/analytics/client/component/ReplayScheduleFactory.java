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

package stroom.analytics.client.component;

import stroom.analytics.shared.ExecutionHistory;
import stroom.analytics.shared.ExecutionSchedule;
import stroom.analytics.shared.ScheduleBounds;

/// Builds the schedule that replays a past execution of a report or analytic rule.
public final class ReplayScheduleFactory {

    static final String REPLAY_NAME_SUFFIX = " (replay)";

    private ReplayScheduleFactory() {
        // Static factory methods only.
    }

    /// Creates a new, unsaved schedule that runs once more for the effective time of a past execution.
    ///
    /// The replay is a new schedule, so it has no UUID. The server assigns one when the schedule is created and
    /// the absence of one is how the rest of the UI recognises a schedule that has not been saved yet.
    ///
    /// @param executionHistory The past execution to replay, may be null.
    /// @return The disabled replay schedule, or null if there is no execution, or no schedule on it, to replay.
    public static ExecutionSchedule create(final ExecutionHistory executionHistory) {
        if (executionHistory == null || executionHistory.getExecutionSchedule() == null) {
            return null;
        }

        final ExecutionSchedule originalSchedule = executionHistory.getExecutionSchedule();
        return originalSchedule
                .copy()
                .uuid(null)
                .name(originalSchedule.getName() + REPLAY_NAME_SUFFIX)
                .enabled(false)
                .scheduleBounds(ScheduleBounds
                        .builder()
                        .startTimeMs(executionHistory.getEffectiveExecutionTimeMs())
                        .endTimeMs(executionHistory.getEffectiveExecutionTimeMs())
                        .build())
                .build();
    }
}
