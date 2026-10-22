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

package stroom.mcp.impl;

import stroom.ai.impl.AiConfig;
import stroom.ai.impl.AiSampleUploadsConfig;
import stroom.ai.impl.McpEndpointConfig;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.mcp.impl.tools.FeedUploads;
import stroom.mcp.impl.tools.SampleUploads;
import stroom.mcp.impl.tools.SampleUploadsDb;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.util.json.JsonUtil;
import stroom.util.shared.UserRef;

import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;

/// `POST /mcp/upload` (the Stroom MCP server's `utils/uploads.py` `handle_upload`, and its tests in
/// `tests/test_uploads.py`), served by Jetty as Stroom serves it, the user signed in already, the waiting files
/// held in the database (MySQL, the test database).
class TestMcpUploadServlet {

    private static final UserRef ALICE = UserRef.builder().uuid("alice").subjectId("alice").build();
    private static final UserRef BOB = UserRef.builder().uuid("bob").subjectId("bob").build();

    private final SecurityContext securityContext = Mockito.mock(SecurityContext.class);
    private final AiConfig aiConfig = Mockito.mock(AiConfig.class);
    private final FeedUploads feedUploads = Mockito.mock(FeedUploads.class);
    private SampleUploadsDb db;
    private SampleUploads sampleUploads;
    private final List<String> sent = new ArrayList<>();
    private final HttpClient client = HttpClient.newHttpClient();
    private UserRef currentUser = ALICE;
    private Server jetty;
    private String base;

    @BeforeEach
    void setUp() throws Exception {
        db = SampleUploadsDb.emptied();
        sampleUploads = db.uploads(() -> aiConfig, "node1");
        Mockito.when(aiConfig.getMcpEndpoint()).thenReturn(new McpEndpointConfig());
        Mockito.when(aiConfig.getSampleUploads()).thenReturn(new AiSampleUploadsConfig(1, null, null));
        Mockito.when(securityContext.hasAppPermission(AppPermission.USE_MCP_TOOLS)).thenReturn(true);
        Mockito.when(securityContext.getUserRef()).thenAnswer(inv -> currentUser);
        Mockito.when(feedUploads.send(anyString(), anyString(), anyMap(), anyString(), any(InputStream.class),
                anyLong())).thenAnswer(inv -> {
                    final byte[] data = inv.<InputStream>getArgument(4).readAllBytes();
                    // As FeedUploads takes it.
                    if (data.length > inv.<Long>getArgument(5)) {
                        throw new FeedUploads.TooLarge(inv.getArgument(5));
                    }
                    if (data.length == 0) {
                        throw new FeedUploads.NoData();
                    }
                    sent.add(inv.getArgument(0) + "|" + inv.getArgument(1) + "|" + inv.getArgument(2) + "|"
                             + inv.getArgument(3) + "|" + new String(data, StandardCharsets.UTF_8));
                    return new FeedUploads.Sent("receipt-1", 77L, data.length);
                });
        final McpUploadServlet servlet = new McpUploadServlet(() -> sampleUploads, () -> feedUploads,
                securityContext, () -> aiConfig);

        jetty = new Server();
        final ServerConnector connector = new ServerConnector(jetty);
        connector.setHost("127.0.0.1");
        connector.setPort(0);
        jetty.addConnector(connector);
        final ServletContextHandler context = new ServletContextHandler();
        servlet.getPathSpecs().forEach(path -> context.addServlet(new ServletHolder(servlet), path));
        jetty.setHandler(context);
        jetty.start();
        base = "http://127.0.0.1:" + connector.getLocalPort() + SampleUploads.PATH + "/";
    }

    @AfterEach
    void tearDown() throws Exception {
        jetty.stop();
    }

