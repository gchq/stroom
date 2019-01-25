package stroom.dataprocess;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.data.meta.shared.Meta;
import stroom.data.meta.shared.MetaService;
import stroom.data.meta.shared.Status;
import stroom.data.meta.shared.FindMetaCriteria;
import stroom.data.meta.shared.MetaDataSource;
import stroom.pipeline.scope.PipelineScoped;
import stroom.pipeline.task.SupersededOutputHelper;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.streamtask.shared.Processor;
import stroom.streamtask.shared.ProcessorFilterTask;

import javax.inject.Inject;
import java.util.List;
import java.util.Objects;

@PipelineScoped
public class SupersededOutputHelperImpl implements SupersededOutputHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger(SupersededOutputHelperImpl.class);

    private final MetaService dataMetaService;

    private Meta sourceStream;
    private Processor streamProcessor;
    private ProcessorFilterTask streamTask;
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

            Objects.requireNonNull(sourceStream, "Source stream must not be null");
            Objects.requireNonNull(streamProcessor, "Stream processor must not be null");

            if (!superseded) {
                final ExpressionOperator expression = new ExpressionOperator.Builder(Op.AND)
                        .addTerm(MetaDataSource.PARENT_STREAM_ID, Condition.EQUALS, String.valueOf(sourceStream.getId()))
                        .addTerm(MetaDataSource.STREAM_PROCESSOR_ID, Condition.EQUALS, String.valueOf(streamProcessor.getId()))
                        .build();
                final FindMetaCriteria findStreamCriteria = new FindMetaCriteria(expression);
                final List<Meta> streamList = dataMetaService.find(findStreamCriteria);

                Long latestStreamTaskId = null;
                long latestStreamCreationTime = processStartTime;

                // Find the latest stream task .... this one is not superseded
                for (final Meta stream : streamList) {
                    // TODO : @66 REMOVE STREAM TASK ID FROM STREAM AND QUERY THE STREAM PROCESSOR SERVICE TO FIND THE LATEST TASK ID FOR THE CURRENT INPUT STREAM AND PROCESSOR

                    if (stream.getProcessTaskId() != null && !Status.DELETED.equals(stream.getStatus())) {
                        if (stream.getCreateMs() > latestStreamCreationTime) {
                            latestStreamCreationTime = stream.getCreateMs();
                            latestStreamTaskId = stream.getProcessTaskId();
                        } else if (stream.getCreateMs() == latestStreamCreationTime
                                && (latestStreamTaskId == null || stream.getProcessTaskId() > latestStreamTaskId)) {
                            latestStreamCreationTime = stream.getCreateMs();
                            latestStreamTaskId = stream.getProcessTaskId();
                        }
                    }
                }

                // Loop around all the streams found above looking for ones to delete
                final FindMetaCriteria findDeleteStreamCriteria = new FindMetaCriteria();
                for (final Meta stream : streamList) {
                    // If the stream is not associated with the latest stream task
                    // and is not already deleted then select it for deletion.
                    if ((latestStreamTaskId == null || !latestStreamTaskId.equals(stream.getProcessTaskId()))
                            && !Status.DELETED.equals(stream.getStatus())) {
                        findDeleteStreamCriteria.obtainSelectedIdSet().add(stream.getId());
                    }
                }

                // If we have found any to delete then delete them now.
                if (findDeleteStreamCriteria.obtainSelectedIdSet().isConstrained()) {
                    final long deleteCount = dataMetaService.updateStatus(findDeleteStreamCriteria, Status.DELETED);
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

    public void init(final Meta sourceStream,
                     final Processor streamProcessor,
                     final ProcessorFilterTask streamTask,
                     final long processStartTime) {
        this.sourceStream = sourceStream;
        this.streamProcessor = streamProcessor;
        this.streamTask = streamTask;
        this.processStartTime = processStartTime;

        initialised = true;
    }
}
