package stroom.processor.impl;

import event.logging.BaseAdvancedQueryOperator.And;
import event.logging.Query;
import event.logging.Query.Advanced;
import stroom.entity.shared.ExpressionCriteria;
import stroom.event.logging.api.DocumentEventLog;
import stroom.processor.api.ProcessorTaskService;
import stroom.processor.shared.FindProcessorTaskAction;
import stroom.processor.shared.ProcessorTask;
import stroom.security.api.SecurityContext;
import stroom.task.api.AbstractTaskHandler;
import stroom.util.shared.BaseResultList;
import stroom.util.shared.ResultList;

import javax.inject.Inject;

class FetchProcessorTaskHandler extends AbstractTaskHandler<FindProcessorTaskAction, ResultList<ProcessorTask>> {
    private final ProcessorTaskService processorTaskService;
    private final DocumentEventLog documentEventLog;
    private final SecurityContext securityContext;

    @Inject
    FetchProcessorTaskHandler(final ProcessorTaskService processorTaskService,
                              final DocumentEventLog documentEventLog,
                              final SecurityContext securityContext) {
        this.processorTaskService = processorTaskService;
        this.documentEventLog = documentEventLog;
        this.securityContext = securityContext;
    }

    @Override
    public ResultList<ProcessorTask> exec(final FindProcessorTaskAction action) {
        final ExpressionCriteria criteria = action.getCriteria();
        return securityContext.secureResult(() -> {
            BaseResultList<ProcessorTask> result;

            final Query query = new Query();
            final Advanced advanced = new Advanced();
            query.setAdvanced(advanced);
            final And and = new And();
            advanced.getAdvancedQueryItems().add(and);

            try {
                result = processorTaskService.find(criteria);
                documentEventLog.search(criteria.getClass().getSimpleName(), query, ProcessorTask.class.getSimpleName(), result.getPageResponse(), null);
            } catch (final RuntimeException e) {
                documentEventLog.search(criteria.getClass().getSimpleName(), query, ProcessorTask.class.getSimpleName(), null, e);
                throw e;
            }

            return result;
        });
    }
}
