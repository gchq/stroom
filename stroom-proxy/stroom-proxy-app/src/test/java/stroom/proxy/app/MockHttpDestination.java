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

import stroom.data.zip.StroomZipFileType;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.app.handler.FeedStatusConfig;
import stroom.proxy.app.handler.ForwardHttpPostConfig;
import stroom.proxy.app.servlet.ProxyStatusServlet;
import stroom.proxy.feed.remote.GetFeedStatusRequestV2;
import stroom.proxy.feed.remote.GetFeedStatusResponse;
import stroom.proxy.repo.AggregatorConfig;
import stroom.receive.common.FeedStatusResourceV2;
import stroom.receive.common.ReceiveDataServlet;
import stroom.security.shared.ApiKeyCheckResource;
import stroom.security.shared.ApiKeyResource;
import stroom.test.common.TestUtil;
import stroom.util.concurrent.UniqueId;
import stroom.util.date.DateUtil;
import stroom.util.io.ByteCountInputStream;
import stroom.util.io.FileName;
import stroom.util.json.JsonUtil;
import stroom.util.logging.AsciiTable;
import stroom.util.logging.AsciiTable.Column;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResourcePaths;
import stroom.util.time.StroomDuration;

import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.ServeEventListener;
import com.github.tomakehurst.wiremock.http.HttpHeaders;
import com.github.tomakehurst.wiremock.http.LoggedResponse;
import com.github.tomakehurst.wiremock.http.MultiValue;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.matching.UrlPattern;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import jakarta.ws.rs.core.Response.Status;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.assertj.core.api.Assertions;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;

public class MockHttpDestination {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(MockHttpDestination.class);

    private static final int DEFAULT_STROOM_PORT = 8080;

    // Can be changed by subclasses, e.g. if one test is noisy but others are not
    private volatile boolean isRequestLoggingEnabled = true;
    private volatile boolean isHeaderLoggingEnabled = true;

    // Hold all requests send to the wiremock stroom datafeed endpoint
    private final List<DataFeedRequest> dataFeedRequests = new ArrayList<>();

    private final ThreadLocal<Long> responseTimes = new ThreadLocal<>();
    private final AtomicInteger count = new AtomicInteger();

    public WireMockExtension createExtension() {
        return WireMockExtension.newInstance()
                .options(WireMockConfiguration.wireMockConfig().port(DEFAULT_STROOM_PORT))
                .options(WireMockConfiguration.wireMockConfig().extensions(new ServeEventListener() {
                    @Override
                    public String getName() {
                        return "Request logging action";
                    }

                    @Override
                    public void beforeResponseSent(final ServeEvent serveEvent, final Parameters parameters) {
                        responseTimes.set(System.currentTimeMillis());
                    }

                    @Override
                    public void afterComplete(final ServeEvent serveEvent, final Parameters parameters) {
                        try {
                            if (serveEvent.getResponse().getStatus() == 200) {
                                if (isRequestLoggingEnabled) {
                                    dumpWireMockEvent(serveEvent);
                                }
                                if (serveEvent.getRequest().getUrl().equals(getDataFeedPath())) {
                                    captureDataFeedRequest(serveEvent);
                                }
                            } else {
                                LOGGER.error(serveEvent.toString());
                            }

                        } finally {
                            final long startTime = responseTimes.get();
                            responseTimes.remove();
                            LOGGER.info(() -> "Responding with " +
                                              serveEvent.getResponse().getStatus() +
                                              " after " +
                                              Duration.ofMillis(System.currentTimeMillis() - startTime).toString() +
                                              " count = " +
                                              count.incrementAndGet());
                        }
                    }
                }))
                .build();
    }

    public void setupLivenessEndpoint(final boolean isLive) {
        LOGGER.info("Setup WireMock POST stub for {} (isLive: {}", getStatusPath(), isLive);
        if (isLive) {
            setupLivenessEndpoint(mappingBuilder ->
                    mappingBuilder.willReturn(WireMock.ok()));
        } else {
            setupLivenessEndpoint(mappingBuilder ->
                    mappingBuilder.willReturn(WireMock.notFound()));
        }
    }

    private void setupLivenessEndpoint(final Function<MappingBuilder, MappingBuilder> livenessBuilderFunc) {
        final String path = getStatusPath();
        WireMock.stubFor(livenessBuilderFunc.apply(WireMock.get(path)));
    }

