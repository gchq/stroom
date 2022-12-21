package stroom.proxy.app;

import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.feed.remote.GetFeedStatusRequest;
import stroom.proxy.repo.AggregatorConfig;
import stroom.proxy.repo.ProxyRepoConfig;
import stroom.test.common.TestUtil;
import stroom.util.time.StroomDuration;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

public class TestEndToEndStoreAndForward extends AbstractEndToEndTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestEndToEndStoreAndForward.class);
    protected static final String FEED_TEST_EVENTS_1 = "TEST-EVENTS_1";
    protected static final String FEED_TEST_EVENTS_2 = "TEST-EVENTS_2";
    protected static final String SYSTEM_TEST_SYSTEM = "TEST SYSTEM";
    protected static final String ENVIRONMENT_DEV = "DEV";

    @Override
    protected ProxyConfig getProxyConfigOverride() {
        return ProxyConfig.builder()
                .useDefaultOpenIdCredentials(true)
                .proxyId("TestProxy")
                .pathConfig(createProxyPathConfig())
                .proxyRepoConfig(ProxyRepoConfig.builder()
                        .storingEnabled(true)
                        .build())
                .aggregatorConfig(AggregatorConfig.builder()
                        .maxItemsPerAggregate(1000)
                        .maxUncompressedByteSizeString("1G")
                        .maxAggregateAge(StroomDuration.ofSeconds(1))
                        .aggregationFrequency(StroomDuration.ofSeconds(1))
                        .maxItemsPerAggregate(3)
                        .build())
                .addForwardDestination(createForwardHttpPostConfig())
                .restClientConfig(RestClientConfig.builder()
                        .withTlsConfiguration(null)
                        .build())
                .feedStatusConfig(createFeedStatusConfig())
                .build();
    }

    @Test
    void testBasicEndToEnd() {
        LOGGER.info("Starting basic end-end test");

        super.isRequestLoggingEnabled = true;

        setupStroomStubs(mappingBuilder ->
                mappingBuilder.willReturn(WireMock.ok()));

        final String content1 = "Hello";
        final String content2 = "Goodbye";

        // Two feeds each send 4, agg max items of 3 so two batches each
        for (int i = 0; i < 4; i++) {
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
        }

        Assertions.assertThat(getPostsToProxyCount())
                .isEqualTo(8);

        TestUtil.waitForIt(
                this::getDataFeedPostsToStroomCount,
                4,
                () -> "Forward to stroom datafeed count",
                Duration.ofSeconds(10),
                Duration.ofMillis(50),
                Duration.ofSeconds(1));

        WireMock.verify((int) 4, WireMock.postRequestedFor(
                WireMock.urlPathEqualTo(getDataFeedPath())));

        final List<LoggedRequest> postsToStroomDataFeed = getPostsToStroomDataFeed();

        Assertions.assertThat(postsToStroomDataFeed)
                .extracting(req -> req.getHeader(StandardHeaderArguments.FEED))
                .containsExactlyInAnyOrder(
                        FEED_TEST_EVENTS_1,
                        FEED_TEST_EVENTS_2,
                        FEED_TEST_EVENTS_1,
                        FEED_TEST_EVENTS_2);

        final List<DataFeedRequest> dataFeedRequests = getDataFeedRequests();
        Assertions.assertThat(dataFeedRequests)
                .hasSize(4);

        // Can't be sure of the order they are sent,
        Assertions.assertThat(dataFeedRequests.stream()
                        .map(dataFeedRequest -> dataFeedRequest.getNameToItemMap().size())
                        .toList())
                .containsExactlyInAnyOrder(6, 6, 2, 2);

        // Health check sends in a feed status check with DUMMY_FEED to see if stroom is available
        Assertions.assertThat(getPostsToFeedStatusCheck())
                .extracting(GetFeedStatusRequest::getFeedName)
                .filteredOn(feed -> !"DUMMY_FEED".equals(feed))
                .containsExactly(FEED_TEST_EVENTS_1, FEED_TEST_EVENTS_2);
    }
}
