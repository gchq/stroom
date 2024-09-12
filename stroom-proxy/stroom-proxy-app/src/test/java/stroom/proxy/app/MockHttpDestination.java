/*
 * Copyright 2024 Crown Copyright
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

import stroom.meta.api.AttributeMap;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.app.forwarder.ForwardHttpPostConfig;
import stroom.proxy.app.handler.FeedStatusConfig;
import stroom.proxy.feed.remote.FeedStatus;
import stroom.proxy.feed.remote.GetFeedStatusRequest;
import stroom.proxy.feed.remote.GetFeedStatusResponse;
import stroom.receive.common.FeedStatusResource;
import stroom.receive.common.ReceiveDataServlet;
import stroom.receive.common.StroomStreamProcessor;
import stroom.test.common.TestUtil;
import stroom.util.NullSafe;
import stroom.util.io.ByteCountInputStream;
import stroom.util.json.JsonUtil;
import stroom.util.logging.AsciiTable;
import stroom.util.logging.AsciiTable.Column;
import stroom.util.logging.LogUtil;
import stroom.util.shared.ResourcePaths;

import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.Admin;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.extension.PostServeAction;
import com.github.tomakehurst.wiremock.http.HttpHeaders;
import com.github.tomakehurst.wiremock.http.LoggedResponse;
import com.github.tomakehurst.wiremock.http.MultiValue;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.matching.UrlPattern;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.io.FilenameUtils;
import org.assertj.core.api.Assertions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

public class MockHttpDestination {

    private static final Logger LOGGER = LoggerFactory.getLogger(MockHttpDestination.class);

    private static final int DEFAULT_STROOM_PORT = 8080;

    // Can be changed by subclasses, e.g. if one test is noisy but others are not
    private volatile boolean isRequestLoggingEnabled = true;
    private volatile boolean isHeaderLoggingEnabled = true;


    // Hold all requests send to the wiremock stroom datafeed endpoint
    private final List<DataFeedRequest> dataFeedRequests = new ArrayList<>();

    WireMockExtension createExtension() {
        return WireMockExtension.newInstance()
                .options(WireMockConfiguration.wireMockConfig().port(DEFAULT_STROOM_PORT))
                .options(WireMockConfiguration.wireMockConfig().extensions(new PostServeAction() {
                    @Override
                    public String getName() {
                        return "Request logging action";
                    }

                    @Override
                    public void doGlobalAction(final ServeEvent serveEvent, final Admin admin) {
                        super.doGlobalAction(serveEvent, admin);
                        if (isRequestLoggingEnabled) {
                            dumpWireMockEvent(serveEvent);
                        }
                        if (serveEvent.getRequest().getUrl().equals(getDataFeedPath())) {
                            captureDataFeedRequest(serveEvent);
                        }
                    }
                }))
                .build();
    }

    void setupStroomStubs(Function<MappingBuilder, MappingBuilder> datafeedBuilderFunc) {
        final String feedStatusPath = getFeedStatusPath();
        final GetFeedStatusResponse feedStatusResponse = GetFeedStatusResponse.createOKReceiveResponse();

        final String responseJson;
        try {
            responseJson = JsonUtil.writeValueAsString(feedStatusResponse);
        } catch (RuntimeException e) {
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
    }

    private static String getDataFeedPath() {
        return ResourcePaths.buildUnauthenticatedServletPath(ReceiveDataServlet.DATA_FEED_PATH_PART);
    }

    private void dumpWireMockEvent(final ServeEvent serveEvent) {
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
                        Body:
                        {}
                        --------------------------------------------------------------------------------
                        response: {}
                        {}
                        Body:
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
            final AttributeMap attributeMap = buildAttributeMap(request);
            final List<DataFeedRequestItem> dataFeedRequestItems = new ArrayList<>();
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
                        dataFeedRequestItems.add(item);
                        return byteCountInputStream.getCount();
                    },
                    val -> {
                    });

            stroomStreamProcessor.processInputStream(new ByteArrayInputStream(body), "");

            final DataFeedRequest dataFeedRequest = new DataFeedRequest(dataFeedRequestItems);
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

    private static AttributeMap buildAttributeMap(final LoggedRequest loggedRequest) {
        final AttributeMap attributeMap = new AttributeMap();
        loggedRequest.getHeaders().all()
                .forEach(httpHeader -> attributeMap.put(httpHeader.key(), httpHeader.firstValue()));
        return attributeMap;
    }

    private int getDataFeedPostsToStroomCount() {
        return dataFeedRequests.size();
    }

    List<LoggedRequest> getPostsToStroomDataFeed() {
        return WireMock.findAll(WireMock.postRequestedFor(WireMock.urlPathEqualTo(getDataFeedPath())));
    }

    private List<GetFeedStatusRequest> getPostsToFeedStatusCheck() {
        return WireMock.findAll(WireMock.postRequestedFor(WireMock.urlPathEqualTo(getFeedStatusPath())))
                .stream()
                .map(req -> extractContent(req, GetFeedStatusRequest.class))
                .toList();
    }

    void assertFeedStatusCheck() {
        // Health check sends in a feed status check with DUMMY_FEED to see if stroom is available
        Assertions.assertThat(getPostsToFeedStatusCheck())
                .extracting(GetFeedStatusRequest::getFeedName)
                .filteredOn(feed -> !"DUMMY_FEED".equals(feed))
                .containsExactly(TestConstants.FEED_TEST_EVENTS_1, TestConstants.FEED_TEST_EVENTS_2);
    }

    private static String getFeedStatusPath() {
        return ResourcePaths.buildAuthenticatedApiPath(
                FeedStatusResource.BASE_RESOURCE_PATH,
                FeedStatusResource.GET_FEED_STATUS_PATH_PART);
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

    void clear() {
        dataFeedRequests.clear();
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
            return JsonUtil.readValue(contentStr, clazz);
        } catch (RuntimeException e) {
            throw new RuntimeException(LogUtil.message(
                    "Error de-serialising content to {}, error: {}, content:\n{}",
                    clazz.getSimpleName(), e.getMessage(), contentStr), e);
        }
    }


    void assertPosts() {
        final List<LoggedRequest> postsToStroomDataFeed = getPostsToStroomDataFeed();

        // Check feed names.
        Assertions.assertThat(postsToStroomDataFeed)
                .extracting(req -> req.getHeader(StandardHeaderArguments.FEED.get()))
                .containsExactlyInAnyOrder(
                        TestConstants.FEED_TEST_EVENTS_1,
                        TestConstants.FEED_TEST_EVENTS_2,
                        TestConstants.FEED_TEST_EVENTS_1,
                        TestConstants.FEED_TEST_EVENTS_2);

        // Check zip content file count.
        Assertions.assertThat(dataFeedRequests)
                .hasSize(4);

        // Can't be sure of the order they are sent,
        Assertions.assertThat(dataFeedRequests.stream()
                        .map(dataFeedRequest -> dataFeedRequest.getDataFeedRequestItems().size())
                        .toList())
                .containsExactlyInAnyOrder(7, 7, 3, 3);

        assertDataFeedRequestContent(dataFeedRequests);
    }

    private void assertDataFeedRequestContent(final List<DataFeedRequest> dataFeedRequests) {
        final List<String> expectedFiles = List.of(
                "001.mf",
                "001.meta",
                "001.dat",
                "002.meta",
                "002.dat",
                "003.meta",
                "003.dat",
                "004.meta",
                "004.dat");
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

    static ForwardHttpPostConfig createForwardHttpPostConfig() {
        return ForwardHttpPostConfig.builder()
                .enabled(true)
                .forwardUrl("http://localhost:"
                        + MockHttpDestination.DEFAULT_STROOM_PORT
                        + getDataFeedPath())
                .name("Stroom datafeed")
                .userAgent("Junit test")
                .build();
    }

    static FeedStatusConfig createFeedStatusConfig() {
        return new FeedStatusConfig(
                true,
                FeedStatus.Receive,
                "http://localhost:"
                        + MockHttpDestination.DEFAULT_STROOM_PORT
                        + ResourcePaths.buildAuthenticatedApiPath(FeedStatusResource.BASE_RESOURCE_PATH),
                null,
                null);
    }

    void assertSimpleDataFeedRequestContent(int expected) {
        Assertions.assertThat(dataFeedRequests).hasSize(expected);

        final List<String> expectedFiles = List.of(
                "001.dat",
                "001.meta");
        assertDataFeedRequestContent(dataFeedRequests, expectedFiles);
    }

    void assertRequestCount(int expectedRequestCount) {
        TestUtil.waitForIt(
                this::getDataFeedPostsToStroomCount,
                expectedRequestCount,
                () -> "Forward to stroom datafeed count",
                Duration.ofSeconds(30),
                Duration.ofMillis(50),
                Duration.ofSeconds(1));

        WireMock.verify(expectedRequestCount, WireMock.postRequestedFor(
                WireMock.urlPathEqualTo(getDataFeedPath())));
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


    private static class DataFeedRequest {

        private final List<DataFeedRequestItem> dataFeedRequestItems;

        public DataFeedRequest(final List<DataFeedRequestItem> dataFeedRequestItems) {
            this.dataFeedRequestItems = dataFeedRequestItems;
        }

        public List<DataFeedRequestItem> getDataFeedRequestItems() {
            return dataFeedRequestItems;
        }
    }


    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~


    private record DataFeedRequestItem(String name,
                                       String baseName,
                                       String type,
                                       String content) {

    }
}
