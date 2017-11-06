/*
 * Copyright 2016 Crown Copyright
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

package stroom.feed.server;

import stroom.entity.shared.FolderService;
import stroom.feed.remote.GetFeedStatusRequest;
import stroom.feed.remote.GetFeedStatusResponse;
import stroom.feed.remote.RemoteFeedService;
import stroom.feed.shared.Feed;
import stroom.feed.shared.Feed.FeedStatus;
import stroom.feed.shared.FeedService;
import stroom.pool.SecurityHelper;
import stroom.security.Insecure;
import stroom.security.SecurityContext;
import stroom.util.task.ServerTask;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component("remoteFeedService")
public class RemoteFeedServiceImpl implements RemoteFeedService {
    @Resource
    private SecurityContext securityContext;
    @Resource(name = "cachedFeedService")
    private FeedService feedService;
    @Resource(name = "cachedFolderService")
    private FolderService folderService;

    @Override
    @Insecure
    public GetFeedStatusResponse getFeedStatus(final GetFeedStatusRequest request) {
        try (SecurityHelper securityHelper = SecurityHelper.elevate(securityContext)) {
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
//            if (StringUtils.hasText(request.getSenderDn())) {
//                final String cn = CertificateUtil.extractCNFromDN(request.getSenderDn());
//                if (StringUtils.hasText(cn)) {
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

        }
    }
}