    @Test
    void theWholeFileGoesToTheFeedTheCommandWasGivenForAsTheUser() throws Exception {
        final String id = waiting("sample-data/fortios/001_1.json");
        // Far past the 2,000 characters a reader gave the agent.
        final String body = "[" + ",".repeat(800_000) + "]";

        final HttpResponse<String> response = post(id + "?file=sample-data%2Ffortios%2F001_1.json", body);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json(response)).isEqualTo(Map.of("feed", "FORTIOS-EVENTS-V1.0", "file",
                "sample-data/fortios/001_1.json", "receipt_id", "receipt-1", "stream_id", 77, "bytes",
                body.length()));
        assertThat(sent).containsExactly("FORTIOS-EVENTS-V1.0|Raw Events|{Type=Raw Events, MyHost=ws01}|"
                                         + "sample-data/fortios/001_1.json|" + body);
    }

    @Test
    void aPostReachingAnotherNodeThanTheOneThatGaveTheCommandsIsTaken() throws Exception {
        // The commands were given on node2 (its chat or MCP session); the load balancer sent the post to node1, which
        // serves this servlet. Both read the waiting files from the database.
        final SampleUploads givingNode = db.uploads(() -> aiConfig, "node2");
        final String id = givingNode.issue("alice", "FORTIOS-EVENTS-V1.0", "f1", "Raw Events",
                Map.of("MyHost", "ws01"), "fortios", List.of("001_1.json")).id();

        final HttpResponse<String> response = post(id + "?file=001_1.json", "[]");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(sent).containsExactly("FORTIOS-EVENTS-V1.0|Raw Events|{Type=Raw Events, MyHost=ws01}|"
                                         + "001_1.json|[]");
        // Stored once, for either node.
        assertThat(post(id + "?file=001_1.json", "[]").statusCode()).isEqualTo(403);
        assertThat(givingNode.claim(id, "alice", "001_1.json")).isEmpty();
    }

    @Test
    void aFileBeingSentIsNotTakenByASecondPost() throws Exception {
        // As if another node were storing it now: its claim isn't stale yet.
        final String id = waiting("001_1.json");
        final SampleUploads otherNode = db.uploads(() -> aiConfig, "node2");
        final SampleUploads.Claim claim = otherNode.claim(id, "alice", "001_1.json").orElseThrow();

        final HttpResponse<String> refused = post(id + "?file=001_1.json", "x");

        assertThat(refused.statusCode()).isEqualTo(403);
        assertThat(String.valueOf(json(refused).get("error"))).contains("or is being sent");
        assertThat(sent).isEmpty();
        // Once the other post fails and lets it go, it is taken.
        otherNode.release(claim);
        assertThat(post(id + "?file=001_1.json", "x").statusCode()).isEqualTo(200);
    }

    @Test
    void aPostNoCommandWasGivenForIsRefused() throws Exception {
        final String id = waiting("001_1.json");

        final HttpResponse<String> unknown = post("not-a-code12?file=001_1.json", "x");
        assertThat(unknown.statusCode()).isEqualTo(403);
        assertThat(String.valueOf(json(unknown).get("error"))).contains("not copied exactly");
        assertThat(post(id + "?file=other.json", "x").statusCode()).isEqualTo(403);
        currentUser = BOB;
        // Another user's: the commands are for the user they were given to.
        assertThat(post(id + "?file=001_1.json", "x").statusCode()).isEqualTo(403);
        currentUser = ALICE;
        assertThat(post(id + "?file=001_1.json", "x").statusCode()).isEqualTo(200);
        // Each file once.
        assertThat(post(id + "?file=001_1.json", "x").statusCode()).isEqualTo(403);
        assertThat(sent).hasSize(1);
    }

    @Test
    void tooLargeOrEmptyIsRefusedAndCanBeSentAgain() throws Exception {
        final String id = waiting("001_1.json");

        final HttpResponse<String> large = post(id + "?file=001_1.json", "x".repeat(1024 * 1024 + 1));
        assertThat(large.statusCode()).isEqualTo(413);
        assertThat(String.valueOf(json(large).get("error"))).startsWith("Larger than 1 MB");
        final HttpResponse<String> empty = post(id + "?file=001_1.json", "");
        assertThat(empty.statusCode()).isEqualTo(400);
        assertThat(String.valueOf(json(empty).get("error"))).contains("--data-binary @<file>");
        assertThat(sent).isEmpty();
        assertThat(post(id + "?file=001_1.json", "[]").statusCode()).isEqualTo(200);
    }

    @Test
    void whatStroomRefusesIsSaidAndTheFileCanBeSentAgain() throws Exception {
        final String id = waiting("001_1.json");
        Mockito.when(feedUploads.send(anyString(), anyString(), anyMap(), anyString(), any(InputStream.class),
                anyLong())).thenThrow(new ToolRefusal("Stroom refused data for feed FORTIOS-EVENTS-V1.0: no"));

        final HttpResponse<String> refused = post(id + "?file=001_1.json", "x");

        assertThat(refused.statusCode()).isEqualTo(502);
        assertThat(json(refused).get("error")).isEqualTo("Stroom refused data for feed FORTIOS-EVENTS-V1.0: no");
        assertThat(sampleUploads.claim(id, "alice", "001_1.json")).isPresent();
    }

    @Test
    void aBodySentAsAFormArrivesWhole() throws Exception {
        // curl --data-binary sends application/x-www-form-urlencoded unless told otherwise: reading the file's name
        // as a request parameter had the container parse the body as a form, and refuse one past its size limit.
        Mockito.when(aiConfig.getSampleUploads()).thenReturn(new AiSampleUploadsConfig(10, null, null));
        final String id = waiting("a&b=c.json");
        final String body = "k=v&x=%41&".repeat(300_000);

        final HttpResponse<String> response = client.send(HttpRequest.newBuilder(URI.create(base + id
                                                                                            + "?file=a%26b%3Dc.json"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());

        assertThat(body.length()).isGreaterThanOrEqualTo(3_000_000);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(sent).singleElement().asString().endsWith("|a&b=c.json|" + body);
    }

    @Test
    void anyFailureLetsTheFileBeSentAgain() throws Exception {
        final String id = waiting("001_1.json");
        Mockito.when(feedUploads.send(anyString(), anyString(), anyMap(), anyString(), any(InputStream.class),
                anyLong())).thenThrow(new IllegalStateException("disk full"));

        final HttpResponse<String> failed = post(id + "?file=001_1.json", "x");

        assertThat(failed.statusCode()).isEqualTo(500);
        assertThat(String.valueOf(json(failed).get("error"))).contains("disk full");
        assertThat(sampleUploads.claim(id, "alice", "001_1.json")).isPresent();
    }

    @Test
    void whatTheClientNamedIsLoggedWithoutControlCharacters() {
        // A file name with a line break would otherwise forge a line in the log.
        assertThat(McpUploadServlet.forLog("a.json\n2026-10-23 INFO forged\r\u001b[31m"))
                .isEqualTo("a.json?2026-10-23 INFO forged??[31m");
        assertThat(McpUploadServlet.forLog("x".repeat(400))).hasSize(McpUploadServlet.MAX_LOGGED_CHARS + 1);
    }

    @Test
    void aUserWithoutThePermissionIsRefused() throws Exception {
        final String id = waiting("001_1.json");
        Mockito.when(securityContext.hasAppPermission(AppPermission.USE_MCP_TOOLS)).thenReturn(false);

        final HttpResponse<String> refused = post(id + "?file=001_1.json", "x");

        assertThat(refused.statusCode()).isEqualTo(403);
        assertThat(String.valueOf(json(refused).get("error"))).contains("Use MCP Tools");
        assertThat(sent).isEmpty();
    }

    @Test
    void whenTheEndpointIsTurnedOffThereIsNoUpload() throws Exception {
        final String id = waiting("001_1.json");
        Mockito.when(aiConfig.getMcpEndpoint()).thenReturn(new McpEndpointConfig(false, null, null, null, null,
                null, null));

        assertThat(post(id + "?file=001_1.json", "x").statusCode()).isEqualTo(404);
        assertThat(sent).isEmpty();
    }

    @Test
    void onlyAPostIsTaken() throws Exception {
        final String id = waiting("001_1.json");

        final HttpResponse<String> got = client.send(HttpRequest.newBuilder(URI.create(base + id + "?file=001_1.json"))
                .GET().build(), HttpResponse.BodyHandlers.ofString());

        assertThat(got.statusCode()).isEqualTo(405);
    }

    @Test
    void theFilesArePostedBelowTheMcpEndpoint() {
        assertThat(new McpUploadServlet(() -> sampleUploads, () -> feedUploads, securityContext, () -> aiConfig)
                .getPathSpecs()).containsExactly("/mcp/upload/*");
    }

    private String waiting(final String file) {
        return sampleUploads.issue("alice", "FORTIOS-EVENTS-V1.0", "f1", "Raw Events", Map.of("MyHost", "ws01"),
                "fortios", List.of(file)).id();
    }

    private HttpResponse<String> post(final String path, final String body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(base + path))
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> json(final HttpResponse<String> response) {
        return JsonUtil.readValue(response.body(), Map.class);
    }
}
