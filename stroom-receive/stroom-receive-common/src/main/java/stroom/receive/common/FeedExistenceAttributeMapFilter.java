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
import stroom.proxy.feed.remote.GetFeedStatusRequestV2;
import stroom.proxy.feed.remote.GetFeedStatusResponse;
import stroom.util.shared.NullSafe;
import stroom.util.shared.UserDesc;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Checks whether the Feed in the {@link AttributeMap} exists or not.
 * If it does not exist then the filter will return false.
 * If auto content creation is enabled on stroom, then when the check is performed,
 * the feed will be auto-created (if various conditions for that are met) and this
 * filter will then return true.
 * <p>
 * It is intended to be used alongside the {@link DataReceiptPolicyAttributeMapFilter}
 * and should not be used with {@link FeedStatusAttributeMapFilter} (as it is doing similar
 * work).
 * </p>
 */
public class FeedExistenceAttributeMapFilter implements AttributeMapFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(FeedExistenceAttributeMapFilter.class);

    private final Provider<FeedStatusService> feedStatusServiceProvider;

    @Inject
    public FeedExistenceAttributeMapFilter(final Provider<FeedStatusService> feedStatusServiceProvider) {
        this.feedStatusServiceProvider = feedStatusServiceProvider;
    }

    @Override
    public boolean filter(final AttributeMap attributeMap) {
        final String feedName = NullSafe.string(attributeMap.get(StandardHeaderArguments.FEED));
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

        // Slightly abusing the feedStatus service for this. We just ignore the
        // status if found. Only care if feedDoc is there or not.
        final GetFeedStatusRequestV2 request = new GetFeedStatusRequestV2(
                feedName,
                userDesc,
                attributeMap);
        final GetFeedStatusResponse response = getFeedStatus(request);
        final StroomStatusCode stroomStatusCode = response.getStroomStatusCode();
        if (stroomStatusCode == StroomStatusCode.FEED_IS_NOT_DEFINED) {
            LOGGER.debug("filter() - Throwing StroomStreamException for feed '{}', stroomStatusCode: {}",
                    feedName, stroomStatusCode);
            throw new StroomStreamException(stroomStatusCode, attributeMap);
        }
        // Don't care what the feed status on the feed is
        return true;
//        final boolean result = response.getStroomStatusCode() != StroomStatusCode.FEED_IS_NOT_DEFINED;
//
//        LOGGER.debug("filter() - Returning {} for feed '{}', stroomStatusCode: {}",
//                result, feedName, stroomStatusCode);
//        return result;
    }

    private GetFeedStatusResponse getFeedStatus(final GetFeedStatusRequestV2 request) {
        final GetFeedStatusResponse response = feedStatusServiceProvider.get()
                .getFeedStatus(request);
        LOGGER.debug("getFeedStatus() " + request + " -> " + response);
        return response;
    }
}
