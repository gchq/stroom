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

package stroom.datafeed.server;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import stroom.feed.shared.Feed;
import stroom.feed.shared.FeedService;
import stroom.security.Insecure;
import stroom.security.SecurityContext;
import stroom.internalstatistics.MetaDataStatistic;
import stroom.streamstore.server.StreamStore;
import stroom.streamtask.server.StreamTargetStroomStreamHandler;
import stroom.util.task.ServerTask;
import stroom.util.thread.ThreadLocalBuffer;
import stroom.util.zip.HeaderMap;
import stroom.util.zip.StroomHeaderArguments;
import stroom.util.zip.StroomStatusCode;
import stroom.util.zip.StroomStreamException;
import stroom.util.zip.StroomStreamProcessor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * <p>
 * Handle the incoming requests and stream them to disk checking a few things.
 * </p>
 */
@Component("dataFeedRequest")
@Scope("request")
public class DefaultDataFeedRequest implements DataFeedRequest {
    @Resource
    private SecurityContext securityContext;
    @Resource
    private HttpServletRequest request;
    @Resource
    private StreamStore streamStore;
    @Resource(name = "cachedFeedService")
    private FeedService feedService;
    @Resource(name = "requestThreadLocalBuffer")
    private ThreadLocalBuffer requestThreadLocalBuffer;
    @Resource
    private MetaDataStatistic metaDataStatistics;
    @Resource
    private HeaderMap headerMap;

    /**
     * Read the file in.
     *
     * @return HTTP response code
     */
    @Override
    @Insecure
    public void processRequest() {
        securityContext.pushUser(ServerTask.INTERNAL_PROCESSING_USER_TOKEN);
        try {
            final String feedName = headerMap.get(StroomHeaderArguments.FEED);

            if (!StringUtils.hasText(feedName)) {
                throw new StroomStreamException(StroomStatusCode.FEED_MUST_BE_SPECIFIED);
            }

            final Feed feed = feedService.loadByName(headerMap.get(StroomHeaderArguments.FEED));

            if (feed == null) {
                throw new StroomStreamException(StroomStatusCode.FEED_IS_NOT_DEFINED);
            }

            if (!feed.isReceive()) {
                throw new StroomStreamException(StroomStatusCode.FEED_IS_NOT_SET_TO_RECEIVED_DATA);
            }

            List<StreamTargetStroomStreamHandler> handlers = StreamTargetStroomStreamHandler.buildSingleHandlerList(streamStore,
                    feedService, metaDataStatistics, feed, feed.getStreamType());

            StroomStreamProcessor stroomStreamProcessor = new StroomStreamProcessor(headerMap, handlers,
                    requestThreadLocalBuffer.getBuffer(),
                    "DefaultDataFeedRequest-" + headerMap.get(StroomHeaderArguments.GUID));

            try {
                stroomStreamProcessor.processRequestHeader(request);
                stroomStreamProcessor.process(getInputStream(), "");

                stroomStreamProcessor.closeHandlers();
                stroomStreamProcessor = null;
                handlers = null;
            } finally {
                // some kind of error
                if (handlers != null) {
                    handlers.get(0).closeDelete();
                }
            }
        } finally {
            securityContext.popUser();
        }
    }

    private InputStream getInputStream() {
        try {
            return request.getInputStream();
        } catch (final IOException ioEx) {
            throw new StroomStreamException(StroomStatusCode.UNKNOWN_ERROR, ioEx.getMessage());
        }
    }

    /**
     * @param request setter
     */
    public void setRequest(final HttpServletRequest request) {
        this.request = request;
    }

    /**
     * @param feedService setter
     */
    public void setFeedService(final FeedService feedService) {
        this.feedService = feedService;
    }

    /**
     * @param streamStore setter
     */
    public void setStreamStore(final StreamStore streamStore) {
        this.streamStore = streamStore;
    }

}
