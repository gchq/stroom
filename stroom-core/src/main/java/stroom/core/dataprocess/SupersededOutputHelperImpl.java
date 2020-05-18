package stroom.core.dataprocess;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.data.store.api.Target;
import stroom.entity.shared.ExpressionCriteria;
import stroom.meta.api.MetaService;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaExpressionUtil;
import stroom.meta.shared.MetaFields;
import stroom.meta.shared.Status;
import stroom.pipeline.task.SupersededOutputHelper;
import stroom.processor.api.ProcessorTaskService;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorTask;
import stroom.processor.shared.ProcessorTaskFields;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.util.pipeline.scope.PipelineScoped;
import stroom.util.shared.ResultPage;

import javax.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@PipelineScoped
public class SupersededOutputHelperImpl implements SupersededOutputHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger(SupersededOutputHelperImpl.class);

    private final MetaService dataMetaService;
    private final ProcessorTaskService processorTaskService;
    private final Set<Target> targets = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private Meta sourceMeta;
    private Processor processor;
    private ProcessorTask processorTask;
    private long processStartTime;

    private boolean initialised;
    private boolean superseded;

    @Inject
    public SupersededOutputHelperImpl(final MetaService dataMetaService,
                                      final ProcessorTaskService processorTaskService) {
        this.dataMetaService = dataMetaService;
        this.processorTaskService = processorTaskService;
    }

    /**
     * Record that we are creating a target for the current processing pipeline.
     *
     * @param supplier The supplier of the new target.
     * @return The newly created target.
     */
    @Override
    public synchronized Target addTarget(final Supplier<Target> supplier) {
        final Target target = supplier.get();
        targets.add(target);
        return target;
    }

    /**
     * Look for any other streams that have been produced by the same pipeline
     * and stream as the one we are processing. If we find any only the latest
     * stream task id is validate (which would normally be this stream task).
     * Any earlier stream tasks their streams should be deleted. If we are an
     * earlier stream task then mark our output as to be deleted (rather than
     * unlock it).
     */
    @Override
    public synchronized boolean isSuperseded() {
        try {
            if (!initialised) {
                LOGGER.debug("SupersededOutputHelper has not been initialised");
                return false;
            }

            Objects.requireNonNull(sourceMeta, "Source stream must not be null");
            Objects.requireNonNull(processor, "Stream processor must not be null");

            if (!superseded) {
                final ExpressionOperator findMetaExpression = new ExpressionOperator.Builder(Op.AND)
                        .addTerm(MetaFields.PARENT_ID, Condition.EQUALS, sourceMeta.getId())
                        .addTerm(MetaFields.PROCESSOR_ID, Condition.EQUALS, processor.getId())
                        .build();
                final FindMetaCriteria findMetaCriteria = new FindMetaCriteria(findMetaExpression);
                final List<Meta> streamList = dataMetaService.find(findMetaCriteria).getValues();

                // Find any task id's that are greater than the current task id for this input meta.
                final ExpressionOperator findTaskExpression = new ExpressionOperator.Builder()
                        .addTerm(ProcessorTaskFields.META_ID, Condition.EQUALS, sourceMeta.getId())
                        .addTerm(ProcessorTaskFields.PROCESSOR_ID, Condition.EQUALS, processor.getId())
                        .addTerm(ProcessorTaskFields.TASK_ID, Condition.GREATER_THAN, processorTask.getId())
                        .build();
                final ResultPage<ProcessorTask> tasks = processorTaskService.find(new ExpressionCriteria(findTaskExpression));
                final OptionalLong maxTaskId = tasks.getValues().stream().mapToLong(ProcessorTask::getId).max();

                // Is our task old?
                superseded = maxTaskId.isPresent() && maxTaskId.getAsLong() != processorTask.getId();

                if (!superseded) {
                    // Get the data we want to retain.
                    final List<Meta> retain = targets
                            .stream()
                            .map(Target::getMeta)
                            .collect(Collectors.toList());
                    streamList.removeAll(retain);
                }

                // Loop around all the streams found above looking for ones to delete
                final Set<Long> idSet = streamList.stream().map(Meta::getId).collect(Collectors.toSet());
                if (idSet.size() > 0) {
                    // If we have found any to delete then delete them now.
                    final FindMetaCriteria findDeleteMetaCriteria = new FindMetaCriteria(MetaExpressionUtil.createDataIdSetExpression(idSet));
                    final long deleteCount = dataMetaService.updateStatus(findDeleteMetaCriteria, null, Status.DELETED);
                    LOGGER.info("checkSuperseded() - Removed {}", deleteCount);
                }
            }
        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
        }

        return superseded;
    }

    public void init(final Meta sourceMeta,
                     final Processor processor,
                     final ProcessorTask processorTask,
                     final long processStartTime) {
        this.sourceMeta = sourceMeta;
        this.processor = processor;
        this.processorTask = processorTask;
        this.processStartTime = processStartTime;

        initialised = true;
    }
}