    public void setupStroomStubs(final Function<MappingBuilder, MappingBuilder> datafeedBuilderFunc) {
        final String feedStatusPath = getFeedStatusPath();
        final GetFeedStatusResponse feedStatusResponse = GetFeedStatusResponse.createOKReceiveResponse();

        final String responseJson;
        try {
            responseJson = JsonUtil.writeValueAsString(feedStatusResponse);
        } catch (final RuntimeException e) {
            throw new RuntimeException("Error creating json for " + feedStatusResponse);
        }

        WireMock.stubFor(WireMock.post(feedStatusPath)
                .willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(responseJson)));
        LOGGER.info("Setup WireMock POST stub for {}", feedStatusPath);

        final String apiKeyVerificationPath = getApiKeyVerificationPath();

        WireMock.stubFor(WireMock.post(apiKeyVerificationPath)
                .willReturn(WireMock.aResponse()
                        .withStatus(Status.NO_CONTENT.getStatusCode())
                        .withHeader("Content-Type", "application/json")));
        LOGGER.info("Setup WireMock POST stub for {}", apiKeyVerificationPath);

        final String datafeedPath = getDataFeedPath();
        WireMock.stubFor(datafeedBuilderFunc.apply(WireMock.post(datafeedPath)));
        LOGGER.info("Setup WireMock POST stub for {}", datafeedPath);

