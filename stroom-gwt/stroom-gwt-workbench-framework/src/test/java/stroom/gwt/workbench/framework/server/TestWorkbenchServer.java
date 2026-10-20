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

package stroom.gwt.workbench.framework.server;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestWorkbenchServer {

    @TempDir
    Path tempDir;

    private WorkbenchServer server;
    private HttpClient httpClient;
    private String baseUrl;

    @BeforeEach
    void setUp() throws IOException {
        Files.writeString(tempDir.resolve("index.html"), "<html>index</html>");
        Files.createDirectories(tempDir.resolve("workbench"));
        Files.writeString(tempDir.resolve("workbench/workbench.nocache.js"), "// js");

        server = new WorkbenchServer(0, List.of(tempDir));
        server.start();
        baseUrl = "http://localhost:" + server.getPort();
        httpClient = HttpClient.newHttpClient();
    }

    @AfterEach
    void tearDown() {
        server.stop();
        httpClient.close();
    }

    @Test
    void testServesIndexForRoot() throws Exception {
        final HttpResponse<String> response = get("/?path=/story/widgets-buttons-button--default");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("<html>index</html>");
        assertThat(response.headers().firstValue("Content-Type")).contains("text/html; charset=utf-8");
        assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
    }

    @Test
    void testServesFile() throws Exception {
        final HttpResponse<String> response = get("/workbench/workbench.nocache.js");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("// js");
        assertThat(response.headers().firstValue("Content-Type"))
                .contains("text/javascript; charset=utf-8");
    }

    @Test
    void testHead() throws Exception {
        final HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/index.html"))
                        .method("HEAD", HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEmpty();
    }

    @Test
    void testNotFound() throws Exception {
        assertThat(get("/missing.js").statusCode()).isEqualTo(404);
        assertThat(get("/workbench/").statusCode()).isEqualTo(404);
    }

    @Test
    void testMethodNotAllowed() throws Exception {
        final HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/index.html"))
                        .POST(HttpRequest.BodyPublishers.ofString("x"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(405);
    }

    @Test
    void testMainRequiresArgs() {
        assertThatThrownBy(() -> WorkbenchServer.main(new String[]{"6008"}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testGetPort() {
        assertThat(server.getPort()).isPositive();
        assertThat(new WorkbenchServer(1234, List.of(tempDir)).getPort()).isEqualTo(1234);
    }

    @Test
    void testAllowsLoopbackHosts() throws Exception {
        final int port = server.getPort();
        for (final String host : List.of("localhost", "127.0.0.1", "[::1]", "LOCALHOST")) {
            assertThat(RawHttp.send(port, "GET", "/index.html", "Host: " + host + ":" + port))
                    .as(host)
                    .isEqualTo(200);
        }
    }

    @Test
    void testRejectsOtherHosts() throws Exception {
        // E.g. DNS rebinding, where another site's name points at the loopback address
        final int port = server.getPort();
        assertThat(RawHttp.send(port, "GET", "/index.html", "Host: evil.example.com:" + port)).isEqualTo(403);
        assertThat(RawHttp.send(port, "GET", "/index.html", "Host: evil.example.com")).isEqualTo(403);
        assertThat(RawHttp.send(port, "GET", "/index.html", "Host: localhost:1")).isEqualTo(403);
        assertThat(RawHttp.send(port, "GET", "/index.html", "Host: localhost")).isEqualTo(403);
        assertThat(RawHttp.send(port, "GET", "/index.html")).isEqualTo(403);
    }

    @Test
    void testUnreadableFileGives500() throws Exception {
        final Path file = tempDir.resolve("unreadable.js");
        Files.writeString(file, "// js");
        try {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("-w-------"));
        } catch (final UnsupportedOperationException e) {
            Assumptions.abort("No POSIX file permissions");
        }
        Assumptions.assumeFalse(Files.isReadable(file), "Running as a user that can read anything");

        // The error is sent rather than the connection being dropped
        assertThat(get("/unreadable.js").statusCode()).isEqualTo(500);
    }

    @Test
    void testSlowRequestDoesNotBlockOthers() throws Exception {
        try (final Socket slowClient = new Socket("localhost", server.getPort())) {
            // Start a request but never finish it
            slowClient.getOutputStream().write("GET /index.html HTTP/1.1\r\nHost: localhost"
                    .getBytes(StandardCharsets.US_ASCII));
            slowClient.getOutputStream().flush();

            final HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(URI.create(baseUrl + "/index.html"))
                            .timeout(Duration.ofSeconds(10))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
        }
    }

    @Test
    void testCreate() {
        final WorkbenchServer created = WorkbenchServer.create(new String[]{
                "0",
                "--source-root=" + tempDir,
                "--stories-root=" + tempDir,
                "--stories-package=app",
                "--all-stories=" + tempDir.resolve("AllStories.java"),
                tempDir.toString()});
        assertThat(created.getPort()).isZero();
    }

    @Test
    void testCreateWithSomeStoriesOptions() {
        assertThatThrownBy(() -> WorkbenchServer.create(new String[]{
                "0", "--source-root=" + tempDir, "--stories-root=" + tempDir, tempDir.toString()}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("--stories-package");
    }

    @Test
    void testCreateWithInvalidOptions() {
        assertThatThrownBy(() -> WorkbenchServer.create(new String[]{"0", "--unknown=x", tempDir.toString()}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown option --unknown=x");
        assertThatThrownBy(() -> WorkbenchServer.create(new String[]{"0", "--source-root", tempDir.toString()}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("--source-root needs a value");
    }

    private HttpResponse<String> get(final String path) throws Exception {
        return httpClient.send(
                HttpRequest.newBuilder(URI.create(baseUrl + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
