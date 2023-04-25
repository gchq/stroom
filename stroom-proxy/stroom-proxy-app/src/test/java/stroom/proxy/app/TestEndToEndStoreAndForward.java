package stroom.proxy.app;

import stroom.proxy.feed.remote.GetFeedStatusRequest;
import stroom.proxy.repo.AggregatorConfig;
import stroom.proxy.repo.ProxyRepoConfig;
import stroom.proxy.repo.dao.AggregateDao;
import stroom.proxy.repo.dao.FeedDao;
import stroom.proxy.repo.dao.ForwardAggregateDao;
import stroom.proxy.repo.dao.ForwardDestDao;
import stroom.proxy.repo.dao.ForwardSourceDao;
import stroom.proxy.repo.dao.SourceDao;
import stroom.proxy.repo.dao.SourceItemDao;
import stroom.test.common.TestUtil;
import stroom.util.time.StroomDuration;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.google.inject.Injector;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collections;
import java.util.function.Supplier;
import javax.inject.Inject;

public class TestEndToEndStoreAndForward extends AbstractEndToEndTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestEndToEndStoreAndForward.class);
    protected static final String SYSTEM_TEST_SYSTEM = "TEST SYSTEM";
    protected static final String ENVIRONMENT_DEV = "DEV";

    @Inject
    private AggregateDao aggregateDao;
    @Inject
    private FeedDao feedDao;
    @Inject
    private ForwardAggregateDao forwardAggregateDao;
    @Inject
    private ForwardDestDao forwardDestDao;
    @Inject
    private ForwardSourceDao forwardSourceDao;
    @Inject
    private SourceDao sourceDao;
    @Inject
    private SourceItemDao sourceItemDao;

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
        final App app = getDropwizard().getApplication();
        final Injector injector = app.getInjector();
        injector.injectMembers(this);

        DbRecordCounts expected = new DbRecordCounts(0, 0, 0, 1, 0, 0, 0, 0);
        assertRecordCounts(this::getDbRecordCounts, expected);

        wireMockProxyDestination.setRequestLoggingEnabled(true);
        wireMockProxyDestination.setupStroomStubs(mappingBuilder ->
                mappingBuilder.willReturn(WireMock.ok()));
        // now the stubs are set up wait for proxy to be ready as proxy needs the
        // stubs to be available to be healthy
        waitForHealthyProxyApp(Duration.ofSeconds(30));

        final String content1 = "Hello";
        final String content2 = "Goodbye";

        // Two feeds each send 4, agg max items of 3 so two batches each
        final PostDataHelper postDataHelper = createPostDataHelper();
        for (int i = 0; i < 4; i++) {
            postDataHelper.sendData(
                    FEED_TEST_EVENTS_1,
                    SYSTEM_TEST_SYSTEM,
                    ENVIRONMENT_DEV,
                    Collections.emptyMap(),
                    content1);

            postDataHelper.sendData(
                    FEED_TEST_EVENTS_2,
                    SYSTEM_TEST_SYSTEM,
                    ENVIRONMENT_DEV,
                    Collections.emptyMap(),
                    content2);
        }

        Assertions.assertThat(postDataHelper.getPostCount())
                .isEqualTo(8);

        TestUtil.waitForIt(
                wireMockProxyDestination::getDataFeedPostsToStroomCount,
                4,
                () -> "Forward to stroom datafeed count",
                Duration.ofSeconds(10),
                Duration.ofMillis(50),
                Duration.ofSeconds(1));

        // Check number of forwarded files.
        WireMock.verify((int) 4, WireMock.postRequestedFor(
                WireMock.urlPathEqualTo(getDataFeedPath())));

        // Assert the content of posts
        wireMockProxyDestination.assertPosts();

        expected = new DbRecordCounts(0, 2, 0, 1, 0, 0, 0, 0);
        assertRecordCounts(this::getDbRecordCounts, expected);

        // Health check sends in a feed status check with DUMMY_FEED to see if stroom is available
        Assertions.assertThat(wireMockProxyDestination.getPostsToFeedStatusCheck())
                .extracting(GetFeedStatusRequest::getFeedName)
                .filteredOn(feed -> !"DUMMY_FEED".equals(feed))
                .containsExactly(FEED_TEST_EVENTS_1, FEED_TEST_EVENTS_2);
    }

    @Test
    void testForwardFailure() {
        LOGGER.info("Starting basic end-end test");
        final App app = getDropwizard().getApplication();
        final Injector injector = app.getInjector();
        injector.injectMembers(this);

        DbRecordCounts expected = new DbRecordCounts(0, 0, 0, 1, 0, 0, 0, 0);
        assertRecordCounts(this::getDbRecordCounts, expected);

        wireMockProxyDestination.setRequestLoggingEnabled(true);
        wireMockProxyDestination.setupStroomStubs(mappingBuilder ->
                mappingBuilder.willReturn(WireMock.serverError()));
        // now the stubs are set up wait for proxy to be ready as proxy needs the
        // stubs to be available to be healthy
        waitForHealthyProxyApp(Duration.ofSeconds(30));

        final String content1 = "Hello";
        final String content2 = "Goodbye";

        // Two feeds each send 4, agg max items of 3 so two batches each
        final PostDataHelper postDataHelper = createPostDataHelper();
        for (int i = 0; i < 4; i++) {
            postDataHelper.sendData(
                    FEED_TEST_EVENTS_1,
                    SYSTEM_TEST_SYSTEM,
                    ENVIRONMENT_DEV,
                    Collections.emptyMap(),
                    content1);

            postDataHelper.sendData(
                    FEED_TEST_EVENTS_2,
                    SYSTEM_TEST_SYSTEM,
                    ENVIRONMENT_DEV,
                    Collections.emptyMap(),
                    content2);
        }

        // Assert that we posted 8 files.
        Assertions.assertThat(postDataHelper.getPostCount())
                .isEqualTo(8);

        // Assert that proxy attempted to forward 4 files to Stroom.
        TestUtil.waitForIt(
                wireMockProxyDestination::getDataFeedPostsToStroomCount,
                4,
                () -> "Forward to Stroom datafeed count",
                Duration.ofSeconds(10),
                Duration.ofMillis(50),
                Duration.ofSeconds(1));

        // Check number of forwarded files.
        WireMock.verify(4, WireMock.postRequestedFor(
                WireMock.urlPathEqualTo(getDataFeedPath())));

        // Assert the content of posts
        wireMockProxyDestination.assertPosts();

        expected = new DbRecordCounts(4, 2, 4, 1, 0, 8, 0, 8);
        assertRecordCounts(this::getDbRecordCounts, expected);

        // Health check sends in a feed status check with DUMMY_FEED to see if stroom is available
        Assertions.assertThat(wireMockProxyDestination.getPostsToFeedStatusCheck())
                .extracting(GetFeedStatusRequest::getFeedName)
                .filteredOn(feed -> !"DUMMY_FEED".equals(feed))
                .containsExactly(FEED_TEST_EVENTS_1, FEED_TEST_EVENTS_2);
    }

    private DbRecordCounts getDbRecordCounts() {
        return new DbRecordCounts(aggregateDao.countAggregates(),
                feedDao.countFeeds(),
                forwardAggregateDao.countForwardAggregates(),
                forwardDestDao.countForwardDest(),
                forwardSourceDao.countForwardSource(),
                sourceDao.countSources(),
                sourceDao.countDeletableSources(),
                sourceItemDao.countItems());
    }

    private void assertRecordCounts(final Supplier<DbRecordCounts> actual,
                                    final DbRecordCounts expected) {
        TestUtil.waitForIt(
                actual,
                expected,
                () -> "Unexpected record counts",
                Duration.ofSeconds(10),
                Duration.ofMillis(50),
                Duration.ofSeconds(1));
    }

    private record DbRecordCounts(int countAggregates,
                                  int countFeeds,
                                  int countForwardAggregates,
                                  int countForwardDest,
                                  int countForwardSource,
                                  int countSources,
                                  int countDeletableSources,
                                  int countItems) {

    }
}
