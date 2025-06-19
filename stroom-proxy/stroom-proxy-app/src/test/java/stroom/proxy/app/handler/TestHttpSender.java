package stroom.proxy.app.handler;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.StroomStatusCode;
import stroom.proxy.app.MockHttpDestination;
import stroom.proxy.repo.LogStream;
import stroom.proxy.repo.LogStream.EventType;
import stroom.proxy.repo.ProxyServices;
import stroom.security.api.UserIdentityFactory;
import stroom.test.common.MockMetrics;
import stroom.test.common.TestResourceLocks;
import stroom.util.io.CommonDirSetup;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.apache.commons.io.IOUtils;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

@ResourceLock(TestResourceLocks.STROOM_APP_PORT_8080)
@ExtendWith(MockitoExtension.class)
class TestHttpSender {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestHttpSender.class);

    @Mock
    private LogStream mockLogStream;
    @Mock
    private UserIdentityFactory mockUserIdentityFactory;
    @Mock
    private HttpClient mockHttpClient;
    @Mock
    private ClassicHttpResponse mockHttpResponse;
    @Mock
    private HttpEntity mockHttpEntity;
    @Mock
    private ProxyServices mockProxyServices;

    final MockHttpDestination mockHttpDestination = new MockHttpDestination();
    // Use RegisterExtension instead of @WireMockTest so we can set up the req listener
    @SuppressWarnings("unused")
    @RegisterExtension
    public final WireMockExtension wireMockExtension = mockHttpDestination.createExtension();

    static {
        CommonDirSetup.setup();
    }

    @BeforeEach
    void setup() {
//        mockHttpDestination.clear();
    }

    @Test
    void testSend() throws IOException, ProtocolException, ForwardException {
        final ForwardHttpPostConfig config = MockHttpDestination.createForwardHttpPostConfig(false);
        final StroomStatusCode stroomStatusCode = StroomStatusCode.OK;
        final String receiptId = "my-receipt-id";
        Mockito.when(mockUserIdentityFactory.getServiceUserAuthHeaders())
                .thenReturn(Collections.emptyMap());
        Mockito.when(mockHttpResponse.getCode())
                .thenReturn(stroomStatusCode.getHttpCode());
//        Mockito.when(mockHeader.getValue()).thenReturn(String.valueOf(stroomStatusCode.getCode()));
        Mockito.doAnswer(
                        invocation -> {
                            final String header = invocation.getArgument(0, String.class);
                            return switch (header) {
                                case StandardHeaderArguments.STROOM_ERROR -> {
                                    //noinspection RedundantLabeledSwitchRuleCodeBlock // Stops formatter going nuts
                                    yield TestHeader.of(stroomStatusCode.getCode()
                                                        + " - "
                                                        + stroomStatusCode.getMessage());
                                }
                                case StandardHeaderArguments.STROOM_STATUS ->
                                        TestHeader.of(String.valueOf(stroomStatusCode.getCode()));
                                default -> throw new RuntimeException("Unexpected header " + header);
                            };
                        })
                .when(mockHttpResponse).getHeader(Mockito.anyString());

//        Mockito.when(mockHttpResponse.getHeader(Mockito.eq(StandardHeaderArguments.STROOM_STATUS)))
//                .thenReturn(mockHeader);
        Mockito.when(mockHttpResponse.getEntity())
                .thenReturn(mockHttpEntity);
        Mockito.when(mockHttpEntity.getContent())
                .thenReturn(IOUtils.toInputStream(receiptId, StandardCharsets.UTF_8));

        Mockito.doAnswer(
                        invocation -> {
                            final HttpClientResponseHandler<?> responseHandler = invocation.getArgument(
                                    1, HttpClientResponseHandler.class);
                            return responseHandler.handleResponse(mockHttpResponse);
                        })
                .when(mockHttpClient)
                .execute(Mockito.any(HttpPost.class), Mockito.any(HttpClientResponseHandler.class));

        final HttpSender httpSender = new HttpSender(
                mockLogStream,
                config,
                "my-user-agent",
                mockUserIdentityFactory,
                mockHttpClient,
                new MockMetrics(),
                mockProxyServices);
        final AttributeMap attributeMap = new AttributeMap(Map.of(
                StandardHeaderArguments.FEED, "MY_FEED"
        ));
        final InputStream inputStream = IOUtils.toInputStream("my payload", StandardCharsets.UTF_8);
        httpSender.send(attributeMap, inputStream);

        Mockito.verify(mockLogStream, Mockito.times(1))
                .log(Mockito.any(Logger.class),
                        Mockito.any(AttributeMap.class),
                        Mockito.eq(EventType.SEND),
                        Mockito.eq(config.getForwardUrl()),
                        Mockito.eq(stroomStatusCode),
                        Mockito.eq(receiptId),
                        Mockito.anyLong(),
                        Mockito.anyLong());
    }

    @Test
    void testSend_withDelay() throws IOException, ProtocolException, ForwardException {
        final ForwardHttpPostConfig config = ForwardHttpPostConfig.builder()
                .enabled(true)
                .instant(false)
                .forwardUrl("http://notused:8080/datafeed")
                .name("Mock Stroom datafeed")
//                .forwardDelay(StroomDuration.ofMillis(500))
                .build();
        final StroomStatusCode stroomStatusCode = StroomStatusCode.OK;
        final String receiptId = "my-receipt-id";
        Mockito.when(mockUserIdentityFactory.getServiceUserAuthHeaders())
                .thenReturn(Collections.emptyMap());
        Mockito.when(mockHttpResponse.getCode())
                .thenReturn(stroomStatusCode.getHttpCode());
//        Mockito.when(mockHeader.getValue()).thenReturn(String.valueOf(stroomStatusCode.getCode()));
        Mockito.doAnswer(
                        invocation -> {
                            final String header = invocation.getArgument(0, String.class);
                            return switch (header) {
                                case StandardHeaderArguments.STROOM_ERROR -> {
                                    //noinspection RedundantLabeledSwitchRuleCodeBlock // Stops formatter going nuts
                                    yield TestHeader.of(stroomStatusCode.getCode()
                                                        + " - "
                                                        + stroomStatusCode.getMessage());
                                }
                                case StandardHeaderArguments.STROOM_STATUS ->
                                        TestHeader.of(String.valueOf(stroomStatusCode.getCode()));
                                default -> throw new RuntimeException("Unexpected header " + header);
                            };
                        })
                .when(mockHttpResponse).getHeader(Mockito.anyString());

//        Mockito.when(mockHttpResponse.getHeader(Mockito.eq(StandardHeaderArguments.STROOM_STATUS)))
//                .thenReturn(mockHeader);
        Mockito.when(mockHttpResponse.getEntity())
                .thenReturn(mockHttpEntity);
        Mockito.when(mockHttpEntity.getContent())
                .thenReturn(IOUtils.toInputStream(receiptId, StandardCharsets.UTF_8));

        Mockito.doAnswer(
                        invocation -> {
                            final HttpClientResponseHandler<?> responseHandler = invocation.getArgument(
                                    1, HttpClientResponseHandler.class);
                            return responseHandler.handleResponse(mockHttpResponse);
                        })
                .when(mockHttpClient)
                .execute(Mockito.any(HttpPost.class), Mockito.any(HttpClientResponseHandler.class));

        final HttpSender httpSender = new HttpSender(
                mockLogStream,
                config,
                "my-user-agent",
                mockUserIdentityFactory,
                mockHttpClient,
                new MockMetrics(),
                mockProxyServices);
        final AttributeMap attributeMap = new AttributeMap(Map.of(
                StandardHeaderArguments.FEED, "MY_FEED"
        ));
        final InputStream inputStream = IOUtils.toInputStream("my payload", StandardCharsets.UTF_8);
        httpSender.send(attributeMap, inputStream);

        Mockito.verify(mockLogStream, Mockito.times(1))
                .log(Mockito.any(Logger.class),
                        Mockito.any(AttributeMap.class),
                        Mockito.eq(EventType.SEND),
                        Mockito.eq(config.getForwardUrl()),
                        Mockito.eq(stroomStatusCode),
                        Mockito.eq(receiptId),
                        Mockito.anyLong(),
                        Mockito.anyLong());
    }

    @Test
    void testRejected() throws IOException, ProtocolException {
        final ForwardHttpPostConfig config = MockHttpDestination.createForwardHttpPostConfig(false);
        final StroomStatusCode stroomStatusCode = StroomStatusCode.FEED_IS_NOT_SET_TO_RECEIVE_DATA;
        final String receiptId = "my-receipt-id";
        Mockito.when(mockUserIdentityFactory.getServiceUserAuthHeaders())
                .thenReturn(Collections.emptyMap());
        Mockito.when(mockHttpResponse.getCode())
                .thenReturn(stroomStatusCode.getHttpCode());
//        Mockito.when(mockHeader.getValue()).thenReturn(String.valueOf(stroomStatusCode.getCode()));
        Mockito.doAnswer(
                        invocation -> {
                            final String headerKey = invocation.getArgument(0, String.class);
                            final Header header = switch (headerKey) {
                                case StandardHeaderArguments.STROOM_ERROR -> {
                                    //noinspection RedundantLabeledSwitchRuleCodeBlock // Stops formatter going nuts
                                    yield TestHeader.of(stroomStatusCode.getCode()
                                                        + " - "
                                                        + stroomStatusCode.getMessage());
                                }
                                case StandardHeaderArguments.STROOM_STATUS ->
                                        TestHeader.of(String.valueOf(stroomStatusCode.getCode()));
                                default -> throw new RuntimeException("Unexpected header " + headerKey);
                            };
                            LOGGER.info("headerKey: {}, header: {}", headerKey, header);
                            return header;
                        })
                .when(mockHttpResponse).getHeader(Mockito.anyString());

//        Mockito.when(mockHttpResponse.getHeader(Mockito.eq(StandardHeaderArguments.STROOM_STATUS)))
//                .thenReturn(mockHeader);
        Mockito.when(mockHttpResponse.getEntity())
                .thenReturn(mockHttpEntity);
        Mockito.when(mockHttpEntity.getContent())
                .thenReturn(IOUtils.toInputStream(receiptId, StandardCharsets.UTF_8));

        Mockito.doAnswer(
                        invocation -> {
                            final HttpClientResponseHandler<?> responseHandler = invocation.getArgument(
                                    1, HttpClientResponseHandler.class);
                            return responseHandler.handleResponse(mockHttpResponse);
                        })
                .when(mockHttpClient)
                .execute(Mockito.any(HttpPost.class), Mockito.any(HttpClientResponseHandler.class));

        final HttpSender httpSender = new HttpSender(
                mockLogStream,
                config,
                "my-user-agent",
                mockUserIdentityFactory,
                mockHttpClient,
                new MockMetrics(),
                mockProxyServices);
        final AttributeMap attributeMap = new AttributeMap(Map.of(
                StandardHeaderArguments.FEED, "MY_FEED"
        ));
        final InputStream inputStream = IOUtils.toInputStream("my payload", StandardCharsets.UTF_8);

        Assertions.assertThatThrownBy(
                        () -> {
                            httpSender.send(attributeMap, inputStream);
                        })
                .isInstanceOf(ForwardException.class)
                .extracting(ex -> ((ForwardException) ex).isRecoverable())
                .isEqualTo(false);

        Mockito.verify(mockLogStream, Mockito.times(1))
                .log(Mockito.any(Logger.class),
                        Mockito.any(AttributeMap.class),
                        Mockito.eq(EventType.ERROR),
                        Mockito.eq(config.getForwardUrl()),
                        Mockito.eq(stroomStatusCode),
                        Mockito.eq(receiptId),
                        Mockito.anyLong(),
                        Mockito.anyLong());
    }

    @Test
    void testUnknownError() throws IOException, ProtocolException {
        final ForwardHttpPostConfig config = MockHttpDestination.createForwardHttpPostConfig(false);
        final StroomStatusCode stroomStatusCode = StroomStatusCode.UNKNOWN_ERROR;
        final String receiptId = "my-receipt-id";
        Mockito.when(mockUserIdentityFactory.getServiceUserAuthHeaders())
                .thenReturn(Collections.emptyMap());
        Mockito.when(mockHttpResponse.getCode())
                .thenReturn(stroomStatusCode.getHttpCode());
        Mockito.doAnswer(
                        invocation -> {
                            final String header = invocation.getArgument(0, String.class);
                            return switch (header) {
                                case StandardHeaderArguments.STROOM_ERROR -> {
                                    //noinspection RedundantLabeledSwitchRuleCodeBlock // Stops formatter going nuts
                                    yield TestHeader.of(stroomStatusCode.getCode()
                                                        + " - "
                                                        + stroomStatusCode.getMessage());
                                }
                                case StandardHeaderArguments.STROOM_STATUS ->
                                        TestHeader.of(String.valueOf(stroomStatusCode.getCode()));
                                default -> throw new RuntimeException("Unexpected header " + header);
                            };
                        })
                .when(mockHttpResponse).getHeader(Mockito.anyString());

        Mockito.when(mockHttpResponse.getEntity())
                .thenReturn(mockHttpEntity);
        Mockito.when(mockHttpEntity.getContent())
                .thenReturn(IOUtils.toInputStream(receiptId, StandardCharsets.UTF_8));

        Mockito.doAnswer(
                        invocation -> {
                            final HttpClientResponseHandler<?> responseHandler = invocation.getArgument(
                                    1, HttpClientResponseHandler.class);
                            return responseHandler.handleResponse(mockHttpResponse);
                        })
                .when(mockHttpClient)
                .execute(Mockito.any(HttpPost.class), Mockito.any(HttpClientResponseHandler.class));

        final HttpSender httpSender = new HttpSender(
                mockLogStream,
                config,
                "my-user-agent",
                mockUserIdentityFactory,
                mockHttpClient,
                new MockMetrics(),
                mockProxyServices);
        final AttributeMap attributeMap = new AttributeMap(Map.of(
                StandardHeaderArguments.FEED, "MY_FEED"
        ));
        final InputStream inputStream = IOUtils.toInputStream("my payload", StandardCharsets.UTF_8);

        Assertions.assertThatThrownBy(
                        () -> {
                            httpSender.send(attributeMap, inputStream);
                        })
                .isInstanceOf(ForwardException.class)
                .extracting(ex -> ((ForwardException) ex).isRecoverable())
                .isEqualTo(true);

        Mockito.verify(mockLogStream, Mockito.times(1))
                .log(Mockito.any(Logger.class),
                        Mockito.any(AttributeMap.class),
                        Mockito.eq(EventType.ERROR),
                        Mockito.eq(config.getForwardUrl()),
                        Mockito.eq(stroomStatusCode),
                        Mockito.eq(receiptId),
                        Mockito.anyLong(),
                        Mockito.anyLong());
    }

    @Test
    void testHttpClientError() throws IOException, ProtocolException {
        final ForwardHttpPostConfig config = MockHttpDestination.createForwardHttpPostConfig(false);
        final StroomStatusCode stroomStatusCode = StroomStatusCode.UNKNOWN_ERROR;
        final String errorMsg = "ERROR in HttpClient";
        Mockito.when(mockUserIdentityFactory.getServiceUserAuthHeaders())
                .thenReturn(Collections.emptyMap());
        Mockito.doAnswer(
                        invocation -> {
                            throw new RuntimeException(errorMsg);
                        })
                .when(mockHttpClient).execute(
                        Mockito.any(HttpPost.class),
                        Mockito.any(HttpClientResponseHandler.class));

        final HttpSender httpSender = new HttpSender(
                mockLogStream,
                config,
                "my-user-agent",
                mockUserIdentityFactory,
                mockHttpClient,
                new MockMetrics(),
                mockProxyServices);
        final AttributeMap attributeMap = new AttributeMap(Map.of(
                StandardHeaderArguments.FEED, "MY_FEED"
        ));
        final InputStream inputStream = IOUtils.toInputStream("my payload", StandardCharsets.UTF_8);

        Assertions.assertThatThrownBy(
                        () -> {
                            httpSender.send(attributeMap, inputStream);
                        })
                .isInstanceOf(ForwardException.class)
                .extracting(ex -> ((ForwardException) ex).isRecoverable())
                .isEqualTo(true);

        Mockito.verify(mockLogStream, Mockito.times(1))
                .log(Mockito.any(Logger.class),
                        Mockito.any(AttributeMap.class),
                        Mockito.eq(EventType.ERROR),
                        Mockito.eq(config.getForwardUrl()),
                        Mockito.eq(stroomStatusCode),
                        Mockito.eq(null),
                        Mockito.anyLong(),
                        Mockito.anyLong(),
                        Mockito.contains(errorMsg));
    }

    @Test
    void testLiveness_wiremock_noUrl() {
        final ForwardHttpPostConfig config = MockHttpDestination.createForwardHttpPostConfig(false)
                .copy()
                .livenessCheckUrl(null)
                .build();

        mockHttpDestination.setupLivenessEndpoint(mappingBuilder ->
                mappingBuilder.willReturn(WireMock.ok()));

        final HttpClient httpClient = buildRealHttpClient();

        final HttpSender httpSender = new HttpSender(
                mockLogStream,
                config,
                "my-user-agent",
                mockUserIdentityFactory,
                httpClient,
                new MockMetrics(),
                mockProxyServices);

        assertLivenessCheck(httpSender, true);
    }

    @Test
    void testLiveness_wiremock_live() {
        final ForwardHttpPostConfig config = MockHttpDestination.createForwardHttpPostConfig(false)
                .copy()
                .livenessCheckUrl(MockHttpDestination.getLivenessCheckUrl())
                .build();

        mockHttpDestination.setupLivenessEndpoint(mappingBuilder ->
                mappingBuilder.willReturn(WireMock.ok()));

        final HttpClient httpClient = buildRealHttpClient();

        final HttpSender httpSender = new HttpSender(
                mockLogStream,
                config,
                "my-user-agent",
                mockUserIdentityFactory,
                httpClient,
                new MockMetrics(),
                mockProxyServices);

        assertLivenessCheck(httpSender, true);
    }

    @Test
    void testLiveness_wiremock_notLive() {
        final ForwardHttpPostConfig config = MockHttpDestination.createForwardHttpPostConfig(false)
                .copy()
                .livenessCheckUrl(MockHttpDestination.getLivenessCheckUrl())
                .build();

        mockHttpDestination.setupLivenessEndpoint(mappingBuilder ->
                mappingBuilder.willReturn(WireMock.notFound()));

        final HttpClient httpClient = buildRealHttpClient();

        final HttpSender httpSender = new HttpSender(
                mockLogStream,
                config,
                "my-user-agent",
                mockUserIdentityFactory,
                httpClient,
                new MockMetrics(),
                mockProxyServices);

        assertLivenessCheck(httpSender, false);
    }

    private HttpClient buildRealHttpClient() {
        return HttpClientBuilder.create()
                .build();
    }

    private void assertLivenessCheck(final HttpSender httpSender, final boolean isLive) {
        try {
            Assertions.assertThat(httpSender.performLivenessCheck())
                    .isEqualTo(isLive);
        } catch (final Exception e) {
            if (isLive) {
                Assertions.fail(LogUtil.message("Expecting {} to be live", httpSender));
            }
        }
    }


    // --------------------------------------------------------------------------------


    private record TestHeader(String val) implements Header {

        private static TestHeader of(final String value) {
            return new TestHeader(value);
        }

        @Override
        public boolean isSensitive() {
            return false;
        }

        @Override
        public String getName() {
            return null;
        }

        @Override
        public String getValue() {
            return val;
        }

        @Override
        public String toString() {
            return getValue();
        }
    }
}
