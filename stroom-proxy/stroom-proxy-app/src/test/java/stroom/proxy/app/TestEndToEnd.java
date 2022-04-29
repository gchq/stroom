package stroom.proxy.app;

import stroom.util.concurrent.ThreadUtil;
import stroom.util.shared.ResourcePaths;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;

@WireMockTest(httpPort = AbstractEndToEndTest.DEFAULT_STROOM_PORT) // Doesn't seem to work when it is on the superclass
public class TestEndToEnd extends AbstractEndToEndTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestEndToEnd.class);

    @Test
    void testBasicEndToEnd() {
        LOGGER.info("Hello");

        setupStroomStubs(mappingBuilder -> mappingBuilder.willReturn(WireMock.ok()));

        postToProxyDatafeed(
                "TEST-EVENTS",
                "TEST SYSTEM",
                "DEV",
                Collections.emptyMap(),
                "Hello");

        ThreadUtil.sleep(30_000);

        final int expectedRequestCount = 1;

        Assertions.assertThat(getPostCount())
                .isEqualTo(expectedRequestCount);

        WireMock.verify(1, WireMock.postRequestedFor(
                WireMock.urlPathEqualTo(
                        ResourcePaths.buildUnauthenticatedServletPath("datafeed"))));
    }
}
