package stroom.processor.mock;

import stroom.entity.shared.ExpressionCriteria;
import stroom.processor.api.ProcessorTaskService;
import stroom.processor.shared.ProcessorTask;
import stroom.processor.shared.ProcessorTaskSummary;
import stroom.util.shared.BaseResultList;

import java.util.Collections;

public class MockProcessorTaskService implements ProcessorTaskService {
    @Override
    public BaseResultList<ProcessorTask> find(final ExpressionCriteria criteria) {
        return BaseResultList.createUnboundedList(Collections.emptyList());
    }

    @Override
    public BaseResultList<ProcessorTaskSummary> findSummary(final ExpressionCriteria criteria) {
        return BaseResultList.createUnboundedList(Collections.emptyList());
    }
}
