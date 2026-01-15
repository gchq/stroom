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

package stroom.core.receive;

import stroom.feed.api.FeedProperties;
import stroom.feed.shared.FeedDoc;
import stroom.feed.shared.FeedDoc.FeedStatus;
import stroom.meta.api.AttributeMap;
import stroom.proxy.StroomStatusCode;
import stroom.proxy.feed.remote.GetFeedStatusRequest;
import stroom.proxy.feed.remote.GetFeedStatusRequestV2;
import stroom.proxy.feed.remote.GetFeedStatusResponse;
import stroom.receive.common.FeedStatusService;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.security.shared.AppPermissionSet;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.UserDesc;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.regex.Pattern;

class FeedStatusServiceImpl implements FeedStatusService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FeedStatusServiceImpl.class);
    private static final AppPermissionSet REQUIRED_PERMISSION_SET = AppPermissionSet.oneOf(
            AppPermission.CHECK_RECEIPT_STATUS,
            AppPermission.STROOM_PROXY);

    private static final Pattern REPLACE_PATTERN = Pattern.compile("[^A-Z0-9_]");
    public static final String NAME_PART_DELIMITER = "-";

    private final SecurityContext securityContext;
    private final FeedProperties feedProperties;
    private final Provider<AutoContentCreationConfig> autoContentCreationConfigProvider;
    private final ContentAutoCreationService contentAutoCreationService;

    @Inject
    FeedStatusServiceImpl(final SecurityContext securityContext,
                          final FeedProperties feedProperties,
                          final Provider<AutoContentCreationConfig> autoContentCreationConfigProvider,
                          final ContentAutoCreationService contentAutoCreationService) {
        this.securityContext = securityContext;
        this.feedProperties = feedProperties;
        this.autoContentCreationConfigProvider = autoContentCreationConfigProvider;
        this.contentAutoCreationService = contentAutoCreationService;
    }

    /**
     * @deprecated Use {@link FeedStatusService#getFeedStatus(GetFeedStatusRequestV2)}
     */
    @Deprecated
    public GetFeedStatusResponse getFeedStatus(final GetFeedStatusRequest legacyRequest) {
        // Legacy API that does not require a perm check
        return securityContext.asProcessingUserResult(() -> {
            final FeedStatus feedStatus = feedProperties.getStatus(legacyRequest.getFeedName());
            return buildGetFeedStatusResponse(feedStatus);
        });
    }

    @Override
    public GetFeedStatusResponse getFeedStatus(final GetFeedStatusRequestV2 request) {
        // Can't allow anyone with an api key to check feed statues.
        try {
            return securityContext.secureResult(REQUIRED_PERMISSION_SET, () ->
                    securityContext.asProcessingUserResult(() -> {

                        final String feedName;
                        try {
                            feedName = request.getFeedName();
                        } catch (final Exception e) {
                            return new GetFeedStatusResponse(stroom.proxy.feed.remote.FeedStatus.Reject,
                                    e.getMessage(),
                                    StroomStatusCode.FEED_MUST_BE_SPECIFIED);
                        }

                        FeedStatus feedStatus = feedProperties.getStatus(feedName);
                        final UserDesc userDesc = request.getUserDesc();

                        LOGGER.debug("feedName: {}, userDesc: {}, feedStatus: {}, ",
                                feedName, userDesc, feedStatus);

                        // Feed does not exist so auto-create it if so configured
                        if (feedStatus == null) {
                            if (autoContentCreationConfigProvider.get().isEnabled()) {
                                final AttributeMap attributeMap = NullSafe.getOrElseGet(
                                        request.getAttributeMap(),
                                        AttributeMap::new,
                                        AttributeMap::new);
                                // Create the feed if it doesn't already exist
                                feedStatus = contentAutoCreationService.tryCreateFeed(
                                                feedName, userDesc, attributeMap)
                                        .map(FeedDoc::getStatus)
                                        .orElse(null);
                            } else {
                                LOGGER.debug("Content auto-creation disabled");
                            }
                        } else {
                            LOGGER.debug("Feed {} exists with status {}", feedName, feedStatus);
                        }
                        LOGGER.debug("feedName: {}, userDesc: {}, feedStatus: {}, ",
                                feedName, userDesc, feedStatus);
                        return buildGetFeedStatusResponse(feedStatus);
                    }));
        } catch (final Exception e) {
            LOGGER.debug(() -> LogUtil.message("Error getting feed status: {}", LogUtil.exceptionMessage(e)), e);
            throw e;
        }
    }

    private static GetFeedStatusResponse buildGetFeedStatusResponse(final FeedStatus feedStatus) {
        return switch (feedStatus) {
            case null -> GetFeedStatusResponse.createFeedIsNotDefinedResponse();
            case RECEIVE -> GetFeedStatusResponse.createOKReceiveResponse();
            case REJECT -> GetFeedStatusResponse.createFeedNotSetToReceiveDataResponse();
            case DROP -> GetFeedStatusResponse.createOKDropResponse();
        };
        // All OK so far

        // TODO : REPLACE THIS WITH A POLICY BASED DECISION.

        // Feed exists - now check the folder
//        final Folder folder = folderService.load(feed.getFolder());
//        final GroupAuthorisation groupAuthorisation = folder.getComputerAuthorisation();
//
//        if (GroupAuthorisation.REQUIRED.equals(groupAuthorisation)
//                || GroupAuthorisation.RESTRICTED.equals(groupAuthorisation)) {
//            SecurityContext securityContext = null;
//
//            if (request.getSenderDn( != null && !request.getSenderDn(.isEmpty())) {
//                final String cn = CertificateUtil.extractCNFromDN(request.getSenderDn());
//                if (cn != null && !cn.isEmpty()) {
//                    securityContext = securityContextFactory.forUser(cn);
//                }
//            }
//
//            if (securityContext == null) {
//                return GetFeedStatusResponse.createCertificateRequiredResponse();
//            }
//
//            if (GroupAuthorisation.RESTRICTED.equals(groupAuthorisation)) {
//                // Check that the user identified by the cn is allowed to update
//                // the feed.
//
//                if (!securityContext.hasDocumentPermission(Feed.DOCUMENT_TYPE,
//                feed.getUuid(), DocumentPermissionNames.UPDATE)) {
//                    return GetFeedStatusResponse.createCertificateNotAuthorisedResponse();
//                }
//            }
//        }
    }
}
