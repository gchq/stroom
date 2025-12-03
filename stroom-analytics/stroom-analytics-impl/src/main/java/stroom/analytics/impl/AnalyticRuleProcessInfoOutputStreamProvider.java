/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.analytics.impl;

import stroom.data.shared.StreamTypeNames;
import stroom.data.store.api.Store;
import stroom.data.store.api.Target;
import stroom.feed.api.VolumeGroupNameProvider;
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
    private final String pipelineUuid;
    private final RecordCount recordCount;
    private final ErrorReceiverProxy errorReceiverProxy;
    private final VolumeGroupNameProvider volumeGroupNameProvider;

    private OutputStream processInfoOutputStream;
    private Target processInfoStreamTarget;

    AnalyticRuleProcessInfoOutputStreamProvider(final Store streamStore,
                                                final String feedName,
                                                final String pipelineUuid,
                                                final RecordCount recordCount,
                                                final ErrorReceiverProxy errorReceiverProxy,
                                                final VolumeGroupNameProvider volumeGroupNameProvider) {
        this.streamStore = streamStore;
        this.feedName = feedName;
        this.pipelineUuid = pipelineUuid;
        this.recordCount = recordCount;
        this.errorReceiverProxy = errorReceiverProxy;
        this.volumeGroupNameProvider = volumeGroupNameProvider;
    }

    @Override
    public Destination borrowDestination() {
        return this;
    }

    @Override
    public void returnDestination(final Destination destination) {
    }

    @Override
    public OutputStream getOutputStream() {
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
//                    .processorUuid(analyticUuid)
                    .pipelineUuid(pipelineUuid)
                    .build();

            final String volumeGroupName = volumeGroupNameProvider
                    .getVolumeGroupName(feedName, StreamTypeNames.ERROR, null);
            processInfoStreamTarget = streamStore.openTarget(metaProperties, volumeGroupName);
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
