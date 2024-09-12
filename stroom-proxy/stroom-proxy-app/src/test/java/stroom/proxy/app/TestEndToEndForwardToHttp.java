/*
 * Copyright 2024 Crown Copyright
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

import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.app.DbRecordCountAssertion.DbRecordCounts;
import stroom.proxy.repo.ProxyRepoConfig;
import stroom.receive.common.ReceiveDataConfig;
import stroom.security.openid.api.IdpType;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import jakarta.inject.Inject;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;

@Disabled
public class TestEndToEndForwardToHttp extends AbstractEndToEndTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestEndToEndForwardToHttp.class);

    @Inject
    private DbRecordCountAssertion dbRecordCountAssertion;

    @Override
    protected ProxyConfig getProxyConfigOverride() {
        return ProxyConfig.builder()
                .securityConfig(new ProxySecurityConfig(ProxyAuthenticationConfig.builder()
                        .openIdConfig(new ProxyOpenIdConfig()
                                .withIdentityProviderType(IdpType.TEST_CREDENTIALS))
                        .build()))
                .proxyId("TestProxy")
                .pathConfig(createProxyPathConfig())
                .proxyRepoConfig(ProxyRepoConfig.builder()
                        .storingEnabled(false)
                        .build())
                .addForwardDestination(MockHttpDestination.createForwardHttpPostConfig())
                .feedStatusConfig(MockHttpDestination.createFeedStatusConfig())
                .receiveDataConfig(ReceiveDataConfig.builder()
                        .withAuthenticationRequired(false)
                        .build())
                .build();
    }

    @Test
    void testBasicEndToEnd() {
        LOGGER.info("Starting basic end-end test");
        dbRecordCountAssertion.assertRecordCounts(new DbRecordCounts(0, 0, 0, 0, 0, 0, 0, 0));

        mockHttpDestination.setupStroomStubs(mappingBuilder ->
                mappingBuilder.willReturn(WireMock.ok()));
        // now the stubs are set up wait for proxy to be ready as proxy needs the
        // stubs to be available to be healthy
        waitForHealthyProxyApp(Duration.ofSeconds(30));

        final PostDataHelper postDataHelper = createPostDataHelper();
        postDataHelper.sendTestData1();
        postDataHelper.sendTestData2();

        final int expectedRequestCount = 2;

        Assertions.assertThat(postDataHelper.getPostCount())
                .isEqualTo(expectedRequestCount);

        mockHttpDestination.assertRequestCount(expectedRequestCount);
        final List<LoggedRequest> postsToStroomDataFeed = mockHttpDestination.getPostsToStroomDataFeed();

        postsToStroomDataFeed.forEach(loggedRequest -> {
            mockHttpDestination.assertHeaderValue(loggedRequest, "System", TestConstants.SYSTEM_TEST_SYSTEM);
            mockHttpDestination.assertHeaderValue(loggedRequest, "Environment", TestConstants.ENVIRONMENT_DEV);
        });

        Assertions.assertThat(postsToStroomDataFeed)
                .extracting(req -> req.getHeader(StandardHeaderArguments.FEED.get()))
                .containsExactly(TestConstants.FEED_TEST_EVENTS_1, TestConstants.FEED_TEST_EVENTS_2);

        mockHttpDestination.assertSimpleDataFeedRequestContent(expectedRequestCount);

        dbRecordCountAssertion.assertRecordCounts(new DbRecordCounts(0, 0, 0, 0, 0, 0, 0, 0));

        // Health check sends in a feed status check with DUMMY_FEED to see if stroom is available
        mockHttpDestination.assertFeedStatusCheck();
    }
}
