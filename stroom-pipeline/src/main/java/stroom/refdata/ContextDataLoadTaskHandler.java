/*
 * Copyright 2016 Crown Copyright
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

package stroom.refdata;

import stroom.feed.shared.Feed;
import stroom.io.StreamCloser;
import stroom.pipeline.server.EncodingSelection;
import stroom.pipeline.server.errorhandler.ErrorReceiverIdDecorator;
import stroom.pipeline.server.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.server.errorhandler.StoredErrorReceiver;
import stroom.pipeline.server.factory.Pipeline;
import stroom.pipeline.server.factory.PipelineDataCache;
import stroom.pipeline.server.factory.PipelineFactory;
import stroom.pipeline.shared.PipelineEntity;
import stroom.pipeline.shared.PipelineEntityService;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.state.FeedHolder;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamType;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.util.logging.StroomLogger;
import stroom.util.shared.Severity;
import stroom.util.spring.StroomScope;
import org.springframework.context.annotation.Scope;

import javax.annotation.Resource;
import java.io.IOException;
import java.io.InputStream;

@TaskHandlerBean(task = ContextDataLoadTask.class)
@Scope(value = StroomScope.TASK)
public class ContextDataLoadTaskHandler extends AbstractTaskHandler<ContextDataLoadTask, MapStore> {
    private static final StroomLogger LOGGER = StroomLogger.getLogger(ContextDataLoadTaskHandler.class);

    @Resource
    private PipelineFactory pipelineFactory;
    @Resource
    private MapStoreHolder mapStoreHolder;
    @Resource
    private FeedHolder feedHolder;
    @Resource
    private ErrorReceiverProxy errorReceiverProxy;
    @Resource(name = "cachedPipelineEntityService")
    private PipelineEntityService pipelineEntityService;
    @Resource
    private PipelineDataCache pipelineDataCache;

    private ErrorReceiverIdDecorator errorReceiver;

    @Override
    public MapStore exec(final ContextDataLoadTask task) {
        final StoredErrorReceiver storedErrorReceiver = new StoredErrorReceiver();
        final MapStoreBuilder mapStoreBuilder = new MapStoreBuilderImpl(storedErrorReceiver);
        errorReceiver = new ErrorReceiverIdDecorator(getClass().getSimpleName(), storedErrorReceiver);
        errorReceiverProxy.setErrorReceiver(errorReceiver);

        final InputStream inputStream = task.getInputStream();
        final Stream stream = task.getStream();
        final Feed feed = task.getFeed();

        if (inputStream != null) {
            final StreamCloser streamCloser = new StreamCloser();
            streamCloser.add(inputStream);

            try {
                String contextIdentifier = null;

                if (LOGGER.isDebugEnabled()) {
                    final StringBuilder sb = new StringBuilder();
                    sb.append("(feed = ");
                    sb.append(feed.getName());
                    if (stream != null) {
                        sb.append(", source id = ");
                        sb.append(stream.getId());
                    }
                    sb.append(")");
                    contextIdentifier = sb.toString();
                    LOGGER.debug("Loading context data " + contextIdentifier);
                }

                // Create the parser.
                final PipelineEntity pipelineEntity = pipelineEntityService.loadByUuid(task.getContextPipeline().getUuid());
                final PipelineData pipelineData = pipelineDataCache.getOrCreate(pipelineEntity);
                final Pipeline pipeline = pipelineFactory.create(pipelineData);

                feedHolder.setFeed(feed);

                // Get the appropriate encoding for the stream type.
                final String encoding = EncodingSelection.select(feed, StreamType.CONTEXT);
                mapStoreHolder.setMapStoreBuilder(mapStoreBuilder);
                // Parse the stream.
                pipeline.process(inputStream, encoding);

                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Finished loading context data " + contextIdentifier);
                }
            } catch (final Throwable e) {
                log(Severity.FATAL_ERROR, "Error loading context data: " + e.getMessage(), e);
            } finally {
                try {
                    // Close all open streams.
                    streamCloser.close();
                } catch (final IOException e) {
                    log(Severity.FATAL_ERROR, "Error closing context data stream: " + e.getMessage(), e);
                }
            }
        }

        return mapStoreBuilder.getMapStore();
    }

    private void log(final Severity severity, final String message, final Throwable e) {
        LOGGER.debug(message, e);
        errorReceiver.log(severity, null, getClass().getSimpleName(), message, e);
    }
}
