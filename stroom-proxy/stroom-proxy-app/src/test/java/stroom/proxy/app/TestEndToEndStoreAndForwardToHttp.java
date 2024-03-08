package stroom.proxy.app;

import stroom.proxy.app.DbRecordCountAssertion.DbRecordCounts;
import stroom.proxy.repo.AggregatorConfig;
import stroom.proxy.repo.ProxyRepoConfig;
import stroom.receive.common.ReceiveDataConfig;
import stroom.security.openid.api.IdpType;
import stroom.util.time.StroomDuration;

import com.github.tomakehurst.wiremock.client.WireMock;
import jakarta.inject.Inject;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

@Disabled
public class TestEndToEndStoreAndForwardToHttp extends AbstractEndToEndTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestEndToEndStoreAndForwardToHttp.class);

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
                        .storingEnabled(true)
                        .build())
                .aggregatorConfig(AggregatorConfig.builder()
                        .maxUncompressedByteSizeString("1G")
                        .maxAggregateAge(StroomDuration.ofSeconds(5))
                        .aggregationFrequency(StroomDuration.ofSeconds(1))
                        .maxItemsPerAggregate(3)
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

        // Check number of forwarded files.
        mockHttpDestination.assertRequestCount(4);

        // Assert the content of posts
        mockHttpDestination.assertPosts();

        dbRecordCountAssertion.assertRecordCounts(new DbRecordCounts(0, 2, 0, 1, 0, 0, 0, 0));

        // Health check sends in a feed status check with DUMMY_FEED to see if stroom is available
        mockHttpDestination.assertFeedStatusCheck();
    }

    @Test
    void testForwardFailure() {
        LOGGER.info("Starting basic end-end test");
        dbRecordCountAssertion.assertRecordCounts(new DbRecordCounts(0, 0, 0, 1, 0, 0, 0, 0));

        mockHttpDestination.setupStroomStubs(mappingBuilder ->
                mappingBuilder.willReturn(WireMock.serverError()));
        // now the stubs are set up wait for proxy to be ready as proxy needs the
        // stubs to be available to be healthy
        waitForHealthyProxyApp(Duration.ofSeconds(30));

        // Two feeds each send 4, agg max items of 3 so two batches each
        final PostDataHelper postDataHelper = createPostDataHelper();
        for (int i = 0; i < 4; i++) {
            postDataHelper.sendTestData1();
            postDataHelper.sendTestData2();
        }

        // Assert that we posted 8 files.
        Assertions.assertThat(postDataHelper.getPostCount())
                .isEqualTo(8);

        // Check number of forwarded files.
        mockHttpDestination.assertRequestCount(4);

        // Assert the content of posts
        mockHttpDestination.assertPosts();

        dbRecordCountAssertion.assertRecordCounts(new DbRecordCounts(4, 2, 4, 1, 0, 8, 0, 8));

        // Health check sends in a feed status check with DUMMY_FEED to see if stroom is available
        mockHttpDestination.assertFeedStatusCheck();
    }
}
