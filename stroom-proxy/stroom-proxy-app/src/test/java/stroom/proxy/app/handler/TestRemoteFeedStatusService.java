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

import stroom.cache.impl.CacheManagerImpl;
import stroom.proxy.feed.remote.FeedStatus;
import stroom.proxy.feed.remote.GetFeedStatusRequestV2;
import stroom.proxy.feed.remote.GetFeedStatusResponse;
import stroom.receive.common.GetFeedStatusRequestAdapter;
import stroom.receive.common.ReceiveDataConfig;
import stroom.security.mock.MockCommonSecurityContext;
import stroom.test.common.MockMetrics;
import stroom.util.metrics.Metrics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestRemoteFeedStatusService {

    private static final GetFeedStatusRequestV2 REQUEST = new GetFeedStatusRequestV2("FEED_A", null, Map.of());

    private RemoteFeedStatusClient client;
    private RemoteFeedStatusService service;

    @BeforeEach
    void setUp() {
        client = Mockito.mock(RemoteFeedStatusClient.class);
        Mockito.when(client.isDownstreamEnabled()).thenReturn(true);
        final Metrics metrics = new MockMetrics();
        service = new RemoteFeedStatusService(
                FeedStatusConfig::new,
                new CacheManagerImpl(() -> metrics),
                () -> ReceiveDataConfig.builder().build(),
                MockCommonSecurityContext::getInstance,
                client,
                Mockito.mock(GetFeedStatusRequestAdapter.class));
    }

    @Test
    void testTheFirstRequestLoadsAndLaterOnesReadWhatIsCached() {
        final GetFeedStatusResponse drop = GetFeedStatusResponse.createOKDropResponse();
        Mockito.when(client.callFeedStatus(ArgumentMatchers.any())).thenReturn(drop);

        assertThat(service.getFeedStatus(REQUEST)).isSameAs(drop);
        assertThat(service.getFeedStatus(REQUEST)).isSameAs(drop);

        Mockito.verify(client, Mockito.times(1)).callFeedStatus(ArgumentMatchers.any());
    }

    @Test
    void testTheScheduleRefreshesOnlyWhatIsStale() {
        final GetFeedStatusResponse first = GetFeedStatusResponse.createOKReceiveResponse();
        final GetFeedStatusResponse second = GetFeedStatusResponse.createOKDropResponse();
        Mockito.when(client.callFeedStatus(ArgumentMatchers.any())).thenReturn(first, second);
        service.getFeedStatus(REQUEST);

        service.refreshStale();
        assertThat(service.getFeedStatus(REQUEST)).as("a fresh entry is left alone").isSameAs(first);

        service.refreshOlderThan(Duration.ZERO);
        assertThat(service.getFeedStatus(REQUEST)).as("a stale one is reloaded").isSameAs(second);
        Mockito.verify(client, Mockito.times(2)).callFeedStatus(ArgumentMatchers.any());
    }

    @Test
    void testAnEntryNobodyHasReadSinceItWasLoadedIsNotRefreshed() {
        Mockito.when(client.callFeedStatus(ArgumentMatchers.any()))
                .thenReturn(GetFeedStatusResponse.createOKReceiveResponse());
        service.getFeedStatus(REQUEST);

        service.refreshOlderThan(Duration.ZERO);
        service.refreshOlderThan(Duration.ZERO);
        // Loaded once, refreshed once for the read, then left alone.
        Mockito.verify(client, Mockito.times(2)).callFeedStatus(ArgumentMatchers.any());

        service.getFeedStatus(REQUEST);
        service.refreshOlderThan(Duration.ZERO);
        Mockito.verify(client, Mockito.times(3)).callFeedStatus(ArgumentMatchers.any());
    }

    @Test
    void testNothingIsRefreshedWhileTheDownstreamIsDisabled() {
        Mockito.when(client.callFeedStatus(ArgumentMatchers.any()))
                .thenReturn(GetFeedStatusResponse.createOKReceiveResponse());
        service.getFeedStatus(REQUEST);
        Mockito.when(client.isDownstreamEnabled()).thenReturn(false);

        service.refreshOlderThan(Duration.ZERO);

        Mockito.verify(client, Mockito.times(1)).callFeedStatus(ArgumentMatchers.any());
    }

    @Test
    void testAFailedRefreshKeepsThePreviousAnswerAndAFailedFirstLoadFallsBackToTheDefault() {
        final GetFeedStatusResponse first = GetFeedStatusResponse.createOKDropResponse();
        Mockito.when(client.callFeedStatus(ArgumentMatchers.any()))
                .thenReturn(first)
                .thenThrow(new RuntimeException("downstream down"));
        service.getFeedStatus(REQUEST);

        service.refreshOlderThan(Duration.ZERO);
        assertThat(service.getFeedStatus(REQUEST)).isSameAs(first);

        final GetFeedStatusRequestV2 other = new GetFeedStatusRequestV2("FEED_B", null, Map.of());
        assertThat(service.getFeedStatus(other).getStatus())
                .as("the configured fallback is to receive")
                .isEqualTo(FeedStatus.Receive);
    }
}
