/*
 * Copyright 2026 Crown Copyright
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

package stroom.contentstore.impl;

import com.sun.net.httpserver.HttpServer;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class TestIconPassthroughServlet {

    private static final byte[] SVG_BYTES = "<svg xmlns=\"http://www.w3.org/2000/svg\"/>"
            .getBytes(StandardCharsets.UTF_8);
    private static final byte[] HTML_BYTES = "<html><script>alert(1)</script></html>"
            .getBytes(StandardCharsets.UTF_8);

    @Mock
    private HttpServletRequest mockRequest;
    @Mock
    private HttpServletResponse mockResponse;

    private HttpServer httpServer;
    private String baseUrl;
    private ByteArrayOutputStream responseBody;

    @BeforeEach
    void setUp() throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        httpServer.createContext("/icon.svg", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "image/svg+xml");
            exchange.sendResponseHeaders(200, SVG_BYTES.length);
            try (final OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(SVG_BYTES);
            }
        });
        httpServer.createContext("/page.html", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "text/html");
            exchange.sendResponseHeaders(200, HTML_BYTES.length);
            try (final OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(HTML_BYTES);
            }
        });
        httpServer.createContext("/redirect-to-icon", exchange -> {
            exchange.getResponseHeaders().add("Location", "/icon.svg");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        httpServer.createContext("/redirect-to-metadata", exchange -> {
            exchange.getResponseHeaders().add("Location", "http://169.254.169.254/latest/meta-data/");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        httpServer.createContext("/redirect-loop", exchange -> {
            exchange.getResponseHeaders().add("Location", "/redirect-loop");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        httpServer.start();
        baseUrl = "http://" + InetAddress.getLoopbackAddress().getHostAddress()
                  + ":" + httpServer.getAddress().getPort();
        responseBody = new ByteArrayOutputStream();
    }

    @AfterEach
    void tearDown() {
        httpServer.stop(0);
    }

    @Test
    void testDoGet_imageIsPassedThroughWithSecurityHeaders() throws IOException {
        stubOutputStream();
        doGet(baseUrl + "/icon.svg");

        assertThat(responseBody.toByteArray())
                .isEqualTo(SVG_BYTES);
        Mockito.verify(mockResponse).setHeader("Content-Type", "image/svg+xml");
        Mockito.verify(mockResponse).setHeader(
                IconPassthroughServlet.CONTENT_TYPE_OPTIONS_HEADER,
                IconPassthroughServlet.CONTENT_TYPE_OPTIONS_NOSNIFF);
        Mockito.verify(mockResponse).setHeader(
                IconPassthroughServlet.CONTENT_SECURITY_POLICY_HEADER,
                IconPassthroughServlet.CONTENT_SECURITY_POLICY_INERT);
        Mockito.verify(mockResponse, Mockito.never()).setStatus(Mockito.anyInt());
    }

    @Test
    void testDoGet_redirectIsFollowed() throws IOException {
        stubOutputStream();
        doGet(baseUrl + "/redirect-to-icon");

        assertThat(responseBody.toByteArray())
                .isEqualTo(SVG_BYTES);
        Mockito.verify(mockResponse, Mockito.never()).setStatus(Mockito.anyInt());
    }

    @Test
    void testDoGet_nonImageIsRefused() throws IOException {
        doGet(baseUrl + "/page.html");

        assertRefused();
    }

    @Test
    void testDoGet_redirectToMetadataAddressIsRefused() throws IOException {
        doGet(baseUrl + "/redirect-to-metadata");

        assertRefused();
    }

    @Test
    void testDoGet_redirectLoopIsRefused() throws IOException {
        doGet(baseUrl + "/redirect-loop");

        assertRefused();
    }

    @Test
    void testDoGet_fileUrlIsRefused(@TempDir final Path tempDir) throws IOException {
        final Path file = tempDir.resolve("secret.svg");
        Files.write(file, SVG_BYTES);

        doGet(file.toUri().toString());

        assertRefused();
    }

    @Test
    void testDoGet_unknownId() throws IOException {
        Mockito.when(mockRequest.getQueryString())
                .thenReturn(UUID.randomUUID().toString());

        new IconPassthroughServlet().doGet(mockRequest, mockResponse);

        Mockito.verify(mockResponse).setStatus(HttpServletResponse.SC_BAD_REQUEST);
        Mockito.verify(mockResponse, Mockito.never()).getOutputStream();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "file:///etc/passwd",
            "jar:file:///tmp/x.jar!/icon.svg",
            "ftp://example.com/icon.svg",
            "http://169.254.169.254/latest/meta-data/",
            "http://0.0.0.0/icon.svg",
            "/relative/icon.svg"})
    void testCheckUrl_refused(final String url) {
        assertThatThrownBy(() -> IconPassthroughServlet.checkUrl(url))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://127.0.0.1/icon.svg",
            "HTTPS://127.0.0.1/icon.svg"})
    void testCheckUrl_allowed(final String url) throws Exception {
        assertThat(IconPassthroughServlet.checkUrl(url).toString())
                .isEqualTo(url);
    }

    @ParameterizedTest
    @ValueSource(strings = {"image/svg+xml", "image/png", " IMAGE/PNG ", "image/svg+xml; charset=utf-8"})
    void testIsImageContentType_true(final String contentType) {
        assertThat(IconPassthroughServlet.isImageContentType(contentType))
                .isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "text/html", "application/xml", "text/plain", "application/octet-stream"})
    void testIsImageContentType_false(final String contentType) {
        assertThat(IconPassthroughServlet.isImageContentType(contentType))
                .isFalse();
    }

    @Test
    void testIsImageContentType_null() {
        assertThat(IconPassthroughServlet.isImageContentType(null))
                .isFalse();
    }

    private void doGet(final String iconUrl) throws IOException {
        final String id = UUID.randomUUID().toString();
        IconPassthroughServlet.addIdToUrl(id, iconUrl);
        Mockito.when(mockRequest.getQueryString())
                .thenReturn(id);

        new IconPassthroughServlet().doGet(mockRequest, mockResponse);
    }

    private void assertRefused() throws IOException {
        Mockito.verify(mockResponse).setStatus(HttpServletResponse.SC_BAD_GATEWAY);
        Mockito.verify(mockResponse, Mockito.never()).getOutputStream();
        Mockito.verify(mockResponse, Mockito.never()).setHeader(Mockito.eq("Content-Type"), Mockito.anyString());
    }

    private void stubOutputStream() throws IOException {
        Mockito.when(mockResponse.getOutputStream())
                .thenReturn(new ServletOutputStream() {
                    @Override
                    public boolean isReady() {
                        return true;
                    }

                    @Override
                    public void setWriteListener(final WriteListener writeListener) {
                    }

                    @Override
                    public void write(final int b) {
                        responseBody.write(b);
                    }
                });
    }
}
