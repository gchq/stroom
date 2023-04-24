package stroom.proxy.app;

import stroom.meta.api.AttributeMap;
import stroom.proxy.app.AbstractEndToEndTest.DataFeedRequest;
import stroom.proxy.app.AbstractEndToEndTest.DataFeedRequestItem;
import stroom.receive.common.ReceiveDataServlet;
import stroom.receive.common.StroomStreamProcessor;
import stroom.util.NullSafe;
import stroom.util.io.ByteCountInputStream;
import stroom.util.logging.AsciiTable;
import stroom.util.logging.AsciiTable.Column;
import stroom.util.logging.LogUtil;
import stroom.util.shared.ResourcePaths;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.Admin;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.extension.PostServeAction;
import com.github.tomakehurst.wiremock.http.HttpHeaders;
import com.github.tomakehurst.wiremock.http.LoggedResponse;
import com.github.tomakehurst.wiremock.http.MultiValue;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.io.FilenameUtils;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

public class WireMockProxyDestination {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractEndToEndTest.class);

    static final int DEFAULT_STROOM_PORT = 8080;

    // Can be changed by subclasses, e.g. if one test is noisy but others are not
    protected volatile boolean isRequestLoggingEnabled = true;
    protected volatile boolean isHeaderLoggingEnabled = true;

    // Hold all requests send to the wiremock stroom datafeed endpoint
    private final List<DataFeedRequest> dataFeedRequests = new ArrayList<>();

    // Use RegisterExtension instead of @WireMockTest so we can set up the req listener
    @SuppressWarnings("unused")
    @RegisterExtension
    private final WireMockExtension wireMockExtension = WireMockExtension.newInstance()
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

    public static String getDataFeedPath() {
        return ResourcePaths.buildUnauthenticatedServletPath(ReceiveDataServlet.DATA_FEED_PATH_PART);
    }

    public void dumpWireMockEvent(final ServeEvent serveEvent) {
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

    public void dumpAllWireMockEvents() {
        WireMock.getAllServeEvents().forEach(this::dumpWireMockEvent);
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
                .forEach(httpHeader -> {
                    attributeMap.put(httpHeader.key(), httpHeader.firstValue());
                });
        return attributeMap;
    }
}
