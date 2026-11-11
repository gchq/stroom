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

import stroom.ai.impl.tools.BuiltInTools;
import stroom.ai.impl.tools.StroomTool;
import stroom.ai.shared.AiToolAccess;
import stroom.docref.DocRef;
import stroom.feed.api.FeedStore;
import stroom.security.api.SecurityContext;
import stroom.security.impl.apikey.ApiKeyService;
import stroom.security.shared.AppPermission;
import stroom.security.shared.CreateHashedApiKeyRequest;
import stroom.security.shared.HashAlgorithm;
import stroom.test.AbstractAiIntegrationTest;
import stroom.util.shared.UserRef;

import com.google.inject.Injector;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import jakarta.inject.Inject;
import jakarta.servlet.Filter;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/// Stroom's MCP endpoint's REAL tools ([McpTools]) as MCP clients of users with different tool policies (made with
/// the real [stroom.ai.impl.AiToolPolicyService], kept in the database) and permissions see them: what each is
/// offered, a call that reads, and a call that writes refused without forms, declined, and agreed to. Then the
/// endpoint itself, `/mcp`, through Stroom's real security filter and servlet: no credentials, an API key of a
/// user without Use MCP Tools, and an API key of a user with it.
class TestMcpToolsWithRealPolicies extends AbstractAiIntegrationTest {

    private static final String FEED = "ACME-POLICY";
    private static final String BUILD = "acme-policy";
    private static final String SAMPLE = "time,user\n2026-09-28T10:00:00,alice\n";

    @Inject
    private McpTools mcpTools;
    @Inject
    private BuiltInTools builtInTools;
    @Inject
    private SecurityContext securityContext;
    @Inject
    private FeedStore feedStore;
    @Inject
    private Injector injector;

    private UserRef reader;
    private UserRef writer;
    private UserRef nobody;

    @BeforeEach
    void setUp() {
        reader = user("reader", Set.of(AppPermission.USE_MCP_TOOLS, AppPermission.VIEW_DATA_PERMISSION));
        writer = user("writer", Set.of(AppPermission.USE_MCP_TOOLS, AppPermission.ADMINISTRATOR));
        nobody = user("nobody", Set.of(AppPermission.USE_MCP_TOOLS));
        toolPolicy("Read only", AiToolAccess.READ_ONLY, reader);
        toolPolicy("Read and write", AiToolAccess.READ_WRITE, writer);
    }

    @Test
    void eachUserIsOfferedTheToolsTheirPoliciesGrant() {
        final List<String> readerTools = client(reader, true).toolNames();
        final List<String> writerTools = client(writer, true).toolNames();

        final List<StroomTool> offered = builtInTools.tools().stream().filter(tool -> !tool.isChatOnly()).toList();
        // Read-only: the tools that only read, and those with some calls that only read (e.g. a dry run).
        assertThat(readerTools).containsExactlyInAnyOrderElementsOf(offered.stream()
                .filter(tool -> tool.isReadOnly() || tool.hasReadOnlyCalls())
                .map(tool -> tool.definition().name())
                .toList());
        assertThat(readerTools)
                .contains("stream_meta", "folder_contents", "read_document", "build_data_splitter")
                .doesNotContain("create_feed", "start_onboarding", "promote_build");
        // Read and write: every tool but the chat's own.
        assertThat(writerTools).containsExactlyInAnyOrderElementsOf(offered.stream()
                .map(tool -> tool.definition().name())
                .toList());
        // No policy, no tools.
        assertThat(client(nobody, true).toolNames()).isEmpty();
    }

    @Test
    void aReadOnlyPolicyAllowsCallsThatReadAndRefusesThoseThatWrite() {
        final McpTestClient client = client(reader, true);

        final CallToolResult read = client.call("folder_contents", Map.of("folder", "System"));
        assertThat(read.isError()).isFalse();
        // A dry run of a tool that can save: it only reads.
        final CallToolResult dryRun = client.call("build_data_splitter", Map.of("sample", SAMPLE));
        assertThat(dryRun.isError()).as(McpTestClient.text(dryRun)).isFalse();
        assertThat(McpTestClient.text(dryRun)).contains("\"converter_type\"");

        // Its call that would save is refused, without asking the user.
        final CallToolResult save = client.call("build_data_splitter", Map.of("sample", SAMPLE, "save_as", "X",
                "build", BUILD));
        assertThat(McpTestClient.text(save)).startsWith("Not run: your tool policies allow the");
        // A tool that only writes isn't there at all.
        assertThat(McpTestClient.text(client.call("create_feed", Map.of("build", BUILD, "name", FEED))))
                .startsWith("Error: there is no tool 'create_feed'");
        assertThat(client.forms()).isEmpty();
        assertThat(feeds()).isEmpty();
    }

    @Test
    void aWriteCallIsRefusedWithoutFormsDeclinedOrMadeOnceTheUserAgrees() {
        // A client that can't show forms can't change anything.
        final McpTestClient noForms = client(writer, false);
        assertThat(McpTestClient.text(noForms.call("create_feed", Map.of("build", BUILD, "name", FEED))))
                .contains("create_feed")
                .doesNotContain("\"uuid\"");
        assertThat(feeds()).isEmpty();

        // The user declines.
        final McpTestClient client = client(writer, true);
        client.answeringWith(McpTestClient::decline);
        final CallToolResult declined = client.call("create_feed", Map.of("build", BUILD, "name", FEED));
        assertThat(client.forms()).hasSize(1);
        assertThat(McpTestClient.text(declined)).doesNotContain("\"uuid\"");
        assertThat(feeds()).isEmpty();

        // The user agrees: the feed is made, in the build's folder.
        client.answeringWith(McpTestClient::agree);
        final Map<String, Object> created = client.json("create_feed", Map.of("build", BUILD, "name", FEED));
        assertThat(client.forms()).hasSizeGreaterThan(1);
        assertThat(created).containsEntry("name", FEED);
        assertThat(feeds()).extracting(DocRef::getUuid).containsExactly((String) created.get("uuid"));
    }

