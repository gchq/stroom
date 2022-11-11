package stroom.proxy.app;

import stroom.meta.api.AttributeMap;
import stroom.proxy.feed.remote.GetFeedStatusRequest;
import stroom.proxy.feed.remote.GetFeedStatusResponse;
import stroom.receive.common.FeedStatusResource;
import stroom.receive.common.ReceiveDataServlet;
import stroom.receive.common.StroomStreamProcessor;
import stroom.util.NullSafe;
import stroom.util.io.ByteCountInputStream;
import stroom.util.logging.LogUtil;
import stroom.util.shared.ResourcePaths;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.Admin;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.extension.PostServeAction;
import com.github.tomakehurst.wiremock.http.HttpHeaders;
import com.github.tomakehurst.wiremock.http.LoggedResponse;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.matching.UrlPattern;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.dropwizard.server.DefaultServerFactory;
import io.dropwizard.testing.junit5.DropwizardAppExtension;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.io.FilenameUtils;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status.Family;


public class AbstractEndToEndTest extends AbstractApplicationTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractEndToEndTest.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    static final int DEFAULT_STROOM_PORT = 8080;

    // Can be changed by subclasses, e.g. if one test is noisy but others are not
    protected static volatile boolean IS_REQUEST_LOGGING_ENABLED = true;
    protected static volatile boolean IS_HEADER_LOGGING_ENABLED = true;

    // This is needed by DropwizardExtensionsSupport to fire up the proxy app
    private static final DropwizardAppExtension<Config> DROPWIZARD = new DropwizardAppExtension<Config>(
            App.class,
            getConfig());

    // Hold all requests send to the wiremock stroom datafeed endpoint
    private static final List<DataFeedRequest> DATA_FEED_REQUESTS = new ArrayList<>();

    // Use RegisterExtension instead of @WireMockTest so we can set up the req listener
    @SuppressWarnings("unused")
    @RegisterExtension
    private static final WireMockExtension WIRE_MOCK_EXTENSION = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().port(DEFAULT_STROOM_PORT))
            .options(WireMockConfiguration.wireMockConfig().extensions(new PostServeAction() {
                @Override
                public String getName() {
                    return "Request logging action";
                }

                @Override
                public void doGlobalAction(final ServeEvent serveEvent, final Admin admin) {
                    super.doGlobalAction(serveEvent, admin);
                    if (IS_REQUEST_LOGGING_ENABLED) {
                        dumpWireMockEvent(serveEvent);
                    }
                    if (serveEvent.getRequest().getUrl().equals(getDataFeedPath())) {
                        captureDataFeedRequest(serveEvent);
                    }
                }
            }))
            .build();

    final LongAdder postToProxyCount = new LongAdder();
    final Client client = DROPWIZARD.client();

    @BeforeEach
    void beforeEach(final WireMockRuntimeInfo wmRuntimeInfo) {
        DATA_FEED_REQUESTS.clear();
        postToProxyCount.reset();
        LOGGER.info("WireMock running on: {}", wmRuntimeInfo.getHttpBaseUrl());
    }

    void setupStroomStubs(Function<MappingBuilder, MappingBuilder> datafeedBuilderFunc) {
        final String feedStatusPath = getFeedStatusPath();
        final GetFeedStatusResponse feedStatusResponse = GetFeedStatusResponse.createOKRecieveResponse();

        final String responseJson;
        try {
            responseJson = OBJECT_MAPPER.writeValueAsString(feedStatusResponse);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error creating json for " + feedStatusResponse);
        }

        WireMock.stubFor(WireMock.post(feedStatusPath)
                .willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(responseJson)));
        LOGGER.info("Setup WireMock POST stub for {}", feedStatusPath);

        final String datafeedPath = getDataFeedPath();
        WireMock.stubFor(datafeedBuilderFunc.apply(WireMock.post(datafeedPath)));
        LOGGER.info("Setup WireMock POST stub for {}", datafeedPath);

        WireMock.stubFor(WireMock.options(UrlPattern.ANY)
                .willReturn(
                        WireMock.aResponse()
                                .withHeader("Allow", "POST")));
        LOGGER.info("Setup WireMock OPTIONS stub for any URL");

        // now the stubs are set up wait for proxy to be ready as proxy needs to stubs to be healthy
        waitForHealthyProxyApp(Duration.ofSeconds(30));
    }

    private static String getFeedStatusPath() {
        return ResourcePaths.buildAuthenticatedApiPath(
                FeedStatusResource.BASE_RESOURCE_PATH,
                FeedStatusResource.GET_FEED_STATUS_PATH_PART);
    }

    public static String getDataFeedPath() {
        return ResourcePaths.buildUnauthenticatedServletPath(ReceiveDataServlet.DATA_FEED_PATH_PART);
    }

    public static void dumpAllWireMockEvents() {
        WireMock.getAllServeEvents().forEach(AbstractEndToEndTest::dumpWireMockEvent);
    }

    public static String getRequestBodyAsString(final LoggedRequest loggedRequest) {
        return getBodyAsString(loggedRequest.getHeaders(), loggedRequest::getBodyAsString, loggedRequest::getBody);

    }

    public static String getResponseBodyAsString(final LoggedResponse loggedResponse) {
        return getBodyAsString(loggedResponse.getHeaders(), loggedResponse::getBodyAsString, loggedResponse::getBody);
    }

    private static String getBodyAsString(final HttpHeaders headers,
                                          final Supplier<String> bodyAsStrSupplier,
                                          final Supplier<byte[]> bodyAsBytesSupplier) {
        final String requestBody;
        if (NullSafe.test(
                headers.getHeader("Compression"),
                header -> header.containsValue("ZIP"))) {

            final byte[] body = bodyAsBytesSupplier.get();
            final ZipArchiveInputStream zipInputStream = new ZipArchiveInputStream(new ByteArrayInputStream(body));
            final List<String> entries = new ArrayList<>();
            while (true) {
                try {
                    final ZipArchiveEntry entry = zipInputStream.getNextZipEntry();
                    if (entry == null) {
                        break;
                    }
                    entries.add(entry.getName() + " (" + entry.getSize() + ")");
                } catch (IOException e) {
                    throw new RuntimeException(LogUtil.message("Error reading zip stream: {}", e.getMessage()), e);
                }
            }
            requestBody = String.join("\n", entries);
        } else {
            requestBody = bodyAsStrSupplier.get();
        }
        return requestBody;
    }

    private static void captureDataFeedRequest(final ServeEvent serveEvent) {
        final LoggedRequest request = serveEvent.getRequest();

        final byte[] body = request.getBody();
        if (body.length > 0) {
            final AttributeMap attributeMap = buildAttributeMap(request);
            final Map<String, DataFeedRequestItem> nameToItemMap = new HashMap<>();
            final StroomStreamProcessor stroomStreamProcessor = new StroomStreamProcessor(
                    attributeMap,
                    (entry, inputStream, progressHandler) -> {
                        final ByteCountInputStream byteCountInputStream = new ByteCountInputStream(inputStream);
                        // Assumes UTF8 content to save us inspecting the meta, fine for testing
                        final String content = new String(byteCountInputStream.readAllBytes(), StandardCharsets.UTF_8);
                        LOGGER.info("""
                                        datafeed entry: {}, content:
                                        -------------------------------------------------
                                        {}
                                        -------------------------------------------------""",
                                entry, content);
                        final String type = FilenameUtils.getExtension(entry);
                        final String baseName = entry.substring(0, entry.indexOf('.'));
                        final DataFeedRequestItem item = new DataFeedRequestItem(
                                entry,
                                baseName,
                                type,
                                content);
                        nameToItemMap.put(entry, item);
                        return byteCountInputStream.getCount();
                    },
                    val -> {
                    });

            stroomStreamProcessor.processInputStream(new ByteArrayInputStream(body), "");

            final DataFeedRequest dataFeedRequest = new DataFeedRequest(attributeMap, nameToItemMap);
            DATA_FEED_REQUESTS.add(dataFeedRequest);
        }
    }

    private static AttributeMap buildAttributeMap(final LoggedRequest loggedRequest) {
        final AttributeMap attributeMap = new AttributeMap();
        loggedRequest.getHeaders().all()
                .forEach(httpHeader -> {
                    attributeMap.put(httpHeader.key(), httpHeader.firstValue());
                });
        return attributeMap;
    }

    private static String getHeaders(final HttpHeaders headers) {
        return IS_HEADER_LOGGING_ENABLED
                ? headers.all().stream()
                .map(httpHeader -> "  * " + httpHeader.toString())
                .collect(Collectors.joining("\n"))
                : "[Header logging disabled using IS_HEADER_LOGGING_ENABLED]";
    }

    public static void dumpWireMockEvent(final ServeEvent serveEvent) {
        final LoggedRequest request = serveEvent.getRequest();
        final String requestHeaders = getHeaders(request.getHeaders());
        final String requestBody = getRequestBodyAsString(request);

        final LoggedResponse response = serveEvent.getResponse();
        final String responseHeaders = getHeaders(response.getHeaders());
        final String responseBody = getResponseBodyAsString(response);

        LOGGER.info("""
                        Received event:
                        --------------------------------------------------------------------------------
                        request: {} {}
                        {}
                        {}
                        --------------------------------------------------------------------------------
                        response: {}
                        {}
                        {}
                        --------------------------------------------------------------------------------""",
                request.getMethod(),
                request.getUrl(),
                requestHeaders,
                requestBody,
                response.getStatus(),
                responseHeaders,
                responseBody);
    }

    public int sendPostToProxyDatafeed(final String feed,
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
            postToProxyCount.increment();
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
                Thread.sleep(50);
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
        return getProxyBaseAppUrl().replaceAll("/$", "") + path;
    }

    static String buildProxyAdminPath(final String path) {
        return getProxyBaseAdminUrl().replaceAll("/$", "") + path;
    }

    /**
     * @return Count of POSTs sent to proxy
     */
    public int getPostsToProxyCount() {
        return postToProxyCount.intValue();
    }

    public int getDataFeedPostsToStroomCount() {
        return getPostsToStroomDataFeed().size();
    }

    public List<LoggedRequest> getPostsToStroomDataFeed() {
        return WireMock.findAll(WireMock.postRequestedFor(WireMock.urlPathEqualTo(getDataFeedPath())));
    }

    public List<GetFeedStatusRequest> getPostsToFeedStatusCheck() {
        return WireMock.findAll(WireMock.postRequestedFor(WireMock.urlPathEqualTo(getFeedStatusPath())))
                .stream()
                .map(req -> extractContent(req, GetFeedStatusRequest.class))
                .toList();
    }

    /**
     * Assert that a http header is present and has this value
     */
    public static void assertHeaderValue(final LoggedRequest loggedRequest,
                                         final String key,
                                         final String value) {
        Assertions.assertThat(loggedRequest.getHeader(key))
                .isNotNull()
                .isEqualTo(value);
    }

    /**
     * @return All requests received by the /datafeed endpoint
     */
    public static List<DataFeedRequest> getDataFeedRequests() {
        return DATA_FEED_REQUESTS;
    }

    private static <T> T extractContent(final LoggedRequest loggedRequest, final Class<T> clazz) {
        // Assume UTF8
        final Charset charset = StandardCharsets.UTF_8;
        final String contentStr;

        try {
            contentStr = new String(loggedRequest.getBody(), charset);
        } catch (Exception e) {
            throw new RuntimeException(LogUtil.message(
                    "Error reading content bytes as {}, error: {}",
                    clazz.getSimpleName(), e.getMessage()), e);
        }

        try {
            return OBJECT_MAPPER.readValue(contentStr, clazz);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(LogUtil.message(
                    "Error de-serialising content to {}, error: {}, content:\n{}",
                    clazz.getSimpleName(), e.getMessage(), contentStr), e);
        }
    }


    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~


    public static class DataFeedRequest {

        private final AttributeMap attributeMap;
        private final Map<String, DataFeedRequestItem> nameToItemMap;

        public DataFeedRequest(final AttributeMap attributeMap,
                               final Map<String, DataFeedRequestItem> nameToItemMap) {
            this.attributeMap = attributeMap;
            this.nameToItemMap = nameToItemMap;
        }

        public Map<String, DataFeedRequestItem> getNameToItemMap() {
            return nameToItemMap;
        }

        public DataFeedRequestItem getItemByType(final String type) {
            return getNthItemByType(type, 0, 1);
        }

        public DataFeedRequestItem getNthItemByType(final String type,
                                                    final int idx,
                                                    final int expectedCount) {
            final List<DataFeedRequestItem> items = nameToItemMap.values()
                    .stream()
                    .filter(item -> item.type.equals(type))
                    .toList();
            if (items.size() > expectedCount) {
                throw new RuntimeException(LogUtil.message("Found {} items of type {}, expected {}",
                        items.size(), type, expectedCount));
            }
            return items.get(idx);
        }
    }


    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~


    public record DataFeedRequestItem(String name,
                                      String baseName,
                                      String type,
                                      String content) {

    }
}
