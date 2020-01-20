package stroom.processor.impl;

import event.logging.BaseAdvancedQueryOperator.And;
import event.logging.Query;
import event.logging.Query.Advanced;
import stroom.entity.shared.ExpressionCriteria;
import stroom.event.logging.api.DocumentEventLog;
import stroom.processor.api.ProcessorTaskService;
import stroom.processor.shared.FindProcessorTaskSummaryAction;
import stroom.processor.shared.ProcessorTaskSummary;
import stroom.security.api.SecurityContext;
import stroom.task.api.AbstractTaskHandler;
import stroom.util.shared.BaseResultList;
import stroom.util.shared.ResultList;

import javax.inject.Inject;

class FetchProcessorTaskSummaryHandler extends AbstractTaskHandler<FindProcessorTaskSummaryAction, ResultList<ProcessorTaskSummary>> {
    private final ProcessorTaskService processorTaskService;
    private final DocumentEventLog documentEventLog;
    private final SecurityContext securityContext;

    @Inject
    FetchProcessorTaskSummaryHandler(final ProcessorTaskService processorTaskService,
                                     final DocumentEventLog documentEventLog,
                                     final SecurityContext securityContext) {
        this.processorTaskService = processorTaskService;
        this.documentEventLog = documentEventLog;
        this.securityContext = securityContext;
    }

    @Override
    public ResultList<ProcessorTaskSummary> exec(final FindProcessorTaskSummaryAction action) {
        final ExpressionCriteria criteria = action.getCriteria();
        return securityContext.secureResult(() -> {
            BaseResultList<ProcessorTaskSummary> result;

            final Query query = new Query();
            final Advanced advanced = new Advanced();
            query.setAdvanced(advanced);
            final And and = new And();
            advanced.getAdvancedQueryItems().add(and);

            try {
                result = processorTaskService.findSummary(criteria);
                documentEventLog.search(criteria, query, ProcessorTaskSummary.class.getSimpleName(), result, null);
            } catch (final RuntimeException e) {
                documentEventLog.search(criteria, query, ProcessorTaskSummary.class.getSimpleName(), null, e);
                throw e;
            }

            return result;
        });
    }
}