        WireMock.stubFor(WireMock.options(UrlPattern.ANY)
                .willReturn(
                        WireMock.aResponse()
                                .withHeader("Allow", "POST")));
        LOGGER.info("Setup WireMock OPTIONS stub for any URL");
    }

    private static String getDataFeedPath() {
        return ResourcePaths.buildUnauthenticatedServletPath(ReceiveDataServlet.DATA_FEED_PATH_PART);
    }

    private static String getStatusPath() {
        return ResourcePaths.buildUnauthenticatedServletPath(ProxyStatusServlet.PATH_PART);
    }

    private void dumpWireMockEvent(final ServeEvent serveEvent) {
        final LoggedRequest request = serveEvent.getRequest();
        final String requestHeaders = getHeaders(request.getHeaders());
        final String requestBody = getRequestBodyAsString(request);

        final LoggedResponse response = serveEvent.getResponse();
        final String responseHeaders = getHeaders(response.getHeaders());
        final String responseBody = getResponseBodyAsString(response);

        LOGGER.info("""
                        Received event: (datafeed request count: {})
                        --------------------------------------------------------------------------------
                        request: {} {}
                        {}
                        Body:
                        {}
                        --------------------------------------------------------------------------------
                        response: {}
                        {}
                        Body:
                        {}
                        --------------------------------------------------------------------------------""",
                dataFeedRequests.size(),
                request.getMethod(),
                request.getUrl(),
                requestHeaders,
                requestBody,
                response.getStatus(),
                responseHeaders,
                responseBody);
    }

    private String getHeaders(final HttpHeaders headers) {
        if (headers != null) {
            if (isHeaderLoggingEnabled) {
                return AsciiTable.builder(headers.all()
                                .stream()
                                .sorted(Comparator.comparing(MultiValue::key))
                                .toList())
                        .withColumn(Column.of("Header", MultiValue::key))
                        .withColumn(Column.of("Value", MultiValue::firstValue))
                        .build();
            } else {
                return "[Header logging disabled using IS_HEADER_LOGGING_ENABLED]";
            }
        } else {
            return "";
        }
    }

    private void captureDataFeedRequest(final ServeEvent serveEvent) {
        final LoggedRequest request = serveEvent.getRequest();

        final byte[] body = request.getBody();
        if (body.length > 0) {
            final List<DataFeedRequestItem> dataFeedRequestItems = new ArrayList<>();

            // This may not work for 'instant' forwarding, where the front door req into proxy
            // is forwarded straight to the dest with no store/agg, thus may not be ZIP compressed
            try (final ZipArchiveInputStream zipArchiveInputStream = new ZipArchiveInputStream(new ByteArrayInputStream(
                    body))) {
                ZipArchiveEntry entry = zipArchiveInputStream.getNextEntry();
                while (entry != null) {
                    final ByteCountInputStream byteCountInputStream = new ByteCountInputStream(zipArchiveInputStream);
                    // Assumes UTF8 content to save us inspecting the meta, fine for testing
                    final String content = new String(byteCountInputStream.readAllBytes(), StandardCharsets.UTF_8);
                    LOGGER.info("""
                                    datafeed entry: {}, content:
                                    -------------------------------------------------
                                    {}
                                    -------------------------------------------------""",
                            entry, content);
                    final FileName fileName = FileName.parse(entry.getName());
                    final DataFeedRequestItem item = new DataFeedRequestItem(
                            fileName.getFullName(),
                            fileName.getBaseName(),
                            fileName.getExtension(),
                            content);
                    dataFeedRequestItems.add(item);
                    entry = zipArchiveInputStream.getNextEntry();
                }

            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }


            final AttributeMap attributeMap = new AttributeMap();
            final Set<String> headerNames = serveEvent.getRequest().getHeaders().keys();
            for (final String headerName : headerNames) {
                attributeMap.put(headerName, serveEvent.getRequest().getHeader(headerName));
            }

            final DataFeedRequest dataFeedRequest = new DataFeedRequest(
                    attributeMap, dataFeedRequestItems);
            dataFeedRequests.add(dataFeedRequest);
        }
    }

    private void dumpAllWireMockEvents() {
        WireMock.getAllServeEvents().forEach(this::dumpWireMockEvent);
    }

    private static String getRequestBodyAsString(final LoggedRequest loggedRequest) {
        return getBodyAsString(loggedRequest.getHeaders(), loggedRequest::getBodyAsString, loggedRequest::getBody);

    }

    private static String getResponseBodyAsString(final LoggedResponse loggedResponse) {
        return getBodyAsString(loggedResponse.getHeaders(), loggedResponse::getBodyAsString, loggedResponse::getBody);
    }

    private static String getBodyAsString(final HttpHeaders headers,
                                          final Supplier<String> bodyAsStrSupplier,
                                          final Supplier<byte[]> bodyAsBytesSupplier) {
        final String requestBody;
        if (NullSafe.test(
                headers,
                headers2 -> headers2.getHeader("Compression"),
                header -> header.containsValue("ZIP"))) {

            final byte[] body = bodyAsBytesSupplier.get();
            final ZipArchiveInputStream zipInputStream = new ZipArchiveInputStream(new ByteArrayInputStream(body));
            final List<String> entries = new ArrayList<>();
            while (true) {
                try {
                    final ZipArchiveEntry entry = zipInputStream.getNextEntry();
                    if (entry == null) {
                        break;
                    }
                    entries.add(entry.getName() + " (" + entry.getSize() + ")");
                } catch (final IOException e) {
                    throw new RuntimeException(LogUtil.message("Error reading zip stream: {}", e.getMessage()), e);
                }
            }
            requestBody = String.join("\n", entries);
        } else {
            requestBody = bodyAsStrSupplier.get();
        }
        return requestBody;
    }

    private static AttributeMap buildAttributeMap(final LoggedRequest loggedRequest) {
        final AttributeMap attributeMap = new AttributeMap();
        loggedRequest.getHeaders().all()
                .forEach(httpHeader -> attributeMap.put(httpHeader.key(), httpHeader.firstValue()));
        return attributeMap;
    }

    private int getDataFeedPostsToStroomCount() {
        return dataFeedRequests.size();
    }

    public List<DataFeedRequest> getDataFeedRequests() {
        return dataFeedRequests;
    }

    List<LoggedRequest> getPostsToStroomDataFeed() {
        return WireMock.findAll(WireMock.postRequestedFor(WireMock.urlPathEqualTo(getDataFeedPath())));
    }

    private List<GetFeedStatusRequestV2> getPostsToFeedStatusCheck() {
        return WireMock.findAll(WireMock.postRequestedFor(WireMock.urlPathEqualTo(getFeedStatusPath())))
                .stream()
                .map(req -> extractContent(req, GetFeedStatusRequestV2.class))
                .toList();
    }

    void assertFeedStatusCheckNotCalled() {
        // Health check sends in a feed status check with DUMMY_FEED to see if stroom is available
        Assertions.assertThat(getPostsToFeedStatusCheck())
                .isEmpty();
    }

    void assertFeedStatusCheck() {
        // Health check sends in a feed status check with DUMMY_FEED to see if stroom is available
        Assertions.assertThat(getPostsToFeedStatusCheck())
                .extracting(GetFeedStatusRequestV2::getFeedName)
                .filteredOn(feed -> !"DUMMY_FEED".equals(feed))
                .containsExactly(TestConstants.FEED_TEST_EVENTS_1, TestConstants.FEED_TEST_EVENTS_2);
    }

    private static String getFeedStatusPath() {
        return ResourcePaths.buildAuthenticatedApiPath(
                FeedStatusResourceV2.BASE_RESOURCE_PATH,
                FeedStatusResourceV2.GET_FEED_STATUS_PATH_PART);
    }

    private static String getApiKeyVerificationPath() {
        return ResourcePaths.buildAuthenticatedApiPath(
                ApiKeyResource.BASE_PATH,
                ApiKeyCheckResource.VERIFY_API_KEY_PATH_PART);
    }

    /**
     * Assert that a http header is present and has this value
     */
    void assertHeaderValue(final LoggedRequest loggedRequest,
                           final String key,
                           final String value) {
        Assertions.assertThat(loggedRequest.getHeader(key))
                .isNotNull()
                .isEqualTo(value);
    }

    public void clear() {
        dataFeedRequests.clear();
    }

    private static <T> T extractContent(final LoggedRequest loggedRequest, final Class<T> clazz) {
        // Assume UTF8
        final Charset charset = StandardCharsets.UTF_8;
        final String contentStr;

        try {
            contentStr = new String(loggedRequest.getBody(), charset);
        } catch (final Exception e) {
            throw new RuntimeException(LogUtil.message(
                    "Error reading content bytes as {}, error: {}",
                    clazz.getSimpleName(), e.getMessage()), e);
        }

        try {
            return JsonUtil.readValue(contentStr, clazz);
        } catch (final RuntimeException e) {
            throw new RuntimeException(LogUtil.message(
                    "Error de-serialising content to {}, error: {}, content:\n{}",
                    clazz.getSimpleName(), e.getMessage(), contentStr), e);
        }
    }


    void assertPosts(final int count) {
        final List<LoggedRequest> postsToStroomDataFeed = getPostsToStroomDataFeed();

        // Check feed names.
        final String[] feedNames = new String[count];
        for (int i = 0; i < count; i++) {
            feedNames[i++] = TestConstants.FEED_TEST_EVENTS_1;
            feedNames[i] = TestConstants.FEED_TEST_EVENTS_2;
        }

        // Check feed names.
        Assertions.assertThat(postsToStroomDataFeed)
                .extracting(req -> req.getHeader(StandardHeaderArguments.FEED))
                .containsExactlyInAnyOrder(feedNames);

        // Check zip content file count.
        Assertions.assertThat(dataFeedRequests).hasSize(count);

        final Integer[] sizes = new Integer[count];
        Arrays.fill(sizes, 6);
        sizes[sizes.length - 1] = 2;
        sizes[sizes.length - 2] = 2;

        // Can't be sure of the order they are sent,
        Assertions.assertThat(dataFeedRequests.stream()
                        .map(dataFeedRequest -> dataFeedRequest.getDataFeedRequestItems().size())
                        .toList())
                .containsExactlyInAnyOrder(sizes);

        assertDataFeedRequestContent(dataFeedRequests);
    }

    private void assertDataFeedRequestContent(final List<DataFeedRequest> dataFeedRequests) {
        final List<String> expectedFiles = List.of(
                "0000000001.meta",
                "0000000001.dat",
                "0000000002.meta",
                "0000000002.dat",
                "0000000003.meta",
                "0000000003.dat",
                "0000000004.meta",
                "0000000004.dat");
        assertDataFeedRequestContent(dataFeedRequests, expectedFiles);
    }

    private void assertDataFeedRequestContent(final List<DataFeedRequest> dataFeedRequests,
                                              final List<String> expectedFiles) {
        dataFeedRequests.forEach(dataFeedRequest -> {
            for (int i = 0; i < dataFeedRequest.getDataFeedRequestItems().size(); i++) {
                final DataFeedRequestItem zipItem = dataFeedRequest.getDataFeedRequestItems().get(i);
                final String expectedName = expectedFiles.get(i);
                final String actualName = zipItem.baseName() + "." + zipItem.type();
                Assertions.assertThat(actualName).isEqualTo(expectedName);
                Assertions.assertThat(zipItem.content().length()).isGreaterThan(1);
            }
        });
    }

