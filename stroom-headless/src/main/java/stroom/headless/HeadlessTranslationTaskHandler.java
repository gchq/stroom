/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.headless;

import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.StringCriteria;
import stroom.feed.FeedService;
import stroom.feed.MetaMap;
import stroom.feed.StroomHeaderArguments;
import stroom.feed.shared.Feed;
import stroom.feed.shared.FindFeedCriteria;
import stroom.pipeline.ErrorWriterProxy;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.errorhandler.ErrorStatistics;
import stroom.pipeline.errorhandler.LoggedException;
import stroom.pipeline.errorhandler.ProcessException;
import stroom.pipeline.errorhandler.RecordErrorReceiver;
import stroom.pipeline.factory.HasTargets;
import stroom.pipeline.factory.Pipeline;
import stroom.pipeline.factory.PipelineDataCache;
import stroom.pipeline.factory.PipelineFactory;
import stroom.pipeline.filter.RecordOutputFilter;
import stroom.pipeline.filter.SchemaFilter;
import stroom.pipeline.filter.XMLFilter;
import stroom.pipeline.filter.XSLTFilter;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.state.FeedHolder;
import stroom.pipeline.state.MetaData;
import stroom.pipeline.state.MetaDataHolder;
import stroom.pipeline.state.PipelineHolder;
import stroom.pipeline.state.StreamHolder;
import stroom.pipeline.task.StreamMetaDataProvider;
import stroom.docref.DocRef;
import stroom.security.Security;
import stroom.streamstore.fs.serializable.RASegmentInputStream;
import stroom.streamstore.fs.serializable.StreamSourceInputStream;
import stroom.streamstore.fs.serializable.StreamSourceInputStreamProvider;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamType;
import stroom.streamtask.StreamProcessorService;
import stroom.task.AbstractTaskHandler;
import stroom.task.TaskHandlerBean;
import stroom.util.date.DateUtil;
import stroom.util.io.IgnoreCloseInputStream;
import stroom.util.shared.Severity;
import stroom.util.shared.VoidResult;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@TaskHandlerBean(task = HeadlessTranslationTask.class)
class HeadlessTranslationTaskHandler extends AbstractTaskHandler<HeadlessTranslationTask, VoidResult> {
    private final PipelineFactory pipelineFactory;
    private final FeedService feedService;
    private final PipelineStore pipelineStore;
    private final MetaData metaData;
    private final PipelineHolder pipelineHolder;
    private final FeedHolder feedHolder;
    private final MetaDataHolder metaDataHolder;
    private final ErrorReceiverProxy errorReceiverProxy;
    private final ErrorWriterProxy errorWriterProxy;
    private final RecordErrorReceiver recordErrorReceiver;
    private final PipelineDataCache pipelineDataCache;
    private final StreamHolder streamHolder;
    private final StreamProcessorService streamProcessorService;
    private final Security security;

    @Inject
    HeadlessTranslationTaskHandler(final PipelineFactory pipelineFactory,
                                   @Named("cachedFeedService") final FeedService feedService,
                                   @Named("cachedPipelineStore") final PipelineStore pipelineStore,
                                   final MetaData metaData,
                                   final PipelineHolder pipelineHolder,
                                   final FeedHolder feedHolder,
                                   final MetaDataHolder metaDataHolder,
                                   final ErrorReceiverProxy errorReceiverProxy,
                                   final ErrorWriterProxy errorWriterProxy,
                                   final RecordErrorReceiver recordErrorReceiver,
                                   final PipelineDataCache pipelineDataCache,
                                   final StreamHolder streamHolder,
                                   final StreamProcessorService streamProcessorService,
                                   final Security security) {
        this.pipelineFactory = pipelineFactory;
        this.feedService = feedService;
        this.pipelineStore = pipelineStore;
        this.metaData = metaData;
        this.pipelineHolder = pipelineHolder;
        this.feedHolder = feedHolder;
        this.metaDataHolder = metaDataHolder;
        this.errorReceiverProxy = errorReceiverProxy;
        this.errorWriterProxy = errorWriterProxy;
        this.recordErrorReceiver = recordErrorReceiver;
        this.pipelineDataCache = pipelineDataCache;
        this.streamHolder = streamHolder;
        this.streamProcessorService = streamProcessorService;
        this.security = security;
    }

