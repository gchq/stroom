package stroom.proxy.app;

import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.feed.remote.GetFeedStatusRequest;
import stroom.proxy.repo.ProxyRepoConfig;
import stroom.test.common.TestUtil;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

public class TestEndToEndNoStoreForward extends AbstractEndToEndTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestEndToEndNoStoreForward.class);
    protected static final String SYSTEM_TEST_SYSTEM = "TEST SYSTEM";
    protected static final String ENVIRONMENT_DEV = "DEV";

    @Override
    protected ProxyConfig getProxyConfigOverride() {
        return ProxyConfig.builder()
                .useDefaultOpenIdCredentials(true)
                .proxyId("TestProxy")
                .pathConfig(createProxyPathConfig())
                .proxyRepoConfig(ProxyRepoConfig.builder()
                        .storingEnabled(false)
                        .build())
                .addForwardDestination(createForwardHttpPostConfig())
                .restClientConfig(RestClientConfig.builder()
                        .withTlsConfiguration(null)
                        .build())
                .feedStatusConfig(createFeedStatusConfig())
                .build();
    }

    @Test
    void testBasicEndToEnd(final WireMockRuntimeInfo wireMockRuntimeInfo) {
        LOGGER.info("Starting basic end-end test");

        super.isRequestLoggingEnabled = true;

        setupStroomStubs(mappingBuilder ->
                mappingBuilder.willReturn(WireMock.ok()));

        final String content1 = "Hello";
        final String content2 = "Goodbye";
        sendPostToProxyDatafeed(
                FEED_TEST_EVENTS_1,
                SYSTEM_TEST_SYSTEM,
                ENVIRONMENT_DEV,
                Collections.emptyMap(),
                content1);

        sendPostToProxyDatafeed(
                FEED_TEST_EVENTS_2,
                SYSTEM_TEST_SYSTEM,
                ENVIRONMENT_DEV,
                Collections.emptyMap(),
                content2);

        final long expectedRequestCount = 2;

        Assertions.assertThat(getPostsToProxyCount())
                .isEqualTo(expectedRequestCount);

        TestUtil.waitForIt(
                this::getDataFeedPostsToStroomCount,
                (int) expectedRequestCount,
                () -> "Forward to stroom datafeed count",
                Duration.ofSeconds(10),
                Duration.ofMillis(50),
                Duration.ofSeconds(1));

        WireMock.verify((int) expectedRequestCount, WireMock.postRequestedFor(
                WireMock.urlPathEqualTo(getDataFeedPath())));

        final List<LoggedRequest> postsToStroomDataFeed = getPostsToStroomDataFeed();

        postsToStroomDataFeed.forEach(loggedRequest -> {
            assertHeaderValue(loggedRequest, "System", SYSTEM_TEST_SYSTEM);
            assertHeaderValue(loggedRequest, "Environment", ENVIRONMENT_DEV);
        });

        Assertions.assertThat(postsToStroomDataFeed)
                .extracting(req -> req.getHeader(StandardHeaderArguments.FEED))
                .containsExactly(FEED_TEST_EVENTS_1, FEED_TEST_EVENTS_2);

        final List<DataFeedRequest> dataFeedRequests = getDataFeedRequests();
        Assertions.assertThat(dataFeedRequests)
                .hasSize(2);

        assertSimpleDataFeedRequestContent(dataFeedRequests);

        // Health check sends in a feed status check with DUMMY_FEED to see if stroom is available
        Assertions.assertThat(getPostsToFeedStatusCheck())
                .extracting(GetFeedStatusRequest::getFeedName)
                .filteredOn(feed -> !"DUMMY_FEED".equals(feed))
                .containsExactly(FEED_TEST_EVENTS_1, FEED_TEST_EVENTS_2);
    }
}
