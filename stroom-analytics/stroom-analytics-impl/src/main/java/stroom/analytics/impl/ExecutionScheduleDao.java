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

package stroom.analytics.impl;

import stroom.analytics.shared.ExecutionHistory;
import stroom.analytics.shared.ExecutionHistoryRequest;
import stroom.analytics.shared.ExecutionSchedule;
import stroom.analytics.shared.ExecutionScheduleRequest;
import stroom.analytics.shared.ExecutionTracker;
import stroom.docref.DocRef;
import stroom.util.shared.ResultPage;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface ExecutionScheduleDao {

    /**
     * Fetch the node(s) on which this analytic executes, whether enabled or not.
     */
    Set<ExecutionNode> fetchExecutionNodes(final DocRef analyticDocRef);

    ResultPage<ExecutionSchedule> fetchExecutionSchedule(ExecutionScheduleRequest request);

    Optional<ExecutionSchedule> fetchScheduleById(int id);

    List<ExecutionSchedule> fetchSchedulesByRunAsUser(final String userUuid);

    ExecutionSchedule createExecutionSchedule(ExecutionSchedule executionSchedule);

    ExecutionSchedule updateExecutionSchedule(ExecutionSchedule executionSchedule);

    Boolean deleteExecutionSchedule(ExecutionSchedule executionSchedule);

    Optional<ExecutionTracker> getTracker(ExecutionSchedule schedule);

    void createTracker(ExecutionSchedule executionSchedule, ExecutionTracker executionTracker);

    void updateTracker(ExecutionSchedule executionSchedule, ExecutionTracker executionTracker);

    ResultPage<ExecutionHistory> fetchExecutionHistory(ExecutionHistoryRequest request);

    void addExecutionHistory(ExecutionHistory executionHistory);

    ExecutionTracker fetchTracker(ExecutionSchedule schedule);

    void deleteOldExecutionHistory(Instant age);
}
