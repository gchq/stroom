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

package stroom.datafeed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.docref.DocRef;
import stroom.feed.FeedDocCache;
import stroom.data.meta.api.AttributeMap;
import stroom.feed.AttributeMapUtil;
import stroom.feed.StroomHeaderArguments;
import stroom.feed.shared.FeedDoc;
import stroom.properties.api.PropertyService;
import stroom.proxy.repo.StroomStreamProcessor;
import stroom.security.Security;
import stroom.data.store.api.StreamStore;
import stroom.streamstore.shared.StreamTypeNames;
import stroom.streamtask.StreamTargetStroomStreamHandler;
import stroom.streamtask.statistic.MetaDataStatistic;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * <p>
 * Handle the incoming requests and stream them to disk checking a few things.
 * </p>
 */
class DataFeedRequestHandler implements RequestHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(DataFeedRequestHandler.class);

    private final Security security;
    private final StreamStore streamStore;
    private final FeedDocCache feedDocCache;
    private final MetaDataStatistic metaDataStatistics;
    private final AttributeMapFilterFactory attributeMapFilterFactory;
    private final PropertyService propertyService;
    private final BufferFactory bufferFactory;

    private volatile AttributeMapFilter attributeMapFilter;

    @Inject
    public DataFeedRequestHandler(final Security security,
                                  final StreamStore streamStore,
                                  final FeedDocCache feedDocCache,
                                  final MetaDataStatistic metaDataStatistics,
                                  final AttributeMapFilterFactory attributeMapFilterFactory,
                                  final PropertyService propertyService,
                                  final BufferFactory bufferFactory) {
        this.security = security;
        this.streamStore = streamStore;
        this.feedDocCache = feedDocCache;
        this.metaDataStatistics = metaDataStatistics;
        this.attributeMapFilterFactory = attributeMapFilterFactory;
        this.propertyService = propertyService;
        this.bufferFactory = bufferFactory;
    }

    @Override
    public void handle(final HttpServletRequest request, final HttpServletResponse response) {
        if (attributeMapFilter == null) {
            final String receiptPolicyUuid = propertyService.getProperty("stroom.feed.receiptPolicyUuid");
            if (receiptPolicyUuid != null && !receiptPolicyUuid.isEmpty()) {
                this.attributeMapFilter = attributeMapFilterFactory.create(new DocRef("RuleSet", receiptPolicyUuid));
            }
        }

        security.asProcessingUser(() -> {
            final AttributeMap attributeMap = AttributeMapUtil.create(request);
            if (attributeMapFilter == null || attributeMapFilter.filter(attributeMap)) {
                debug("Receiving data", attributeMap);
                final String feedName = attributeMap.get(StroomHeaderArguments.FEED);

                if (feedName == null || feedName.isEmpty()) {
                    throw new StroomStreamException(StroomStatusCode.FEED_MUST_BE_SPECIFIED);
                }

                final Optional<FeedDoc> optional = feedDocCache.get(feedName);
                final String streamTypeName = optional
                        .map(FeedDoc::getStreamType)
                        .orElse(StreamTypeNames.RAW_EVENTS);

//                final String feedName = attributeMap.get(StroomHeaderArguments.FEED);
//                if (feedName == null) {
//                    throw new StroomStreamException(StroomStatusCode.FEED_IS_NOT_DEFINED);
//                }
//
//                if (!feed.isReceive()) {
//                    throw new StroomStreamException(StroomStatusCode.FEED_IS_NOT_SET_TO_RECEIVED_DATA);
//                }

                List<StreamTargetStroomStreamHandler> handlers = StreamTargetStroomStreamHandler.buildSingleHandlerList(streamStore,
                        feedDocCache, metaDataStatistics, feedName, streamTypeName);

                final byte[] buffer = bufferFactory.create();
                final StroomStreamProcessor stroomStreamProcessor = new StroomStreamProcessor(attributeMap, handlers, buffer, "DataFeedRequestHandler-" + attributeMap.get(StroomHeaderArguments.GUID));

                try {
                    stroomStreamProcessor.processRequestHeader(request);
                    stroomStreamProcessor.process(getInputStream(request), "");
                    stroomStreamProcessor.closeHandlers();
                    handlers = null;
                } finally {
                    // some kind of error
                    if (handlers != null) {
                        handlers.get(0).closeDelete();
                    }
                }
            } else {
                // Drop the data.
                debug("Dropping data", attributeMap);
            }

            // Set the response status.
            response.setStatus(StroomStatusCode.OK.getHttpCode());
            LOGGER.info("handleRequest response " + StroomStatusCode.OK);
        });
    }

    private void debug(final String message, final AttributeMap attributeMap) {
        if (LOGGER.isDebugEnabled()) {
            final List<String> keys = attributeMap.keySet().stream().sorted().collect(Collectors.toList());
            final StringBuilder sb = new StringBuilder();
            keys.forEach(key -> {
                sb.append(key);
                sb.append("=");
                sb.append(attributeMap.get(key));
                sb.append(",");
            });
            if (sb.length() > 0) {
                sb.setLength(sb.length() - 1);
            }

            LOGGER.debug(message + " (" + sb.toString() + ")");
        }
    }

    private InputStream getInputStream(final HttpServletRequest request) {
        try {
            return request.getInputStream();
        } catch (final IOException ioEx) {
            throw new StroomStreamException(StroomStatusCode.UNKNOWN_ERROR, ioEx.getMessage());
        }
    }
}