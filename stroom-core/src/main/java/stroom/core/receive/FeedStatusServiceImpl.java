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
 *
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
import stroom.receive.common.AutoContentCreationConfig;
import stroom.receive.common.FeedStatusService;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.util.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.regex.Pattern;

class FeedStatusServiceImpl implements FeedStatusService {

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
            FeedStatus feedStatus = feedProperties.getStatus(legacyRequest.getFeedName());
            return buildGetFeedStatusResponse(feedStatus);
        });
    }

    @Override
    public GetFeedStatusResponse getFeedStatus(final GetFeedStatusRequestV2 request) {
        // Can't allow anyone with an api key to check feed statues.
        return securityContext.secureResult(AppPermission.CHECK_RECEIPT_STATUS, () ->
                securityContext.asProcessingUserResult(() -> {

                    final String feedName;
                    try {
                        feedName = request.getFeedName();
                    } catch (Exception e) {
                        return new GetFeedStatusResponse(stroom.proxy.feed.remote.FeedStatus.Reject,
                                e.getMessage(),
                                StroomStatusCode.FEED_MUST_BE_SPECIFIED);
                    }

                    FeedStatus feedStatus = feedProperties.getStatus(feedName);

                    // Feed does not exist so auto-create it if so configured
                    if (feedStatus == null
                        && autoContentCreationConfigProvider.get().isEnabled()) {

                        final AttributeMap attributeMap = NullSafe.get(
                                request.getAttributeMap(),
                                reqAttrMap -> {
                                    final AttributeMap attrMap = new AttributeMap();
                                    attrMap.putAll(reqAttrMap);
                                    return attrMap;
                                });

                        feedStatus = contentAutoCreationService.createFeed(
                                        feedName,
                                        request.getSubjectId(),
                                        attributeMap)
                                .map(FeedDoc::getStatus)
                                .orElse(null);
                    }
                    return buildGetFeedStatusResponse(feedStatus);
                }));
    }

    private static GetFeedStatusResponse buildGetFeedStatusResponse(final FeedStatus feedStatus) {
        if (feedStatus == null) {
            return GetFeedStatusResponse.createFeedIsNotDefinedResponse();
        } else {
            if (FeedStatus.REJECT.equals(feedStatus)) {
                return GetFeedStatusResponse.createFeedNotSetToReceiveDataResponse();
            }
            if (FeedStatus.DROP.equals(feedStatus)) {
                return GetFeedStatusResponse.createOKDropResponse();
            }
        }

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
//
        return GetFeedStatusResponse.createOKReceiveResponse();
    }
}
