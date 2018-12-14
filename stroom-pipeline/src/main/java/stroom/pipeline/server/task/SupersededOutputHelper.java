package stroom.pipeline.server.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import stroom.streamstore.server.OldFindStreamCriteria;
import stroom.streamstore.server.StreamStore;
import stroom.streamstore.shared.FindStreamCriteria;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamStatus;
import stroom.streamtask.shared.StreamProcessor;
import stroom.streamtask.shared.StreamTask;
import stroom.util.spring.StroomScope;

import javax.inject.Inject;
import java.util.List;
import java.util.Objects;

@Component
@Scope(StroomScope.TASK)
public class SupersededOutputHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger(SupersededOutputHelper.class);

    private final StreamStore streamStore;

    private Stream sourceStream;
    private StreamProcessor streamProcessor;
    private StreamTask streamTask;
    private long processStartTime;

    private boolean initialised;
    private boolean superseded;

    @Inject
    public SupersededOutputHelper(final StreamStore streamStore) {
        this.streamStore = streamStore;
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
                final OldFindStreamCriteria findStreamCriteria = new OldFindStreamCriteria();
                findStreamCriteria.obtainParentStreamIdSet().add(sourceStream);
                findStreamCriteria.obtainStatusSet().setMatchAll(true);
                findStreamCriteria.obtainStreamProcessorIdSet().add(streamProcessor);

                final List<Stream> streamList = streamStore.find(findStreamCriteria);

                Long latestStreamTaskId = null;
                long latestStreamCreationTime = processStartTime;

                // Find the latest stream task .... this one is not superseded
                for (final Stream stream : streamList) {
                    if (stream.getStreamTaskId() != null && !StreamStatus.DELETED.equals(stream.getStatus())) {
                        if (stream.getCreateMs() > latestStreamCreationTime) {
                            latestStreamCreationTime = stream.getCreateMs();
                            latestStreamTaskId = stream.getStreamTaskId();
                        } else if (stream.getCreateMs() == latestStreamCreationTime
                                && (latestStreamTaskId == null || stream.getStreamTaskId() > latestStreamTaskId)) {
                            latestStreamCreationTime = stream.getCreateMs();
                            latestStreamTaskId = stream.getStreamTaskId();
                        }
                    }
                }

                // Loop around all the streams found above looking for ones to delete
                final FindStreamCriteria findDeleteStreamCriteria = new FindStreamCriteria();
                for (final Stream stream : streamList) {
                    // If the stream is not associated with the latest stream task
                    // and is not already deleted then select it for deletion.
                    if ((latestStreamTaskId == null || !latestStreamTaskId.equals(stream.getStreamTaskId()))
                            && !StreamStatus.DELETED.equals(stream.getStatus())) {
                        findDeleteStreamCriteria.obtainSelectedIdSet().add(stream.getId());
                    }
                }
                // If we have found any to delete then delete them now.
                if (findDeleteStreamCriteria.obtainSelectedIdSet().isConstrained()) {
                    final long deleteCount = streamStore.findDelete(findDeleteStreamCriteria);
                    LOGGER.info("isSuperseded() - Removed {}", deleteCount);
                }

                // Is our task old?
                superseded = latestStreamTaskId != null && latestStreamTaskId != streamTask.getId();
            }
        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
        }

        return superseded;
    }

    public void init(final Stream sourceStream,
                     final StreamProcessor streamProcessor,
                     final StreamTask streamTask,
                     final long processStartTime) {
        this.sourceStream = sourceStream;
        this.streamProcessor = streamProcessor;
        this.streamTask = streamTask;
        this.processStartTime = processStartTime;

        initialised = true;
    }
}
