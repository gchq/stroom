package stroom.proxy.app;

import stroom.proxy.app.DbRecordCountAssertion.DbRecordCounts;
import stroom.proxy.repo.AggregatorConfig;
import stroom.proxy.repo.ProxyRepoConfig;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.time.StroomDuration;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.google.common.io.Files;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import javax.inject.Inject;

public class TestEndToEndStoreAndForwardToFile extends AbstractEndToEndTest {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestEndToEndStoreAndForwardToFile.class);

    @Inject
    private DbRecordCountAssertion dbRecordCountAssertion;
    @Inject
    private MockFileDestination mockFileDestination;

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
                .addForwardDestination(MockFileDestination.createForwardFileConfig())
                .feedStatusConfig(MockHttpDestination.createFeedStatusConfig())
                .build();
    }

    @Test
    void testBasicEndToEnd() {
        LOGGER.info("Starting basic end-end test");
        dbRecordCountAssertion.assertRecordCounts(new DbRecordCounts(0, 0, 0, 1, 0, 0, 0, 0));

        mockHttpDestination.setupStroomStubs(mappingBuilder ->
                mappingBuilder.willReturn(WireMock.ok()));
        // now the stubs are set up wait for proxy to be ready as proxy needs the
        // stubs to be available to be healthy
        waitForHealthyProxyApp(Duration.ofSeconds(30));

        // Two feeds each send 4, agg max items of 3 so two batches each
        final PostDataHelper postDataHelper = createPostDataHelper();
        for (int i = 0; i < 4; i++) {
            postDataHelper.sendTestData1();
            postDataHelper.sendTestData2();
        }

        Assertions.assertThat(postDataHelper.getPostCount())
                .isEqualTo(8);


        // Assert the contents of the files.
        mockFileDestination.assertFileContents(getConfig());

        dbRecordCountAssertion.assertRecordCounts(new DbRecordCounts(0, 2, 0, 1, 0, 0, 0, 0));

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
        dbRecordCountAssertion.assertRecordCounts(new DbRecordCounts(0, 0, 0, 1, 0, 0, 0, 0));
        mockHttpDestination.setupStroomStubs(mappingBuilder ->
                mappingBuilder.willReturn(WireMock.ok()));
        // now the stubs are set up wait for proxy to be ready as proxy needs the
        // stubs to be available to be healthy
        waitForHealthyProxyApp(Duration.ofSeconds(30));

        // Two feeds each send 4, agg max items of 3 so two batches each
        final PostDataHelper postDataHelper = createPostDataHelper();
        for (int i = 0; i < 4; i++) {
            postDataHelper.sendZipTestData1();
            postDataHelper.sendZipTestData2();
        }

        Assertions.assertThat(postDataHelper.getPostCount())
                .isEqualTo(8);

        // Assert the contents of the files.
        mockFileDestination.assertFileContents(getConfig(), 12);

        dbRecordCountAssertion.assertRecordCounts(new DbRecordCounts(0, 2, 0, 1, 0, 0, 0, 0));

        // Health check sends in a feed status check with DUMMY_FEED to see if stroom is available
        mockHttpDestination.assertFeedStatusCheck();

        // No http forwarders set up so nothing goes to stroom
        final List<LoggedRequest> postsToStroomDataFeed = mockHttpDestination.getPostsToStroomDataFeed();
        Assertions.assertThat(postsToStroomDataFeed)
                .hasSize(0);
    }
}
