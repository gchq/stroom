package stroom.proxy.app;

import stroom.meta.api.StandardHeaderArguments;
import stroom.receive.common.ReceiveDataConfig;
import stroom.security.openid.api.IdpType;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
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

    @Override
    protected ProxyConfig getProxyConfigOverride() {
        return ProxyConfig.builder()
                .securityConfig(new ProxySecurityConfig(ProxyAuthenticationConfig.builder()
                        .openIdConfig(new ProxyOpenIdConfig()
                                .withIdentityProviderType(IdpType.TEST_CREDENTIALS))
                        .build()))
                .proxyId("TestProxy")
                .pathConfig(createProxyPathConfig())
                .addForwardHttpDestination(MockHttpDestination.createForwardHttpPostConfig(true))
                .feedStatusConfig(MockHttpDestination.createFeedStatusConfig())
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
                .extracting(req -> req.getHeader(StandardHeaderArguments.FEED))
                .containsExactly(TestConstants.FEED_TEST_EVENTS_1, TestConstants.FEED_TEST_EVENTS_2);

        mockHttpDestination.assertSimpleDataFeedRequestContent(expectedRequestCount);

        // Health check sends in a feed status check with DUMMY_FEED to see if stroom is available
        mockHttpDestination.assertFeedStatusCheck();
    }
}
