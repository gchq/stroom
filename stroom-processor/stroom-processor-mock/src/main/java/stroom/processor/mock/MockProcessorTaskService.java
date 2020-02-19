package stroom.processor.mock;

import stroom.entity.shared.ExpressionCriteria;
import stroom.processor.api.ProcessorTaskService;
import stroom.processor.shared.ProcessorTask;
import stroom.processor.shared.ProcessorTaskSummary;
import stroom.util.shared.ResultPage;

import java.util.Collections;

public class MockProcessorTaskService implements ProcessorTaskService {
    @Override
    public ResultPage<ProcessorTask> find(final ExpressionCriteria criteria) {
        return ResultPage.createUnboundedList(Collections.emptyList());
    }

    @Override
    public ResultPage<ProcessorTaskSummary> findSummary(final ExpressionCriteria criteria) {
        return ResultPage.createUnboundedList(Collections.emptyList());
    }
}
