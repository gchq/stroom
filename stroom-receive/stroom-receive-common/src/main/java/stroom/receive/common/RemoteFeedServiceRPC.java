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

import stroom.proxy.feed.remote.GetFeedStatusRequest;
import stroom.proxy.feed.remote.GetFeedStatusResponse;
import stroom.util.shared.IsServlet;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.Unauthenticated;

import jakarta.inject.Inject;

import java.util.Set;

@Unauthenticated
// TODO: 31/10/2023 jakarta - Hessian uses javax.servlet so we can't use it (maybe unless we shade it)
//public class RemoteFeedServiceRPC extends HessianServlet implements IsServlet {
public class RemoteFeedServiceRPC implements IsServlet {

    private static final Set<String> PATH_SPECS = Set.of(
            ResourcePaths.addLegacyUnauthenticatedServletPrefix("/remoting/remotefeedservice.rpc"));

    private final FeedStatusService feedStatusService;

    @Inject
    RemoteFeedServiceRPC(final FeedStatusService feedStatusService) {
        this.feedStatusService = feedStatusService;
    }

    public GetFeedStatusResponse getFeedStatus(final GetFeedStatusRequest request) {
        return feedStatusService.getFeedStatus(request);
    }

    /**
     * @return The part of the path that will be in addition to any base path,
     * e.g. "/datafeed".
     */
    @Override
    public Set<String> getPathSpecs() {
        return PATH_SPECS;
    }
}
