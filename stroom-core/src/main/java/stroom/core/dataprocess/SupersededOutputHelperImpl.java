package stroom.core.dataprocess;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFieldNames;
import stroom.meta.shared.MetaService;
import stroom.meta.shared.Status;
import stroom.pipeline.task.SupersededOutputHelper;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorTask;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.util.pipeline.scope.PipelineScoped;

import javax.inject.Inject;
import java.util.List;
import java.util.Objects;

@PipelineScoped
public class SupersededOutputHelperImpl implements SupersededOutputHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger(SupersededOutputHelperImpl.class);

    private final MetaService dataMetaService;

    private Meta sourceMeta;
    private Processor streamProcessor;
    private ProcessorTask streamTask;
    private long processStartTime;

    private boolean initialised;
    private boolean superseded;

    @Inject
    public SupersededOutputHelperImpl(final MetaService dataMetaService) {
        this.dataMetaService = dataMetaService;
    }

    /**
     * Look for any other streams that have been produced by the same pipeline
     * and stream as the one we are processing. If we find any only the latest
     * stream task id is validate (which would normally be this stream task).
     * Any earlier stream tasks their streams should be deleted. If we are an
     * earlier stream task then mark our output as to be deleted (rather than
     * unlock it).
     */
    public boolean isSuperseded() {
        try {
            if (!initialised) {
                LOGGER.debug("SupersededOutputHelper has not been initialised");
                return false;
            }

            Objects.requireNonNull(sourceMeta, "Source stream must not be null");
            Objects.requireNonNull(streamProcessor, "Stream processor must not be null");

            if (!superseded) {
                final ExpressionOperator expression = new ExpressionOperator.Builder(Op.AND)
                        .addTerm(MetaFieldNames.PARENT_ID, Condition.EQUALS, String.valueOf(sourceMeta.getId()))
                        .addTerm(MetaFieldNames.PROCESSOR_ID, Condition.EQUALS, String.valueOf(streamProcessor.getId()))
                        .build();
                final FindMetaCriteria findMetaCriteria = new FindMetaCriteria(expression);
                final List<Meta> streamList = dataMetaService.find(findMetaCriteria);

                Long latestStreamTaskId = null;
                long latestStreamCreationTime = processStartTime;

                // Find the latest stream task .... this one is not superseded
                for (final Meta meta : streamList) {
                    // TODO : @66 REMOVE STREAM TASK ID FROM STREAM AND QUERY THE STREAM PROCESSOR SERVICE TO FIND THE LATEST TASK ID FOR THE CURRENT INPUT STREAM AND PROCESSOR

                    if (meta.getProcessorTaskId() != null && !Status.DELETED.equals(meta.getStatus())) {
                        if (meta.getCreateMs() > latestStreamCreationTime) {
                            latestStreamCreationTime = meta.getCreateMs();
                            latestStreamTaskId = meta.getProcessorTaskId();
                        } else if (meta.getCreateMs() == latestStreamCreationTime
                                && (latestStreamTaskId == null || meta.getProcessorTaskId() > latestStreamTaskId)) {
                            latestStreamCreationTime = meta.getCreateMs();
                            latestStreamTaskId = meta.getProcessorTaskId();
                        }
                    }
                }

                // Loop around all the streams found above looking for ones to delete
                final FindMetaCriteria findDeleteMetaCriteria = new FindMetaCriteria();
                for (final Meta meta : streamList) {
                    // If the stream is not associated with the latest stream task
                    // and is not already deleted then select it for deletion.
                    if ((latestStreamTaskId == null || !latestStreamTaskId.equals(meta.getProcessorTaskId()))
                            && !Status.DELETED.equals(meta.getStatus())) {
                        findDeleteMetaCriteria.obtainSelectedIdSet().add(meta.getId());
                    }
                }

                // If we have found any to delete then delete them now.
                if (findDeleteMetaCriteria.obtainSelectedIdSet().isConstrained()) {
                    final long deleteCount = dataMetaService.updateStatus(findDeleteMetaCriteria, Status.DELETED);
                    LOGGER.info("checkSuperseded() - Removed {}", deleteCount);
                }

                // Is our task old?
                superseded = latestStreamTaskId != null && latestStreamTaskId != streamTask.getId();
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
        this.streamProcessor = processor;
        this.streamTask = processorTask;
        this.processStartTime = processStartTime;

        initialised = true;
    }
}
