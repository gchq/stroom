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