    @Override
    public VoidResult exec(final HeadlessTranslationTask task) {
        return security.secureResult(() -> {
            try {
                // Setup the error handler and receiver.
                errorWriterProxy.setErrorWriter(task.getHeadlessFilter());
                errorReceiverProxy.setErrorReceiver(recordErrorReceiver);

                final InputStream dataStream = task.getDataStream();
                final InputStream metaStream = task.getMetaStream();

                if (metaStream == null) {
                    throw new RuntimeException("No meta data found");
                }

                // Load the meta and context data.
                final MetaMap metaData = new MetaMap();
                metaData.read(metaStream, false);

                // Get the feed.
                final String feedName = metaData.get(StroomHeaderArguments.FEED);
                final Feed feed = getFeed(feedName);
                feedHolder.setFeedName(feedName);

                // Setup the meta data holder.
                metaDataHolder.setMetaDataProvider(new StreamMetaDataProvider(streamHolder, streamProcessorService, pipelineStore));

                // Set the pipeline so it can be used by a filter if needed.
                final List<DocRef> pipelines = pipelineStore.findByName(feedName);
                if (pipelines == null || pipelines.size() == 0) {
                    throw new ProcessException("No pipeline found for feed name '" + feedName + "'");
                }
                if (pipelines.size() > 1) {
                    throw new ProcessException("More than one pipeline found for feed name '" + feedName + "'");
                }

                final DocRef pipelineRef = pipelines.get(0);
                pipelineHolder.setPipeline(pipelineRef);

                // Create the parser.
                final PipelineDoc pipelineDoc = pipelineStore.readDocument(pipelineRef);
                final PipelineData pipelineData = pipelineDataCache.get(pipelineDoc);
                final Pipeline pipeline = pipelineFactory.create(pipelineData);

                // Find last XSLT filter.
                final XMLFilter lastFilter = getLastFilter(pipeline);
                if (!(lastFilter instanceof HasTargets)) {
                    throw new ProcessException(
                            "No appendable filters can be found in pipeline '" + pipelineRef.getName() + "'");
                }
                ((HasTargets) lastFilter).setTarget(task.getHeadlessFilter());

                // Output the meta data for the new stream.
                this.metaData.putAll(metaData);
                task.getHeadlessFilter().changeMetaData(metaData);

                // Create the stream.
                final Stream stream = new Stream();
                // Set the feed.
                stream.setFeed(feed);

                // Set effective time.
                try {
                    final String effectiveTime = metaData.get(StroomHeaderArguments.EFFECTIVE_TIME);
                    if (effectiveTime != null && !effectiveTime.isEmpty()) {
                        stream.setEffectiveMs(DateUtil.parseNormalDateTimeString(effectiveTime));
                    }
                } catch (final RuntimeException e) {
                    outputError(e);
                }

                // Add stream providers for lookups etc.
                final BasicInputStreamProvider streamProvider = new BasicInputStreamProvider(
                        new IgnoreCloseInputStream(task.getDataStream()), task.getDataStream().available());
                streamHolder.setStream(stream);
                streamHolder.addProvider(streamProvider, StreamType.RAW_EVENTS);
                if (task.getMetaStream() != null) {
                    final BasicInputStreamProvider metaStreamProvider = new BasicInputStreamProvider(
                            new IgnoreCloseInputStream(task.getMetaStream()), task.getMetaStream().available());
                    streamHolder.addProvider(metaStreamProvider, StreamType.META);
                }
                if (task.getContextStream() != null) {
                    final BasicInputStreamProvider contextStreamProvider = new BasicInputStreamProvider(
                            new IgnoreCloseInputStream(task.getContextStream()), task.getContextStream().available());
                    streamHolder.addProvider(contextStreamProvider, StreamType.CONTEXT);
                }

                try {
                    pipeline.process(dataStream, feed.getEncoding());
                } catch (final RuntimeException e) {
                    outputError(e);
                }
            } catch (final IOException | RuntimeException e) {
                outputError(e);
            }

            return VoidResult.INSTANCE;
        });
    }

    private XMLFilter getLastFilter(final Pipeline pipeline) {
        XMLFilter filter = getLastFilter(pipeline, RecordOutputFilter.class);
        if (filter == null) {
            filter = getLastFilter(pipeline, SchemaFilter.class);
        }
        if (filter == null) {
            filter = getLastFilter(pipeline, XSLTFilter.class);
        }
        return filter;
    }

    private <T extends XMLFilter> T getLastFilter(final Pipeline pipeline, final Class<T> clazz) {
        final List<T> filters = pipeline.findFilters(clazz);
        if (filters.size() > 0) {
            return filters.get(filters.size() - 1);
        }
        return null;
    }

    private Feed getFeed(final String feedName) {
        if (feedName == null) {
            throw new RuntimeException("No feed name found in meta data");
        }

        final FindFeedCriteria feedCriteria = new FindFeedCriteria();
        feedCriteria.setName(new StringCriteria(feedName));
        final BaseResultList<Feed> feeds = feedService.find(feedCriteria);

        if (feeds.size() == 0) {
            throw new RuntimeException("No configuration found for feed \"" + feedName + "\"");
        }

        return feeds.getFirst();
    }

    /**
     * Used to handle any errors that may occur during translation.
     */
    private void outputError(final Throwable ex) {
        if (errorReceiverProxy != null && !(ex instanceof LoggedException)) {
            try {
                if (ex.getMessage() != null) {
                    errorReceiverProxy.log(Severity.FATAL_ERROR, null, "PipelineStreamProcessor", ex.getMessage(), ex);
                } else {
                    errorReceiverProxy.log(Severity.FATAL_ERROR, null, "PipelineStreamProcessor", ex.toString(), ex);
                }
            } catch (final RuntimeException e) {
                // Ignore exception as we generated it.
            }

            if (errorReceiverProxy.getErrorReceiver() instanceof ErrorStatistics) {
                ((ErrorStatistics) errorReceiverProxy.getErrorReceiver()).checkRecord(-1);
            }
        }
    }

    private static class BasicInputStreamProvider implements StreamSourceInputStreamProvider {
        private final StreamSourceInputStream inputStream;

        BasicInputStreamProvider(final InputStream inputStream, final long size) {
            this.inputStream = new StreamSourceInputStream(inputStream, size);
        }

        @Override
        public long getStreamCount() {
            return 1;
        }

        @Override
        public StreamSourceInputStream getStream(final long streamNo) {
            return inputStream;
        }

        @Override
        public RASegmentInputStream getSegmentInputStream(final long streamNo) {
            return null;
        }

        @Override
        public void close() throws IOException {
            inputStream.close();
        }
    }
}