    @Test
    void theEndpointNeedsCredentialsAndTheUseMcpToolsPermission() throws Exception {
        final Filter securityFilter = securityFilter();
        final McpServlet servlet = injector.getInstance(McpServlet.class);

        // No credentials: 401, and nothing served.
        final HttpServletResponse anonymous = Mockito.mock(HttpServletResponse.class);
        securityFilter.doFilter(initialize(null), anonymous, (request, response) -> {
            throw new AssertionError("Served without credentials");
        });
        Mockito.verify(anonymous).setStatus(HttpServletResponse.SC_UNAUTHORIZED);

        // An API key of a user without Use MCP Tools: 403.
        final UserRef outsider = user("outsider", Set.of());
        final HttpServletResponse refused = Mockito.mock(HttpServletResponse.class);
        securityFilter.doFilter(initialize(apiKey(outsider)), refused, (request, response) ->
                servlet.service((HttpServletRequest) request, (HttpServletResponse) response));
        Mockito.verify(refused).sendError(Mockito.eq(HttpServletResponse.SC_FORBIDDEN),
                Mockito.contains("Use MCP Tools"));

        // An API key of a user with it: the session starts.
        final HttpServletResponse served = Mockito.mock(HttpServletResponse.class);
        final StringWriter body = new StringWriter();
        Mockito.when(served.getWriter()).thenReturn(new PrintWriter(body));
        securityFilter.doFilter(initialize(apiKey(writer)), served, (request, response) ->
                servlet.service((HttpServletRequest) request, (HttpServletResponse) response));
        Mockito.verify(served, Mockito.never()).sendError(Mockito.anyInt(), Mockito.anyString());
        Mockito.verify(served, Mockito.never()).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        Mockito.verify(served).setHeader(Mockito.eq("Mcp-Session-Id"), Mockito.anyString());
        assertThat(body.toString()).contains("\"serverInfo\"");
        assertThat(injector.getInstance(StroomMcpServer.class).sessionCount()).isEqualTo(1);
    }

    private McpTestClient client(final UserRef user, final boolean canShowForms) {
        return new McpTestClient(mcpTools, securityContext, user, canShowForms);
    }

    private List<DocRef> feeds() {
        return securityContext.asProcessingUserResult(() -> feedStore.list().stream()
                .filter(feed -> FEED.equals(feed.getName()))
                .toList());
    }

    private String apiKey(final UserRef owner) {
        final ApiKeyService apiKeyService = injector.getInstance(ApiKeyService.class);
        return asUser(administrator(), () -> apiKeyService.create(new CreateHashedApiKeyRequest(owner, null,
                "MCP test " + owner.getSubjectId(), null, true, HashAlgorithm.SHA3_256)).getApiKey());
    }

    // Stroom's security filter, as Jetty runs it before every servlet (SecurityModule binds it).
    private Filter securityFilter() throws ClassNotFoundException {
        return (Filter) injector.getInstance(Class.forName("stroom.security.impl.SecurityFilter"));
    }

    // An MCP client's first request, to start a session: an initialize message posted to /mcp.
    private static HttpServletRequest initialize(final String apiKey) throws Exception {
        final String message = """
                {"jsonrpc": "2.0", "id": 1, "method": "initialize", "params": {"protocolVersion": "2025-06-18",
                "capabilities": {}, "clientInfo": {"name": "test", "version": "1"}}}""";
        final HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        Mockito.when(request.getMethod()).thenReturn("POST");
        Mockito.when(request.getRequestURI()).thenReturn("/api" + McpServlet.PATH);
        Mockito.when(request.getServletPath()).thenReturn(McpServlet.PATH);
        Mockito.when(request.getHeader("Accept")).thenReturn("application/json, text/event-stream");
        Mockito.when(request.getContentType()).thenReturn("application/json");
        if (apiKey != null) {
            Mockito.when(request.getHeader("Authorization")).thenReturn("Bearer " + apiKey);
        }
        Mockito.when(request.getHeaderNames()).thenReturn(Collections.emptyEnumeration());
        Mockito.when(request.getReader()).thenReturn(new BufferedReader(new StringReader(message)));
        Mockito.when(request.getInputStream()).thenReturn(new BodyStream(message));
        return request;
    }


    // --------------------------------------------------------------------------------


    // A request's body, as the servlet container gives it.
    private static final class BodyStream extends ServletInputStream {

        private final ByteArrayInputStream body;

        private BodyStream(final String body) {
            this.body = new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public boolean isFinished() {
            return body.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(final ReadListener readListener) {
            throw new UnsupportedOperationException("Not read asynchronously");
        }

        @Override
        public int read() {
            return body.read();
        }

        @Override
        public int read(final byte[] bytes, final int offset, final int length) {
            return body.read(bytes, offset, length);
        }
    }
}
