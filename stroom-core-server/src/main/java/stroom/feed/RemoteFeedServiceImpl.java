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

package stroom.feed;

import stroom.feed.shared.Feed;
import stroom.feed.shared.Feed.FeedStatus;
import stroom.security.Security;

import javax.inject.Inject;
import javax.inject.Named;

class RemoteFeedServiceImpl implements RemoteFeedService {
    private final Security security;
    private final FeedService feedService;

    @Inject
    RemoteFeedServiceImpl(final Security security, @Named("cachedFeedService") final FeedService feedService) {
        this.security = security;
        this.feedService = feedService;
    }

    @Override
    public GetFeedStatusResponse getFeedStatus(final GetFeedStatusRequest request) {
        return security.asProcessingUserResult(() -> {
            final Feed feed = feedService.loadByName(request.getFeedName());

            if (feed == null) {
                return GetFeedStatusResponse.createFeedIsNotDefinedResponse();
            } else {
                if (FeedStatus.REJECT.equals(feed.getStatus())) {
                    return GetFeedStatusResponse.createFeedNotSetToReceiveDataResponse();
                }
                if (FeedStatus.DROP.equals(feed.getStatus())) {
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
//                if (!securityContext.hasDocumentPermission(Feed.ENTITY_TYPE, feed.getUuid(), DocumentPermissionNames.UPDATE)) {
//                    return GetFeedStatusResponse.createCertificateNotAuthorisedResponse();
//                }
//            }
//        }
//
            return GetFeedStatusResponse.createOKRecieveResponse();

        });
    }
}
