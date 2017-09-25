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
 */

package stroom.datafeed.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import stroom.feed.MetaMap;
import stroom.feed.shared.Feed;
import stroom.feed.shared.FeedService;
import stroom.internalstatistics.MetaDataStatistic;
import stroom.policy.shared.DataReceiptAction;
import stroom.proxy.repo.StroomStreamProcessor;
import stroom.security.Insecure;
import stroom.security.SecurityContext;
import stroom.streamstore.server.StreamStore;
import stroom.streamtask.server.StreamTargetStroomStreamHandler;
import stroom.util.task.ServerTask;
import stroom.util.thread.ThreadLocalBuffer;
import stroom.feed.StroomHeaderArguments;
import stroom.feed.StroomStatusCode;
import stroom.feed.StroomStreamException;

import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>
 * Handle the incoming requests and stream them to disk checking a few things.
 * </p>
 */
@Component("dataFeedRequest")
@Scope("request")
public class DefaultDataFeedRequest implements DataFeedRequest {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultDataFeedRequest.class);

    private final SecurityContext securityContext;
    private final HttpServletRequest request;
    private final StreamStore streamStore;
    private final FeedService feedService;
    private final ThreadLocalBuffer requestThreadLocalBuffer;
    private final MetaDataStatistic metaDataStatistics;
    private final MetaMap metaMap;
    private final DataReceiptPolicyChecker dataReceiptPolicyChecker;

    @Inject
    DefaultDataFeedRequest(final SecurityContext securityContext,
                           final HttpServletRequest request,
                           final StreamStore streamStore,
                           @Named("cachedFeedService") final FeedService feedService,
                           @Named("requestThreadLocalBuffer") final ThreadLocalBuffer requestThreadLocalBuffer,
                           final MetaDataStatistic metaDataStatistics,
                           final MetaMap metaMap,
                           final DataReceiptPolicyChecker dataReceiptPolicyChecker) {
        this.securityContext = securityContext;
        this.request = request;
        this.streamStore = streamStore;
        this.feedService = feedService;
        this.requestThreadLocalBuffer = requestThreadLocalBuffer;
        this.metaDataStatistics = metaDataStatistics;
        this.metaMap = metaMap;
        this.dataReceiptPolicyChecker = dataReceiptPolicyChecker;
    }

    @Override
    @Insecure
    public void processRequest() {
        securityContext.pushUser(ServerTask.INTERNAL_PROCESSING_USER_TOKEN);
        try {
            // We need to examine the meta map and ensure we aren't dropping or rejecting this data.
            final DataReceiptAction dataReceiptAction = dataReceiptPolicyChecker.check(metaMap);

            if (DataReceiptAction.REJECT.equals(dataReceiptAction)) {
                debug("Rejecting data", metaMap);
                throw new StroomStreamException(StroomStatusCode.RECEIPT_POLICY_SET_TO_REJECT_DATA);

            } else if (DataReceiptAction.RECEIVE.equals(dataReceiptAction)) {
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

                final StroomStreamProcessor stroomStreamProcessor = new StroomStreamProcessor(metaMap, handlers,
                        requestThreadLocalBuffer.getBuffer(),
                        "DefaultDataFeedRequest-" + metaMap.get(StroomHeaderArguments.GUID));

                try {
                    stroomStreamProcessor.processRequestHeader(request);
                    stroomStreamProcessor.process(getInputStream(), "");
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
        } finally {
            securityContext.popUser();
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

    private InputStream getInputStream() {
        try {
            return request.getInputStream();
        } catch (final IOException ioEx) {
            throw new StroomStreamException(StroomStatusCode.UNKNOWN_ERROR, ioEx.getMessage());
        }
    }
}