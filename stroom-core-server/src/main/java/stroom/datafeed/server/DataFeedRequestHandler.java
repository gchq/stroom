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

package stroom.datafeed.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import stroom.feed.MetaMap;
import stroom.feed.MetaMapFactory;
import stroom.feed.StroomHeaderArguments;
import stroom.feed.StroomStatusCode;
import stroom.feed.StroomStreamException;
import stroom.feed.server.FeedService;
import stroom.feed.shared.Feed;
import stroom.internalstatistics.MetaDataStatistic;
import stroom.properties.StroomPropertyService;
import stroom.proxy.repo.StroomStreamProcessor;
import stroom.security.Insecure;
import stroom.security.SecurityContext;
import stroom.security.SecurityHelper;
import stroom.streamstore.server.StreamStore;
import stroom.streamtask.server.StreamTargetStroomStreamHandler;
import stroom.util.thread.BufferFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>
 * Handle the incoming requests and stream them to disk checking a few things.
 * </p>
 */
public class DataFeedRequestHandler implements RequestHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(DataFeedRequestHandler.class);

    private final SecurityContext securityContext;
    private final StreamStore streamStore;
    private final FeedService feedService;
    private final MetaDataStatistic metaDataStatistics;
    private final MetaMapFilterFactory metaMapFilterFactory;
    private final StroomPropertyService stroomPropertyService;

    private volatile MetaMapFilter metaMapFilter;

    @Inject
    public DataFeedRequestHandler(final SecurityContext securityContext,
                                  final StreamStore streamStore,
                                  @Named("cachedFeedService") final FeedService feedService,
                                  final MetaDataStatistic metaDataStatistics,
                                  final MetaMapFilterFactory metaMapFilterFactory,
                                  final StroomPropertyService stroomPropertyService) {
        this.securityContext = securityContext;
        this.streamStore = streamStore;
        this.feedService = feedService;
        this.metaDataStatistics = metaDataStatistics;
        this.metaMapFilterFactory = metaMapFilterFactory;
        this.stroomPropertyService = stroomPropertyService;
    }

    @Override
    @Insecure
    public void handle(final HttpServletRequest request, final HttpServletResponse response) {
        if (metaMapFilter == null) {
            final String receiptPolicyUuid = stroomPropertyService.getProperty("stroom.feed.receiptPolicyUuid");
            if (receiptPolicyUuid != null && receiptPolicyUuid.length() > 0) {
                this.metaMapFilter = metaMapFilterFactory.create(receiptPolicyUuid);
            }
        }

        try (SecurityHelper securityHelper = SecurityHelper.processingUser(securityContext)) {
            final MetaMap metaMap = MetaMapFactory.create(request);
            if (metaMapFilter == null || metaMapFilter.filter(metaMap)) {
                debug("Receiving data", metaMap);
                final String feedName = metaMap.get(StroomHeaderArguments.FEED);

                if (!StringUtils.hasText(feedName)) {
                    throw new StroomStreamException(StroomStatusCode.FEED_MUST_BE_SPECIFIED);
                }

                final Feed feed = feedService.loadByName(metaMap.get(StroomHeaderArguments.FEED));

                if (feed == null) {
                    throw new StroomStreamException(StroomStatusCode.FEED_IS_NOT_DEFINED);
                }

                if (!feed.isReceive()) {
                    throw new StroomStreamException(StroomStatusCode.FEED_IS_NOT_SET_TO_RECEIVED_DATA);
                }

                List<StreamTargetStroomStreamHandler> handlers = StreamTargetStroomStreamHandler.buildSingleHandlerList(streamStore,
                        feedService, metaDataStatistics, feed, feed.getStreamType());

                final byte[] buffer = BufferFactory.create();
                final StroomStreamProcessor stroomStreamProcessor = new StroomStreamProcessor(metaMap, handlers, buffer, "DataFeedRequestHandler-" + metaMap.get(StroomHeaderArguments.GUID));

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
                debug("Dropping data", metaMap);
            }

            // Set the response status.
            response.setStatus(StroomStatusCode.OK.getHttpCode());
            LOGGER.info("handleRequest response " + StroomStatusCode.OK);
        }
    }

    private void debug(final String message, final MetaMap metaMap) {
        if (LOGGER.isDebugEnabled()) {
            final List<String> keys = metaMap.keySet().stream().sorted().collect(Collectors.toList());
            final StringBuilder sb = new StringBuilder();
            keys.forEach(key -> {
                sb.append(key);
                sb.append("=");
                sb.append(metaMap.get(key));
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