package stroom.proxy.app;

import stroom.proxy.repo.AggregatorConfig;
import stroom.proxy.repo.ProxyRepoConfig;
import stroom.receive.common.ReceiveDataConfig;
import stroom.security.openid.api.IdpType;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.time.StroomDuration;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class TestEndToEndStoreAndForwardToFile extends AbstractEndToEndTest {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestEndToEndStoreAndForwardToFile.class);

    private final MockFileDestination mockFileDestination = new MockFileDestination();

    @Override
    protected ProxyConfig getProxyConfigOverride() {
        return ProxyConfig.builder()
                .proxyId("TestProxy")
                .pathConfig(createProxyPathConfig())
                .proxyRepoConfig(ProxyRepoConfig.builder()
                        .storingEnabled(true)
                        .build())
                .securityConfig(new ProxySecurityConfig(ProxyAuthenticationConfig.builder()
                        .openIdConfig(new ProxyOpenIdConfig()
                                .withIdentityProviderType(IdpType.TEST_CREDENTIALS))
                        .build()))
                .aggregatorConfig(AggregatorConfig.builder()
                        .maxUncompressedByteSizeString("1G")
                        .maxAggregateAge(StroomDuration.ofSeconds(5))
                        .aggregationFrequency(StroomDuration.ofSeconds(1))
                        .maxItemsPerAggregate(3)
                        .build())
                .addForwardDestination(mockFileDestination.getConfig())
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

        // Two feeds each send 4, agg max items of 3 so two batches each
        final PostDataHelper postDataHelper = createPostDataHelper();
        for (int i = 0; i < 4; i++) {
            postDataHelper.sendTestData1();
            postDataHelper.sendTestData2();
        }

        assertThat(postDataHelper.getPostCount())
                .isEqualTo(8);

        // Assert the contents of the files.
        assertThat(mockFileDestination.getForwardFileMetaCount()).isEqualTo(8);

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
            postDataHelper.sendZipTestData1();
            postDataHelper.sendZipTestData2();
        }

        Assertions.assertThat(postDataHelper.getPostCount())
                .isEqualTo(8);

        // Assert the contents of the files.
        assertThat(mockFileDestination.getForwardFileMetaCount()).isEqualTo(12);

        // Health check sends in a feed status check with DUMMY_FEED to see if stroom is available
        mockHttpDestination.assertFeedStatusCheck();

        // No http forwarders set up so nothing goes to stroom
        final List<LoggedRequest> postsToStroomDataFeed = mockHttpDestination.getPostsToStroomDataFeed();
        Assertions.assertThat(postsToStroomDataFeed)
                .hasSize(0);
    }
}