//    public void setHeaderLoggingEnabled(final boolean headerLoggingEnabled) {
//        isHeaderLoggingEnabled = headerLoggingEnabled;
//    }
//
//    public void setRequestLoggingEnabled(final boolean requestLoggingEnabled) {
//        isRequestLoggingEnabled = requestLoggingEnabled;
//    }

    public static ForwardHttpPostConfig createForwardHttpPostConfig(final boolean instant) {
        return ForwardHttpPostConfig.builder()
                .enabled(true)
                .instant(instant)
//                .forwardUrl("http://localhost:"
//                            + MockHttpDestination.DEFAULT_STROOM_PORT
//                            + getDataFeedPath())
                .name("Mock Stroom datafeed")
                // If stroom hits this before the stub is created, then it thinks it is non-live
                // so take ages to notice it is live once the stub is in place
                .livenessCheckEnabled(false)
                .build();
    }


    public static String getLivenessCheckUrl() {
        return "http://localhost:"
               + MockHttpDestination.DEFAULT_STROOM_PORT
               + getStatusPath();
    }

    static FeedStatusConfig createFeedStatusConfig() {
        return new FeedStatusConfig(null, null);
//        return new FeedStatusConfig(
////                true,
////                FeedStatus.Receive,
//                "http://localhost:"
//                + MockHttpDestination.DEFAULT_STROOM_PORT
//                + getFeedStatusPath(),
////                null,
//                null);
    }

    public static DownstreamHostConfig createDownstreamHostConfig() {
        return DownstreamHostConfig.builder()
                .withEnabled(true)
                .withScheme("http")
                .withHostname("localhost")
                .withPort(MockHttpDestination.DEFAULT_STROOM_PORT)
                .build();
    }

    void assertSimpleDataFeedRequestContent(final int expected) {
        Assertions.assertThat(dataFeedRequests).hasSize(expected);

        final List<String> expectedFiles = List.of(
                "001.dat",
                "001.meta");
        assertDataFeedRequestContent(dataFeedRequests, expectedFiles);
    }

    void assertRequestCount(final int expectedRequestCount) {
        TestUtil.waitForIt(
                this::getDataFeedPostsToStroomCount,
                expectedRequestCount,
                () -> "Forward to stroom datafeed POST count",
                Duration.ofMinutes(1),
                Duration.ofMillis(100),
                Duration.ofSeconds(1));

        WireMock.verify(expectedRequestCount, WireMock.postRequestedFor(
                WireMock.urlPathEqualTo(getDataFeedPath())));
    }

    void assertReceivedItemCount(final int count) {
        TestUtil.waitForIt(
                () -> {
                    final long actualCount = getDataFeedRequests()
                            .stream()
                            .map(DataFeedRequest::getDataFeedRequestItems)
                            .flatMap(Collection::stream)
                            .filter(item -> item.type.equals(StroomZipFileType.META.getExtension()))
                            .count();
                    return actualCount;
                },
                (long) count,
                () -> "Received item count",
                Duration.ofMinutes(1),
                Duration.ofMillis(100),
                Duration.ofSeconds(1));

        final long actualCount = getDataFeedRequests()
                .stream()
                .map(DataFeedRequest::getDataFeedRequestItems)
                .flatMap(Collection::stream)
                .filter(item -> item.type.equals(StroomZipFileType.META.getExtension()))
                .count();
        Assertions.assertThat(actualCount)
                .isEqualTo(count);
    }

    /**
     * Assert all the {@link UniqueId}s contained in the stored aggregates
     */
    void assertReceiptIds(final List<UniqueId> expectedReceiptIds) {
        final List<UniqueId> actualReceiptIds = getDataFeedRequests()
                .stream()
                .map(DataFeedRequest::getContainedReceiptIds)
                .flatMap(Collection::stream)
                .toList();

        Assertions.assertThat(actualReceiptIds)
                .containsExactlyInAnyOrderElementsOf(expectedReceiptIds);
    }

    void assertMaxItemsPerAggregate(final Config config) {
        final List<DataFeedRequest> forwardFiles = getDataFeedRequests();
        final List<Long> aggItemCounts = forwardFiles.stream()
                .map(DataFeedRequest::getDataFeedRequestItems)
                .map(zipItems -> zipItems.stream()
                        .filter(zipItem ->
                                zipItem.type().equals(StroomZipFileType.META.getExtension()))
                        .count())
                .toList();

        final AggregatorConfig aggregatorConfig = config.getProxyConfig().getAggregatorConfig();
        final int maxItemsPerAggregate = aggregatorConfig.getMaxItemsPerAggregate();

        // Each agg should be no bigger than configured max
        for (final Long itemCount : aggItemCounts) {
            Assertions.assertThat(itemCount)
                    .isLessThanOrEqualTo(maxItemsPerAggregate);
        }

        final StroomDuration aggregationFrequency = aggregatorConfig.getAggregationFrequency();
        final List<Duration> aggAges = forwardFiles.stream()
                .map(DataFeedRequest::getDataFeedRequestItems)
                .map(zipItems -> {
                    final LongSummaryStatistics stats = zipItems.stream()
                            .filter(zipItem ->
                                    zipItem.type().equals(StroomZipFileType.META.getExtension()))
                            .map(zipItem -> zipItem.getContentAsAttributeMap()
                                    .get(StandardHeaderArguments.RECEIVED_TIME))
                            .mapToLong(DateUtil::parseNormalDateTimeString)
                            .summaryStatistics();
                    return Duration.between(
                            Instant.ofEpochMilli(stats.getMin()),
                            Instant.ofEpochMilli(stats.getMax()));
                })
                .toList();

        // Each agg should have a receipt time range no wider than the configured max agg age
        for (final Duration aggAge : aggAges) {
            Assertions.assertThat(aggAge)
                    .isLessThanOrEqualTo(aggregationFrequency.getDuration());
        }
    }

