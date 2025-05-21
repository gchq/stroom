package stroom.receive.common;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.StroomStatusCode;
import stroom.proxy.feed.remote.FeedStatus;
import stroom.proxy.feed.remote.GetFeedStatusRequestV2;
import stroom.proxy.feed.remote.GetFeedStatusResponse;
import stroom.util.metrics.Metrics;
import stroom.util.shared.NullSafe;
import stroom.util.shared.UserDesc;

import com.codahale.metrics.Meter;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumMap;

@Singleton
public class FeedStatusAttributeMapFilter implements AttributeMapFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(FeedStatusAttributeMapFilter.class);

    private final Provider<FeedStatusService> feedStatusServiceProvider;
    private final EnumMap<FeedStatus, Meter> feedStatusMeters;

    @Inject
    public FeedStatusAttributeMapFilter(final Provider<FeedStatusService> feedStatusServiceProvider,
                                        final Metrics metrics) {
        this.feedStatusServiceProvider = feedStatusServiceProvider;
        feedStatusMeters = new EnumMap<>(FeedStatus.class);
        for (final FeedStatus feedStatus : FeedStatus.values()) {
            final Meter meter = metrics.registrationBuilder(getClass())
                    .addNamePart("feedStatus")
                    .addNamePart(feedStatus.name())
                    .meter()
                    .createAndRegister();
            feedStatusMeters.put(feedStatus, meter);
        }
    }

    @Override
    public boolean filter(final AttributeMap attributeMap) {
        final String feedName = NullSafe.get(
                attributeMap.get(StandardHeaderArguments.FEED),
                String::trim);
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

        final FeedStatus feedStatus = response.getStatus();
        final boolean result = switch (feedStatus) {
            case Receive -> true;
            case Drop -> false;
            case Reject -> {
                NullSafe.consume(feedStatusMeters.get(feedStatus), Meter::mark);
                throw new StroomStreamException(
                        StroomStatusCode.FEED_IS_NOT_SET_TO_RECEIVE_DATA, attributeMap);
            }
            //noinspection UnnecessaryDefault
            default -> {
                LOGGER.error("Unexpected feed status {} for request {}, treating as RECEIVE.",
                        response.getStatus(), request);
                yield true;
            }
        };
        NullSafe.consume(feedStatusMeters.get(feedStatus), Meter::mark);

        LOGGER.debug("Returning {} for feed '{}', feedStatus: {}", result, feedName, feedStatus);
        return result;
    }

    private GetFeedStatusResponse getFeedStatus(final GetFeedStatusRequestV2 request) {
        final GetFeedStatusResponse response = feedStatusServiceProvider.get()
                .getFeedStatus(request);
        LOGGER.debug("getFeedStatus() " + request + " -> " + response);
        return response;
    }
}
