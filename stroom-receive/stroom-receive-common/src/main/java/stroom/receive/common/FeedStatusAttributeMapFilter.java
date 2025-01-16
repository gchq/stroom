package stroom.receive.common;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.StroomStatusCode;
import stroom.proxy.feed.remote.FeedStatus;
import stroom.proxy.feed.remote.GetFeedStatusRequestV2;
import stroom.proxy.feed.remote.GetFeedStatusResponse;
import stroom.util.NullSafe;
import stroom.util.cert.CertificateExtractor;

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

        final String senderDn = attributeMap.get(StandardHeaderArguments.REMOTE_DN);
        final String subjectId = NullSafe.get(senderDn, CertificateExtractor::extractCNFromDN);
        final GetFeedStatusRequestV2 request = new GetFeedStatusRequestV2(
                feedName,
                subjectId,
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
