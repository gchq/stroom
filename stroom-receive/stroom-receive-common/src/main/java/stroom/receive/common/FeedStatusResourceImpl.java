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

import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.proxy.feed.remote.GetFeedStatusRequest;
import stroom.proxy.feed.remote.GetFeedStatusResponse;
import stroom.util.HasHealthCheck;

import com.codahale.metrics.annotation.Timed;
import com.codahale.metrics.health.HealthCheck.Result;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

/**
 * @deprecated Use {@link FeedStatusResourceV2Impl}
 */
@Deprecated
@AutoLogged(OperationType.UNLOGGED)
public class FeedStatusResourceImpl implements FeedStatusResource, HasHealthCheck {

    private final Provider<FeedStatusService> feedStatusServiceProvider;

    @Inject
    public FeedStatusResourceImpl(final Provider<FeedStatusService> feedStatusServiceProvider) {
        this.feedStatusServiceProvider = feedStatusServiceProvider;
    }

    @Timed
    @Override
    // TODO This should really be a GET with the feedName and senderDn as params
    public GetFeedStatusResponse getFeedStatus(final GetFeedStatusRequest legacyRequest) {
        return feedStatusServiceProvider.get().getFeedStatus(legacyRequest);
    }

    @Override
    public Result getHealth() {
        return Result.healthy();
    }
}
