package stroom.receive.common;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.StroomStatusCode;
import stroom.proxy.feed.remote.FeedStatus;
import stroom.proxy.feed.remote.GetFeedStatusRequest;
import stroom.proxy.feed.remote.GetFeedStatusResponse;
import stroom.security.api.UserIdentity;

import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FeedStatusAttributeMapFilter implements AttributeMapFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(FeedStatusAttributeMapFilter.class);

    private final FeedStatusService feedStatusService;

    @Inject
    public FeedStatusAttributeMapFilter(final FeedStatusService feedStatusService) {
        this.feedStatusService = feedStatusService;
    }

    @Override
    public boolean filter(final AttributeMap attributeMap, final UserIdentity userIdentity) {
        final String feedName = attributeMap.get(StandardHeaderArguments.FEED);

        final String senderDn = attributeMap.get(StandardHeaderArguments.REMOTE_DN);
        final GetFeedStatusRequest request = new GetFeedStatusRequest(feedName, senderDn);
        final GetFeedStatusResponse response = getFeedStatus(request);

        if (FeedStatus.Reject.equals(response.getStatus())) {
            throw new StroomStreamException(StroomStatusCode.FEED_IS_NOT_SET_TO_RECEIVED_DATA, attributeMap);
        }

        return FeedStatus.Receive.equals(response.getStatus());
    }

    private GetFeedStatusResponse getFeedStatus(final GetFeedStatusRequest request) {
        final GetFeedStatusResponse response = feedStatusService.getFeedStatus(request);
        LOGGER.debug("getFeedStatus() " + request + " -> " + response);
        return response;
    }
}
