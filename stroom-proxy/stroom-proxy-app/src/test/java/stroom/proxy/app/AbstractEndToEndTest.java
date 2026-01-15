/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.proxy.app;

import stroom.test.common.TestResourceLocks;
import stroom.util.io.CommonDirSetup;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResourcePaths;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.google.inject.Injector;
import io.dropwizard.core.server.DefaultServerFactory;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status.Family;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;


@ResourceLock(TestResourceLocks.STROOM_APP_PORT_8080)
public abstract class AbstractEndToEndTest extends AbstractApplicationTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractEndToEndTest.class);

    final MockHttpDestination mockHttpDestination = new MockHttpDestination();

    // Use RegisterExtension instead of @WireMockTest, so we can set up the req listener
    @SuppressWarnings("unused")
    @RegisterExtension
    public final WireMockExtension wireMockExtension = mockHttpDestination.createExtension();

    static {
        CommonDirSetup.setup();
    }

    @BeforeEach
    void setup() {
        LOGGER.info("WireMock running on: {}", wireMockExtension.getRuntimeInfo().getHttpBaseUrl());
        mockHttpDestination.clear();

        final App app = getDropwizard().getApplication();
        final Injector injector = app.getInjector();
        injector.injectMembers(this);
    }

    public PostDataHelper createPostDataHelper() {
        final String url = buildProxyAppPath(ResourcePaths.buildUnauthenticatedServletPath("datafeed"));
        return new PostDataHelper(getClient(), url);
    }

    void waitForHealthyProxyApp(final Duration timeout) {
        final Instant startTime = Instant.now();
        final Instant endTime = startTime.plus(timeout);
        final String healthCheckUrl = buildProxyAdminPath("/healthcheck");

        boolean didTimeout = true;
        Response response = null;

        LOGGER.info("Waiting for proxy to start using " + healthCheckUrl);
        while (endTime.isAfter(Instant.now())) {
            try {
                response = getClient().target(healthCheckUrl)
                        .request()
                        .get();
                if (Family.SUCCESSFUL.equals(response.getStatusInfo().toEnum().getFamily())) {
                    didTimeout = false;
                    LOGGER.info("Proxy is ready and healthy");
                    break;
                } else {
                    LOGGER.info("Still waiting for proxy to be healthy. Last error: {} - {}. Will give up in {}",
                            response.getStatus(),
                            response.getStatusInfo().getReasonPhrase(),
                            Duration.between(Instant.now(), endTime));
                    throw new RuntimeException(LogUtil.message("Proxy is unhealthy, got {} code",
                            response.getStatus()));
                }
            } catch (final Exception e) {
                // Expected, so sleep and go round again
            }
            try {
                //noinspection BusyWait
                Thread.sleep(100);
            } catch (final InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while sleeping");
            }
        }
        if (didTimeout) {
            // Get the health check content so we can see what is wrong. Likely a feed status check issue
            //noinspection Convert2Diamond
            final Map<String, Object> map = NullSafe.get(response, resp ->
                    resp.readEntity(new GenericType<Map<String, Object>>() {
                    }));
            LOGGER.error("Last response: {}", response);
            throw new RuntimeException(LogUtil.message(
                    "Timed out waiting for proxy to start. Last response: {}", map));
        }
    }

    String getProxyBaseAppUrl() {
        final String appPath = ((DefaultServerFactory) getConfig().getServerFactory()).getApplicationContextPath();
        return "http://localhost:" + getDropwizard().getLocalPort() + appPath;
    }

    String getProxyBaseAdminUrl() {
        final String adminPath = ((DefaultServerFactory) getConfig().getServerFactory()).getAdminContextPath();
        return "http://localhost:" + getDropwizard().getAdminPort() + adminPath;
    }

    String buildProxyAppPath(final String path) {
        return getProxyBaseAppUrl().replaceAll("/$", "") + path;
    }

    String buildProxyAdminPath(final String path) {
        return getProxyBaseAdminUrl().replaceAll("/$", "") + path;
    }
}
