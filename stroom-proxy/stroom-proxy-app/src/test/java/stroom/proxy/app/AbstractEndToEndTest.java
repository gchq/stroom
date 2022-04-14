package stroom.proxy.app;

import stroom.proxy.feed.remote.GetFeedStatusResponse;
import stroom.receive.common.FeedStatusResource;
import stroom.util.logging.LogUtil;
import stroom.util.shared.ResourcePaths;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.matching.UrlPattern;
import io.dropwizard.server.DefaultServerFactory;
import io.dropwizard.testing.junit5.DropwizardAppExtension;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status.Family;

public class AbstractEndToEndTest extends AbstractApplicationTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractEndToEndTest.class);

    static final int DEFAULT_STROOM_PORT = 8080;

    // This is needed by DropwizardExtensionsSupport to fire up the proxy app
    private static final DropwizardAppExtension<Config> DROPWIZARD = new DropwizardAppExtension<Config>(
            App.class,
            getConfig());

    final LongAdder postCount = new LongAdder();
    final Client client = DROPWIZARD.client();

    @BeforeEach
    void beforeEach(final WireMockRuntimeInfo wmRuntimeInfo) {
        postCount.reset();
        LOGGER.info("WireMock running on: {}", wmRuntimeInfo.getHttpBaseUrl());
    }

    void setupStroomStubs(Function<MappingBuilder, MappingBuilder> builderFunc) {
        final String feedStatusPath = ResourcePaths.buildAuthenticatedApiPath(
                FeedStatusResource.BASE_RESOURCE_PATH,
                FeedStatusResource.GET_FEED_STATUS_PATH_PART);

        GetFeedStatusResponse feedStatusResponse = GetFeedStatusResponse.createOKRecieveResponse();

        ObjectMapper objectMapper = new ObjectMapper();
        final String responseJson;
        try {
            responseJson = objectMapper.writeValueAsString(feedStatusResponse);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error creating json for " + feedStatusResponse);
        }

        WireMock.stubFor(WireMock.post(feedStatusPath)
                .willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(responseJson)));
        LOGGER.info("Setup WireMock POST stub for {}", feedStatusPath);

        final String datafeedPath = ResourcePaths.buildUnauthenticatedServletPath("datafeed");
        WireMock.stubFor(builderFunc.apply(WireMock.post(datafeedPath)));
        LOGGER.info("Setup WireMock POST stub for {}", datafeedPath);

        WireMock.stubFor(WireMock.options(UrlPattern.ANY)
                .willReturn(
                        WireMock.aResponse()
                                .withHeader("Allow", "POST")));
        LOGGER.info("Setup WireMock OPTIONS stub for any URL");

        // now the stubs are set up wait for proxy to be ready as proxy needs to stubs to be healthy
        waitForHealthyProxyApp(Duration.ofSeconds(30));
    }

    int postToProxyDatafeed(final String feed,
                            final String system,
                            final String environment,
                            final Map<String, String> extraHeaders,
                            final String data) {
        int status = -1;
        // URL on wiremocked stroom
        final String url = buildProxyAppPath(ResourcePaths.buildUnauthenticatedServletPath("datafeed"));
        try {

            final Builder builder = client.target(url)
                    .request()
                    .header("Feed", feed)
                    .header("System", system)
                    .header("Environment", environment);

            if (extraHeaders != null) {
                extraHeaders.forEach(builder::header);
            }
            LOGGER.info("Sending POST request to {}", url);
            final Response response = builder.post(Entity.text(data));
            postCount.increment();
            status = response.getStatus();
            final String responseText = response.readEntity(String.class);
            LOGGER.info("datafeed response ({}):\n{}", status, responseText);

        } catch (final Exception e) {
            throw new RuntimeException("Error sending request to " + url, e);
        }
        return status;
    }

    static void waitForHealthyProxyApp(final Duration timeout) {

        final Instant startTime = Instant.now();
        final String healthCheckUrl = buildProxyAdminPath("/healthcheck");

        boolean didTimeout = true;

        LOGGER.info("Waiting for proxy to start using " + healthCheckUrl);
        while (startTime.plus(timeout).isAfter(Instant.now())) {
            try {
                Response response = DROPWIZARD.client().target(healthCheckUrl)
                        .request()
                        .get();
                if (Family.SUCCESSFUL.equals(response.getStatusInfo().toEnum().getFamily())) {
                    didTimeout = false;
                    LOGGER.info("Proxy is ready and healthy");
                    break;
                } else {
                    throw new RuntimeException(LogUtil.message("Proxy is unhealthy, got {} code",
                            response.getStatus()));
                }
            } catch (Exception e) {
                // Expected, so sleep and go round again
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while sleeping");
            }
        }
        if (didTimeout) {
            throw new RuntimeException("Timed out waiting for proxy to start");
        }
    }

    static String getProxyBaseAppUrl() {
        final String appPath = ((DefaultServerFactory) getConfig().getServerFactory()).getApplicationContextPath();
        return "http://localhost:" + DROPWIZARD.getLocalPort() + appPath;
    }

    static String getProxyBaseAdminUrl() {
        final String adminPath = ((DefaultServerFactory) getConfig().getServerFactory()).getAdminContextPath();
        return "http://localhost:" + DROPWIZARD.getAdminPort() + adminPath;
    }

    static String buildProxyAppPath(final String path) {
        final String url = getProxyBaseAppUrl().replaceAll("/$", "") + path;
        return url;
    }

    static String buildProxyAdminPath(final String path) {
        final String url = getProxyBaseAdminUrl().replaceAll("/$", "") + path;
        return url;
    }

    long getPostCount() {
        return postCount.longValue();
    }
}
