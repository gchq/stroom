package stroom.processor.mock;

import stroom.entity.shared.ExpressionCriteria;
import stroom.processor.api.ProcessorTaskService;
import stroom.processor.shared.ProcessorTask;
import stroom.processor.shared.ProcessorTaskSummary;
import stroom.util.shared.ResultList;

import java.util.Collections;

public class MockProcessorTaskService implements ProcessorTaskService {
    @Override
    public ResultList<ProcessorTask> find(final ExpressionCriteria criteria) {
        return ResultList.createUnboundedList(Collections.emptyList());
    }

    @Override
    public ResultList<ProcessorTaskSummary> findSummary(final ExpressionCriteria criteria) {
        return ResultList.createUnboundedList(Collections.emptyList());
    }
}
