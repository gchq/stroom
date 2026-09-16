/*
 * Copyright 2026 Crown Copyright
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

package stroom.proxy.app.handler;

import stroom.proxy.app.DownstreamHostConfig;
import stroom.proxy.app.handler.RemoteFeedStatusClient.FeedStatusUnavailableException;
import stroom.proxy.feed.remote.GetFeedStatusRequestV2;
import stroom.receive.common.ReceiveDataConfig;
import stroom.security.api.UserIdentityFactory;
import stroom.util.jersey.JerseyClientFactory;
import stroom.util.jersey.JerseyClientName;

import jakarta.inject.Provider;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@code callFeedStatus} used to swallow every failure and substitute
 * {@code GetFeedStatusResponse.createOKReceiveResponse()} — "if we can't get a feed status response
 * then we will assume ok". That made the method incapable of failing, which made
 * {@code RemoteFeedStatusService}'s last-good-response path and, more seriously, the operator's
 * configured {@code fallbackReceiveAction} unreachable. Someone who set {@code REJECT} to fail
 * closed during a downstream outage silently got {@code Receive}.
 * <p>
 * These pin the one property that matters: <strong>this client never decides to admit data.</strong>
 * Deciding is the caller's job.
 * </p>
 */
class TestRemoteFeedStatusClient {

    @Test
    void testAFailedCallThrowsRatherThanAdmittingTheData() {
        final JerseyClientFactory jerseyClientFactory = Mockito.mock(JerseyClientFactory.class);
        Mockito.when(jerseyClientFactory.createWebTarget(
                        Mockito.any(JerseyClientName.class), Mockito.anyString()))
                .thenThrow(new IllegalStateException("downstream unreachable"));

        final RemoteFeedStatusClient client = createClient(jerseyClientFactory, true);

        assertThatThrownBy(() -> client.callFeedStatus(createRequest()))
                .isInstanceOf(FeedStatusUnavailableException.class);
    }

    /**
     * The other half of the same property: no reachable downstream is still not a reason to admit
     * data. The service short-circuits before reaching here, but the client must not answer
     * permissively if it is reached.
     */
    @Test
    void testNoReachableDownstreamThrowsRatherThanAdmittingTheData() {
        final RemoteFeedStatusClient client = createClient(
                Mockito.mock(JerseyClientFactory.class), false);

        assertThatThrownBy(() -> client.callFeedStatus(createRequest()))
                .isInstanceOf(FeedStatusUnavailableException.class);
    }

    private RemoteFeedStatusClient createClient(final JerseyClientFactory jerseyClientFactory,
                                                final boolean downstreamEnabled) {
        final DownstreamHostConfig downstreamHostConfig = DownstreamHostConfig.builder()
                .withEnabled(downstreamEnabled)
                .withHostname("downstream.example.com")
                .build();
        final Provider<DownstreamHostConfig> downstreamHostConfigProvider = () -> downstreamHostConfig;
        final Provider<FeedStatusConfig> feedStatusConfigProvider = FeedStatusConfig::new;
        final Provider<ReceiveDataConfig> receiveDataConfigProvider = ReceiveDataConfig::new;

        return new RemoteFeedStatusClient(
                jerseyClientFactory,
                Mockito.mock(UserIdentityFactory.class),
                downstreamHostConfigProvider,
                feedStatusConfigProvider,
                receiveDataConfigProvider);
    }

    private GetFeedStatusRequestV2 createRequest() {
        return new GetFeedStatusRequestV2("TEST_FEED", null, java.util.Map.of());
    }
}
