package stroom.analytics.impl;

import stroom.data.shared.StreamTypeNames;
import stroom.data.store.api.Store;
import stroom.data.store.api.Target;
import stroom.meta.api.MetaProperties;
import stroom.pipeline.destination.Destination;
import stroom.pipeline.destination.DestinationProvider;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.factory.AbstractElement;
import stroom.pipeline.state.RecordCount;
import stroom.pipeline.task.ProcessStatisticsFactory;
import stroom.pipeline.task.ProcessStatisticsFactory.ProcessStatistics;
import stroom.util.io.WrappedOutputStream;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.List;

public class AnalyticRuleProcessInfoOutputStreamProvider extends AbstractElement
        implements DestinationProvider, Destination, AutoCloseable {

    private static final LambdaLogger LOGGER =
            LambdaLoggerFactory.getLogger(AnalyticRuleProcessInfoOutputStreamProvider.class);

    private final Store streamStore;
    private final String feedName;
    private final String analyticUuid;
    private final String pipelineUuid;
    private final RecordCount recordCount;
    private final ErrorReceiverProxy errorReceiverProxy;

    private OutputStream processInfoOutputStream;
    private Target processInfoStreamTarget;

    AnalyticRuleProcessInfoOutputStreamProvider(final Store streamStore,
                                                final String feedName,
                                                final String analyticUuid,
                                                final String pipelineUuid,
                                                final RecordCount recordCount,
                                                final ErrorReceiverProxy errorReceiverProxy) {
        this.streamStore = streamStore;
        this.feedName = feedName;
        this.analyticUuid = analyticUuid;
        this.pipelineUuid = pipelineUuid;
        this.recordCount = recordCount;
        this.errorReceiverProxy = errorReceiverProxy;
    }

    @Override
    public Destination borrowDestination() {
        return this;
    }

    @Override
    public void returnDestination(final Destination destination) {
    }

    @Override
    public OutputStream getByteArrayOutputStream() {
        return getOutputStream(null, null);
    }

    @Override
    public OutputStream getOutputStream(final byte[] header, final byte[] footer) {
        if (processInfoOutputStream == null) {
            // Create a processing info stream to write all processing
            // information to.
            final MetaProperties metaProperties = MetaProperties.builder()
                    .feedName(feedName)
                    .typeName(StreamTypeNames.ERROR)
                    .processorUuid(analyticUuid)
                    .pipelineUuid(pipelineUuid)
                    .build();

            processInfoStreamTarget = streamStore.openTarget(metaProperties);
            processInfoOutputStream = new WrappedOutputStream(processInfoStreamTarget.next().get()) {
                @Override
                public void close() throws IOException {
                    try {
                        super.flush();
                        super.close();

                    } finally {
                        // Only do something if an output stream was used.
                        if (processInfoStreamTarget != null) {
                            try {
                                // Write statistics meta data.
                                // Get current process statistics
                                final ProcessStatistics processStatistics = ProcessStatisticsFactory.create(
                                        recordCount, errorReceiverProxy);
                                processStatistics.write(processInfoStreamTarget.getAttributes());
                            } catch (final RuntimeException e) {
                                LOGGER.error(e::getMessage, e);
                            }

                            // Close the stream target.
                            try {
                                processInfoStreamTarget.close();
                            } catch (final RuntimeException e) {
                                LOGGER.error(e::getMessage, e);
                            }
                        }
                    }
                }
            };
        }

        return processInfoOutputStream;
    }

    public void close() throws IOException {
        if (processInfoOutputStream != null) {
            processInfoOutputStream.close();
        }
    }

    @Override
    public List<stroom.pipeline.factory.Processor> createProcessors() {
        return Collections.emptyList();
    }
}
