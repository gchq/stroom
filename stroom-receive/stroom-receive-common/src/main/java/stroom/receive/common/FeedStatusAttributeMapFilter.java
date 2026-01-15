/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.receive.common;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.StroomStatusCode;
import stroom.proxy.feed.remote.FeedStatus;
import stroom.proxy.feed.remote.GetFeedStatusRequestV2;
import stroom.proxy.feed.remote.GetFeedStatusResponse;
import stroom.util.shared.NullSafe;
import stroom.util.shared.UserDesc;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gets the feed status from the {@link stroom.feed.shared.FeedDoc}.
 * <ul>
 *     <li>Receive -> return true</li>
 *     <li>Reject -> throw {@link StroomStreamException}</li>
 *     <li>Drop -> return false</li>
 * </ul>
 * <p>
 * If the {@link stroom.feed.shared.FeedDoc} does not exist then the filter will throw
 * a {@link StroomStreamException}.
 * </p>
 * <p>
 * If auto content creation is enabled on stroom, then when the check is performed,
 * the feed will be auto-created (if various conditions for that are met) and this
 * filter will then return true.
 * </p>
 */
public class FeedStatusAttributeMapFilter implements AttributeMapFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(FeedStatusAttributeMapFilter.class);

    private final Provider<FeedStatusService> feedStatusServiceProvider;
    private final ReceiveActionMetricsRecorder receiveActionMetricsRecorder;

    @Inject
    public FeedStatusAttributeMapFilter(final Provider<FeedStatusService> feedStatusServiceProvider,
                                        final ReceiveActionMetricsRecorder receiveActionMetricsRecorder) {
        this.feedStatusServiceProvider = feedStatusServiceProvider;
        this.receiveActionMetricsRecorder = receiveActionMetricsRecorder;
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
                receiveActionMetricsRecorder.record(feedStatus);
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
        receiveActionMetricsRecorder.record(feedStatus);

        LOGGER.debug("Returning {} for feed '{}', feedStatus: {}", result, feedName, feedStatus);
        return result;
    }

    private GetFeedStatusResponse getFeedStatus(final GetFeedStatusRequestV2 request) {
        // Proxy and stroom each have a different FeedStatusService impl bound.
        final GetFeedStatusResponse response = feedStatusServiceProvider.get()
                .getFeedStatus(request);
        LOGGER.debug("getFeedStatus() " + request + " -> " + response);
        return response;
    }
}
