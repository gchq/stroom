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

public class TestEndToEndStoreAndForwardToFileNoDownstream extends AbstractEndToEndTest {

    private static final int MAX_ITEMS_PER_AGG = 3;

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(
            TestEndToEndStoreAndForwardToFileNoDownstream.class);

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
                // No downstream, just an isolated proxy
                .downstreamHostConfig(DownstreamHostConfig.copy(MockHttpDestination.createDownstreamHostConfig())
                        .withEnabled(false)
                        .build())
                .receiveDataConfig(ReceiveDataConfig.builder()
                        .withAuthenticationRequired(false)
                        .withReceiptCheckMode(ReceiptCheckMode.RECEIVE_ALL)
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

        // No downstream, so FSC not called
        mockHttpDestination.assertFeedStatusCheckNotCalled();

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

        // No downstream, so FSC not called
        mockHttpDestination.assertFeedStatusCheckNotCalled();

        // No http forwarders set up so nothing goes to stroom
        final List<LoggedRequest> postsToStroomDataFeed = mockHttpDestination.getPostsToStroomDataFeed();
        Assertions.assertThat(postsToStroomDataFeed)
                .hasSize(0);
    }
}
