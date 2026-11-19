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
import stroom.docref.DocRef;
import stroom.util.shared.scheduler.Schedule;
import stroom.util.shared.scheduler.ScheduleType;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestReplayScheduleFactory {

    private static final long EFFECTIVE_EXECUTION_TIME_MS = 1_700_000_000_000L;

    @Test
    void testCreate_isNewSchedule() {
        final ExecutionSchedule originalSchedule = createOriginalSchedule();

        final ExecutionSchedule replaySchedule = ReplayScheduleFactory.create(createHistory(originalSchedule));

        // A UUID would make the edit dialog treat the replay as an existing schedule, and reusing the original one
        // would collide with it when the replay is created.
        assertThat(replaySchedule.getUuid())
                .isNull();
        assertThat(originalSchedule.getUuid())
                .isEqualTo("original-uuid");
    }

    @Test
    void testCreate_copiesOriginalSchedule() {
        final ExecutionSchedule originalSchedule = createOriginalSchedule();

        final ExecutionSchedule replaySchedule = ReplayScheduleFactory.create(createHistory(originalSchedule));

        assertThat(replaySchedule.getName())
                .isEqualTo("My schedule (replay)");
        assertThat(replaySchedule.isEnabled())
                .isFalse();
        assertThat(replaySchedule.getNodeName())
                .isEqualTo(originalSchedule.getNodeName());
        assertThat(replaySchedule.getSchedule())
                .isEqualTo(originalSchedule.getSchedule());
        assertThat(replaySchedule.getOwningDoc())
                .isEqualTo(originalSchedule.getOwningDoc());
    }

    @Test
    void testCreate_boundedToEffectiveExecutionTime() {
        final ExecutionSchedule replaySchedule = ReplayScheduleFactory.create(
                createHistory(createOriginalSchedule()));

        final ScheduleBounds scheduleBounds = replaySchedule.getScheduleBounds();
        assertThat(scheduleBounds.getStartTimeMs())
                .isEqualTo(EFFECTIVE_EXECUTION_TIME_MS);
        assertThat(scheduleBounds.getEndTimeMs())
                .isEqualTo(EFFECTIVE_EXECUTION_TIME_MS);
    }

    @Test
    void testCreate_nullHistory() {
        assertThat(ReplayScheduleFactory.create(null))
                .isNull();
    }

    @Test
    void testCreate_historyWithoutSchedule() {
        assertThat(ReplayScheduleFactory.create(createHistory(null)))
                .isNull();
    }

    private ExecutionHistory createHistory(final ExecutionSchedule executionSchedule) {
        return ExecutionHistory
                .builder()
                .id(1L)
                .executionSchedule(executionSchedule)
                .executionTimeMs(EFFECTIVE_EXECUTION_TIME_MS + 5_000L)
                .effectiveExecutionTimeMs(EFFECTIVE_EXECUTION_TIME_MS)
                .status(ExecutionHistory.STATUS_COMPLETE)
                .build();
    }

    private ExecutionSchedule createOriginalSchedule() {
        return ExecutionSchedule
                .builder()
                .uuid("original-uuid")
                .name("My schedule")
                .enabled(true)
                .nodeName("node1a")
                .schedule(new Schedule(ScheduleType.CRON, "0 0 * * * ?"))
                .contiguous(true)
                .scheduleBounds(ScheduleBounds
                        .builder()
                        .startTimeMs(1_600_000_000_000L)
                        .build())
                .owningDoc(new DocRef("Report", "report-uuid", "My report"))
                .build();
    }
}
