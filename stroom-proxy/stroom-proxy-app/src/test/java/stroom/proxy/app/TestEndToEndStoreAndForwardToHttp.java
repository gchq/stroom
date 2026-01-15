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

package stroom.proxy.app;

import stroom.proxy.repo.AggregatorConfig;
import stroom.receive.common.ReceiveDataConfig;
import stroom.security.openid.api.IdpType;
import stroom.util.time.StroomDuration;

import com.github.tomakehurst.wiremock.client.WireMock;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

@Disabled
public class TestEndToEndStoreAndForwardToHttp extends AbstractEndToEndTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestEndToEndStoreAndForwardToHttp.class);

    @Override
    protected ProxyConfig getProxyConfigOverride() {
        return ProxyConfig.builder()
                .securityConfig(new ProxySecurityConfig(ProxyAuthenticationConfig.builder()
                        .openIdConfig(new ProxyOpenIdConfig()
                                .withIdentityProviderType(IdpType.TEST_CREDENTIALS))
                        .build()))
                .proxyId("TestProxy")
                .pathConfig(createProxyPathConfig())
                .aggregatorConfig(AggregatorConfig.builder()
                        .maxUncompressedByteSizeString("1G")
                        .aggregationFrequency(StroomDuration.ofSeconds(5))
                        .maxItemsPerAggregate(3)
                        .build())
                .addForwardHttpDestination(MockHttpDestination.createForwardHttpPostConfig(false))
                .feedStatusConfig(MockHttpDestination.createFeedStatusConfig())
                .downstreamHostConfig(MockHttpDestination.createDownstreamHostConfig())
                .receiveDataConfig(ReceiveDataConfig.builder()
                        .withAuthenticationRequired(false)
                        .build())
                .build();
    }

    @Test
    void testBasicEndToEnd() {
        LOGGER.info("Starting basic end-end test");

        mockHttpDestination.setupStroomStubs(mappingBuilder ->
                mappingBuilder.willReturn(WireMock.ok()));
        // now the stubs are set up wait for proxy to be ready as proxy needs the
        // stubs to be available to be healthy
        waitForHealthyProxyApp(Duration.ofSeconds(30));

        // Two feeds each send 4, agg max items of 3 so two batches each
        final PostDataHelper postDataHelper = createPostDataHelper();
        final int reqPerFeed = 16;
        final int reqCount = reqPerFeed * 2;
        for (int i = 0; i < reqPerFeed; i++) {
            postDataHelper.sendFeed1TestData();
            postDataHelper.sendFeed2TestData();
        }

        Assertions.assertThat(postDataHelper.getPostCount())
                .isEqualTo(reqCount);

        mockHttpDestination.assertReceivedItemCount(reqCount);
        mockHttpDestination.assertReceiptIds(postDataHelper.getReceiptIds());
        mockHttpDestination.assertMaxItemsPerAggregate(getConfig());

        // Health check sends in a feed status check with DUMMY_FEED to see if stroom is available
        mockHttpDestination.assertFeedStatusCheck();
    }

    @Test
    void testForwardFailure() {
        LOGGER.info("Starting basic end-end test");

        mockHttpDestination.setupStroomStubs(mappingBuilder ->
                mappingBuilder.willReturn(WireMock.serverError()));
        // now the stubs are set up wait for proxy to be ready as proxy needs the
        // stubs to be available to be healthy
        waitForHealthyProxyApp(Duration.ofSeconds(30));

        // Two feeds each send 4, agg max items of 3 so two batches each
        final PostDataHelper postDataHelper = createPostDataHelper();
        for (int i = 0; i < 4; i++) {
            postDataHelper.sendFeed1TestData();
            postDataHelper.sendFeed2TestData();
        }

        // Assert that we posted 8 files.
        Assertions.assertThat(postDataHelper.getPostCount())
                .isEqualTo(8);

        // Check number of forwarded files.
        mockHttpDestination.assertRequestCount(4);

        // Assert the content of posts
        mockHttpDestination.assertPosts(4);

        // Health check sends in a feed status check with DUMMY_FEED to see if stroom is available
        mockHttpDestination.assertFeedStatusCheck();
    }
}
