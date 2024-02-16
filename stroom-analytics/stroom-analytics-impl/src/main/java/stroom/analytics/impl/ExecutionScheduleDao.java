package stroom.analytics.impl;

import stroom.analytics.shared.ExecutionHistory;
import stroom.analytics.shared.ExecutionHistoryRequest;
import stroom.analytics.shared.ExecutionSchedule;
import stroom.analytics.shared.ExecutionScheduleRequest;
import stroom.analytics.shared.ExecutionTracker;
import stroom.util.shared.ResultPage;

import java.util.Optional;

public interface ExecutionScheduleDao {

    ResultPage<ExecutionSchedule> fetchExecutionSchedule(ExecutionScheduleRequest request);

    ExecutionSchedule createExecutionSchedule(ExecutionSchedule executionSchedule);

    ExecutionSchedule updateExecutionSchedule(ExecutionSchedule executionSchedule);

    Boolean deleteExecutionSchedule(ExecutionSchedule executionSchedule);

    Optional<ExecutionTracker> getTracker(ExecutionSchedule schedule);

    void createTracker(ExecutionSchedule executionSchedule, ExecutionTracker executionTracker);

    void updateTracker(ExecutionSchedule executionSchedule, ExecutionTracker executionTracker);

    ResultPage<ExecutionHistory> fetchExecutionHistory(ExecutionHistoryRequest request);

    void addExecutionHistory(ExecutionHistory executionHistory);

    ExecutionTracker fetchTracker(ExecutionSchedule schedule);
}
