package stroom.receive.common;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.StroomStatusCode;
import stroom.proxy.feed.remote.FeedStatus;
import stroom.proxy.feed.remote.GetFeedStatusRequestV2;
import stroom.proxy.feed.remote.GetFeedStatusResponse;
import stroom.util.NullSafe;
import stroom.util.shared.UserDesc;

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
    public boolean filter(final AttributeMap attributeMap) {
        final String feedName = attributeMap.get(StandardHeaderArguments.FEED);
        final UserDesc userDesc;
        // These two have been added by RequestAuthenticatorImpl
        final String uploadUserId = NullSafe.get(
                attributeMap.get(StandardHeaderArguments.UPLOAD_USER_ID),
                String::trim);
        if (NullSafe.isNonBlankString(uploadUserId)) {
            final String uploadUsername = NullSafe.get(
                    attributeMap.get(StandardHeaderArguments.UPLOAD_USERNAME),
                    String::trim);
            userDesc = UserDesc.builder(uploadUserId)
                    .displayName(uploadUsername)
                    .build();
        } else {
            userDesc = null;
        }

        final GetFeedStatusRequestV2 request = new GetFeedStatusRequestV2(
                feedName,
                userDesc,
                attributeMap);
        final GetFeedStatusResponse response = getFeedStatus(request);

        if (FeedStatus.Reject.equals(response.getStatus())) {
            throw new StroomStreamException(StroomStatusCode.FEED_IS_NOT_SET_TO_RECEIVE_DATA, attributeMap);
        }

        return FeedStatus.Receive.equals(response.getStatus());
    }

    private GetFeedStatusResponse getFeedStatus(final GetFeedStatusRequestV2 request) {
        final GetFeedStatusResponse response = feedStatusService.getFeedStatus(request);
        LOGGER.debug("getFeedStatus() " + request + " -> " + response);
        return response;
    }
}
