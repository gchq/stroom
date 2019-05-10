package stroom.datafeed.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import stroom.feed.MetaMap;
import stroom.feed.StroomHeaderArguments;
import stroom.feed.StroomStreamException;
import stroom.feed.server.FeedStatusService;
import stroom.proxy.StroomStatusCode;
import stroom.proxy.feed.remote.FeedStatus;
import stroom.proxy.feed.remote.GetFeedStatusRequest;
import stroom.proxy.feed.remote.GetFeedStatusResponse;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class FeedStatusMetaMapFilter implements MetaMapFilter {
    private static Logger LOGGER = LoggerFactory.getLogger(FeedStatusMetaMapFilter.class);

    private final FeedStatusService feedStatusService;

    public FeedStatusMetaMapFilter(final FeedStatusService feedStatusService) {
        this.feedStatusService = feedStatusService;
    }

    @Override
    public boolean filter(final MetaMap metaMap) {
        final String feedName = metaMap.get(StroomHeaderArguments.FEED);
        if (feedName == null || feedName.trim().isEmpty()) {
            throw new StroomStreamException(StroomStatusCode.FEED_MUST_BE_SPECIFIED);
        }

        final String senderDn = metaMap.get(StroomHeaderArguments.REMOTE_DN);
        final GetFeedStatusRequest request = new GetFeedStatusRequest(feedName, senderDn);
        final GetFeedStatusResponse response = getFeedStatus(request);

        if (FeedStatus.Reject.equals(response.getStatus())) {
            throw new StroomStreamException(StroomStatusCode.FEED_IS_NOT_SET_TO_RECEIVED_DATA);
        }

        return FeedStatus.Receive.equals(response.getStatus());
    }

    private GetFeedStatusResponse getFeedStatus(final GetFeedStatusRequest request) {
        final GetFeedStatusResponse response = feedStatusService.getFeedStatus(request);
        LOGGER.debug("getFeedStatus() " + request + " -> " + response);
        return response;
    }
}
