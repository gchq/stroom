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
import stroom.receive.rules.shared.ReceiptCheckMode;
import stroom.security.openid.api.IdpType;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.time.StroomDuration;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import jakarta.inject.Inject;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class TestEndToEndStoreAndForwardToFile extends AbstractEndToEndTest {

    private static final int MAX_ITEMS_PER_AGG = 3;

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestEndToEndStoreAndForwardToFile.class);

    @Inject
    private MockFileDestination mockFileDestination;

    @Override
    protected ProxyConfig getProxyConfigOverride() {
        return ProxyConfig.builder()
                .proxyId("TestProxy")
                .pathConfig(createProxyPathConfig())
                .securityConfig(new ProxySecurityConfig(ProxyAuthenticationConfig.builder()
                        .openIdConfig(new ProxyOpenIdConfig()
                                .withIdentityProviderType(IdpType.TEST_CREDENTIALS))
                        .build()))
                .aggregatorConfig(AggregatorConfig.builder()
                        .maxUncompressedByteSizeString("1G")
                        .aggregationFrequency(StroomDuration.ofSeconds(5))
                        .maxItemsPerAggregate(MAX_ITEMS_PER_AGG)
                        .build())
                .addForwardFileDestination(MockFileDestination.createForwardFileConfig())
                .feedStatusConfig(MockHttpDestination.createFeedStatusConfig())
                .downstreamHostConfig(MockHttpDestination.createDownstreamHostConfig())
                .receiveDataConfig(ReceiveDataConfig.builder()
                        .withAuthenticationRequired(false)
                        .withReceiptCheckMode(ReceiptCheckMode.FEED_STATUS)
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

        // Two feeds each send 16, agg max items of 3 so 6 batches each
        final PostDataHelper postDataHelper = createPostDataHelper();
        final int reqPerFeed = 16;
        final int reqCount = reqPerFeed * 2;
        for (int i = 0; i < reqPerFeed; i++) {
            postDataHelper.sendFeed1TestData();
            postDataHelper.sendFeed2TestData();
        }

        assertThat(postDataHelper.getPostCount())
                .isEqualTo(reqCount);

        mockFileDestination.assertReceivedItemCount(getConfig(), reqCount);

        // Makes sure all receiptIds are in the stored files
        mockFileDestination.assertReceiptIds(getConfig(), postDataHelper.getReceiptIds());

        mockFileDestination.assertMaxItemsPerAggregate(getConfig());

        // Health check sends in a feed status check with DUMMY_FEED to see if stroom is available
        mockHttpDestination.assertFeedStatusCheck();

        // No http forwarders set up so nothing goes to stroom
        final List<LoggedRequest> postsToStroomDataFeed = mockHttpDestination.getPostsToStroomDataFeed();
        Assertions.assertThat(postsToStroomDataFeed)
                .hasSize(0);
    }

    @Test
    void testBasicZipEndToEnd() {
        LOGGER.info("Starting basic end-end test");
        mockHttpDestination.setupStroomStubs(mappingBuilder ->
                mappingBuilder.willReturn(WireMock.ok()));
        // now the stubs are set up wait for proxy to be ready as proxy needs the
        // stubs to be available to be healthy
        waitForHealthyProxyApp(Duration.ofSeconds(30));

        // Two feeds each send 4, agg max items of 3 so two batches each
        final PostDataHelper postDataHelper = createPostDataHelper();
        for (int i = 0; i < 4; i++) {
            postDataHelper.sendZipTestData1(4);
            postDataHelper.sendZipTestData2(4);
        }

        Assertions.assertThat(postDataHelper.getPostCount())
                .isEqualTo(8);

        // Assert the contents of the files.
        mockFileDestination.assertFileContents(getConfig(), 12);

        // Health check sends in a feed status check with DUMMY_FEED to see if stroom is available
        mockHttpDestination.assertFeedStatusCheck();

        // No http forwarders set up so nothing goes to stroom
        final List<LoggedRequest> postsToStroomDataFeed = mockHttpDestination.getPostsToStroomDataFeed();
        Assertions.assertThat(postsToStroomDataFeed)
                .hasSize(0);
    }
}