//    private void assertDataFeedRequestContent(final List<DataFeedRequest> dataFeedRequests,
//                                              final List<String> expectedFiles) {
//        dataFeedRequests.forEach(dataFeedRequest -> {
//            for (int i = 0; i < dataFeedRequest.getDataFeedRequestItems().size(); i++) {
//                final DataFeedRequestItem zipItem = dataFeedRequest.getDataFeedRequestItems().get(i);
//                final String expectedName = expectedFiles.get(i);
//                final String actualName = zipItem.baseName() + "." + zipItem.type();
//                Assertions.assertThat(actualName).isEqualTo(expectedName);
//                Assertions.assertThat(zipItem.content().length()).isGreaterThan(1);
//            }
//        });
//    }

    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~


    public static class DataFeedRequest {

        private final List<DataFeedRequestItem> dataFeedRequestItems;
        private final AttributeMap attributeMap;

        public DataFeedRequest(final AttributeMap attributeMap,
                               final List<DataFeedRequestItem> dataFeedRequestItems) {
            this.dataFeedRequestItems = dataFeedRequestItems;
            this.attributeMap = attributeMap;
        }

        public List<DataFeedRequestItem> getDataFeedRequestItems() {
            return dataFeedRequestItems;
        }

        public AttributeMap getAttributeMap() {
            return attributeMap;
        }

        /**
         * The {@link UniqueId} of the wrapping zip
         */
        public UniqueId getReceiptId() {
            return NullSafe.get(
                    attributeMap.get(StandardHeaderArguments.RECEIPT_ID),
                    UniqueId::parse);
        }

        public List<UniqueId> getContainedReceiptIds() {
            return dataFeedRequestItems.stream()
                    .filter(item ->
                            item.type().equals(StroomZipFileType.META.getExtension()))
                    .map(DataFeedRequestItem::getContentAsAttributeMap)
                    .map(attributeMap ->
                            attributeMap.get(StandardHeaderArguments.RECEIPT_ID))
                    .map(UniqueId::parse)
                    .toList();
        }
    }


    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~


    public record DataFeedRequestItem(String name,
                                      String baseName,
                                      String type,
                                      String content) {

        public AttributeMap getContentAsAttributeMap() {
            if (StroomZipFileType.META.getExtension().equals(type)) {
                return AttributeMapUtil.create(content);
            } else {
                throw new UnsupportedOperationException(LogUtil.message(
                        "Can't convert {} to an AttributeMap", type));
            }
        }

    }
}
