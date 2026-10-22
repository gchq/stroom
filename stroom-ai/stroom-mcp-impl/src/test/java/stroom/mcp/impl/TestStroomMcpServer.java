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

import stroom.ai.impl.AiAuditLog;
import stroom.ai.impl.AiChatRunManager;
import stroom.ai.impl.AiChatRunManager.RunSlot;
import stroom.ai.impl.AiConfig;
import stroom.ai.impl.AiToolPolicyService;
import stroom.ai.impl.AiWorkspaceConfig;
import stroom.ai.impl.McpEndpointConfig;
import stroom.ai.impl.ToolGrants;
import stroom.ai.impl.tools.BuiltInTools;
import stroom.ai.impl.tools.StroomTool;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolCallGuard;
import stroom.ai.impl.tools.ToolDefinition;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.tools.ToolSession;
import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.ai.impl.workspace.ToolDeadline;
import stroom.ai.shared.AiToolAccess;
import stroom.ai.shared.AiToolCategory;
import stroom.config.common.UriFactory;
import stroom.security.api.SecurityContext;
import stroom.security.api.UserIdentity;
import stroom.security.shared.AppPermission;
import stroom.task.api.ExecutorProvider;
import stroom.task.shared.ThreadPool;
import stroom.util.json.JsonUtil;
import stroom.util.shared.UserRef;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ClientCapabilities;
import io.modelcontextprotocol.spec.McpSchema.ElicitFormRequest;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult.Action;
import io.modelcontextprotocol.spec.McpSchema.InitializeResult;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceRequest;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceResult;
import io.modelcontextprotocol.spec.McpSchema.Resource;
import io.modelcontextprotocol.spec.McpSchema.ResourceTemplate;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.TextResourceContents;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Stroom's MCP server, through its real transport, with an MCP SDK client, and tools of its own.
class TestStroomMcpServer {

    private static final UserRef ALICE = UserRef.builder().uuid("alice").subjectId("alice").build();
    private static final UserRef BOB = UserRef.builder().uuid("bob").subjectId("bob").build();

    private final SecurityContext securityContext = Mockito.mock(SecurityContext.class);
    private final AiToolPolicyService policies = Mockito.mock(AiToolPolicyService.class);
    private final BuiltInTools builtInTools = Mockito.mock(BuiltInTools.class);
    private final McpWorkspaces workspaces = Mockito.mock(McpWorkspaces.class);
    private final ChatWorkspace chatWorkspace = Mockito.mock(ChatWorkspace.class);
    private final AiChatRunManager runManager = Mockito.mock(AiChatRunManager.class);
    private final AiAuditLog auditLog = Mockito.mock(AiAuditLog.class);
    private final UriFactory uriFactory = Mockito.mock(UriFactory.class);
    private final List<String> ran = Collections.synchronizedList(new ArrayList<>());
    private final List<ToolCallContext> contexts = Collections.synchronizedList(new ArrayList<>());
    // What a tool does instead of saying it ran, by its name.
    private final Map<String, Function<ToolCallContext, ToolResult>> behaviours = new ConcurrentHashMap<>();
    // The notifications to send, when they are held rather than sent at once.
    private final List<Runnable> heldNotifications = Collections.synchronizedList(new ArrayList<>());
    private final AtomicBoolean holdNotifications = new AtomicBoolean();
    private final HttpClient http = HttpClient.newHttpClient();

    private UserRef currentUser = ALICE;
    private ToolGrants grants = new ToolGrants(Map.of(
            AiToolCategory.CORE, AiToolAccess.READ_WRITE,
            AiToolCategory.SEARCH, AiToolAccess.READ_ONLY));
    private AiConfig aiConfig = new AiConfig();
    private Server jetty;
    private StroomMcpServer server;

    @BeforeEach
    void setUp() throws Exception {
        final UserIdentity identity = Mockito.mock(UserIdentity.class);
        Mockito.when(securityContext.getUserIdentity()).thenReturn(identity);
        Mockito.when(securityContext.getUserRef()).thenAnswer(invocation -> currentUser);
        Mockito.when(securityContext.hasAppPermission(AppPermission.USE_MCP_TOOLS)).thenReturn(true);
        Mockito.when(securityContext.asUserResult(Mockito.any(UserIdentity.class), Mockito.<Supplier<Object>>any()))
                .thenAnswer(invocation -> invocation.<Supplier<?>>getArgument(1).get());
        Mockito.when(policies.grantsFor(Mockito.any())).thenAnswer(invocation -> grants);
        final List<StroomTool> tools = List.of(
                tool("read_thing", AiToolCategory.CORE, true, false, false),
                tool("write_thing", AiToolCategory.CORE, false, false, false),
                confirming(tool("confirmed_write", AiToolCategory.CORE, false, false, false)),
                tool("chat_thing", AiToolCategory.CORE, true, true, false),
                tool("search_thing", AiToolCategory.SEARCH, true, false, true),
                tool("search_write", AiToolCategory.SEARCH, false, false, false),
                tool("admin_thing", AiToolCategory.ADMINISTRATION, true, false, false),
                tool("slow_thing", AiToolCategory.CORE, true, false, false),
                tool("asking_search", AiToolCategory.SEARCH, true, false, true));
        Mockito.when(builtInTools.tools()).thenReturn(tools);
        Mockito.when(builtInTools.tool(Mockito.anyString())).thenAnswer(invocation -> tools.stream()
                .filter(tool -> tool.definition().name().equals(invocation.getArgument(0)))
                .findFirst());
        Mockito.when(workspaces.chatIdFor(Mockito.any())).thenReturn(42);
        Mockito.when(workspaces.callStarted(Mockito.any())).thenReturn(true);
        Mockito.when(chatWorkspace.limits()).thenReturn(new AiWorkspaceConfig());
        Mockito.when(runManager.takeToolCallSlot(Mockito.anyString())).thenReturn(Optional.of(() -> {
        }));
        Mockito.when(uriFactory.uiUri("")).thenReturn(URI.create("https://stroom.example.com"));

        final McpForms forms = new McpForms(AiConfig::new);
        final McpTools mcpTools = new McpTools(() -> builtInTools, () -> policies,
                new McpApprovals(forms, AiConfig::new), forms, workspaces, () -> chatWorkspace,
                () -> runManager, () -> auditLog, () -> uriFactory, new ToolCallGuard());
        // Notifications are sent at once, on the thread given them, unless held.
        final Executor notifier = task -> {
            if (holdNotifications.get()) {
                heldNotifications.add(task);
            } else {
                task.run();
            }
        };
        final ExecutorProvider executorProvider = new ExecutorProvider() {
            @Override
            public Executor get() {
                return notifier;
            }

            @Override
            public Executor get(final ThreadPool threadPool) {
                return notifier;
            }
        };
        server = new StroomMcpServer(securityContext, mcpTools, new McpResources(new BundledKnowledge()),
                workspaces, () -> aiConfig, () -> null, policies, executorProvider);

        jetty = new Server();
        final ServerConnector connector = new ServerConnector(jetty);
        connector.setHost("127.0.0.1");
        connector.setPort(0);
        jetty.addConnector(connector);
        final ServletContextHandler context = new ServletContextHandler();
        // As Stroom serves it: through the servlet, which checks the session is the user's.
        context.addServlet(new ServletHolder(new McpServlet(() -> server, securityContext, AiConfig::new)),
                McpServlet.PATH);
        jetty.setHandler(context);
        jetty.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        jetty.stop();
    }

    @Test
    void theToolsListedAreThoseTheUsersPoliciesGrant() {
        try (final McpSyncClient client = client(false, null)) {
            // Core is read and write, Search read-only, Administration not granted, and the chat's own tool is
            // never offered. A client without forms is offered approve_call, as it is offered a tool that writes.
            assertThat(client.listTools().tools()).extracting(Tool::name)
                    .containsExactlyInAnyOrder("read_thing", "write_thing", "confirmed_write", "search_thing",
                            "slow_thing", "asking_search", "approve_call");
        }
        try (final McpSyncClient client = client(true, request -> new ElicitResult(Action.ACCEPT, Map.of()))) {
            assertThat(client.listTools().tools()).extracting(Tool::name)
                    .containsExactlyInAnyOrder("read_thing", "write_thing", "confirmed_write", "search_thing",
                            "slow_thing", "asking_search");
            final Tool write = client.listTools().tools().stream()
                    .filter(tool -> tool.name().equals("write_thing"))
                    .findFirst()
                    .orElseThrow();
            assertThat(write.annotations().readOnlyHint()).isFalse();
            assertThat(write.inputSchema()).containsEntry("type", "object");
        }
    }

    @Test
    void aCallThatReadsIsMadeAsTheUserInTheSessionsWorkspace() {
        try (final McpSyncClient client = client(false, null)) {
            final CallToolResult result = client.callTool(new CallToolRequest("read_thing", Map.of("q", "x")));

            assertThat(text(result)).isEqualTo("ran read_thing {\"q\":\"x\"}");
            assertThat(result.isError()).isFalse();
            assertThat(contexts).singleElement().satisfies(context -> {
                assertThat(context.session().kind()).isEqualTo(ToolSession.Kind.MCP);
                assertThat(context.session().chatId()).isEmpty();
                assertThat(context.workspace()).isPresent();
                assertThat(context.links().stream(12L)).contains("https://stroom.example.com/?action=open-stream");
            });
            Mockito.verify(auditLog).toolCall(Mockito.any(), Mockito.eq("read_thing"), Mockito.eq("{\"q\":\"x\"}"),
                    Mockito.eq("Run"), Mockito.eq(true), Mockito.eq(false));
        }
    }

    @Test
    void aCallThatChangesSomethingWaitsForTheUsersApprovalId() {
        try (final McpSyncClient client = client(false, null)) {
            final String reply = text(client.callTool(new CallToolRequest("write_thing", Map.of("q", "1"))));

            assertThat(reply).startsWith("Not run yet: this call changes something in Stroom");
            assertThat(ran).isEmpty();
            final String approvalId = reply.replaceAll(".*approval_id='([^']+)'.*", "$1");

            final CallToolResult approved = client.callTool(new CallToolRequest("approve_call",
                    Map.of("approval_id", approvalId)));
            assertThat(text(approved)).isEqualTo("ran write_thing {\"q\":\"1\"}");
            // An approval id can be used once.
            final CallToolResult again = client.callTool(new CallToolRequest("approve_call",
                    Map.of("approval_id", approvalId)));
            assertThat(again.isError()).isTrue();
            assertThat(ran).containsExactly("write_thing");
        }
    }

    @Test
    void aToolThatConfirmsEachChangeItselfIsNotApprovedAsAWholeFirst() {
        try (final McpSyncClient client = client(false, null)) {
            // It asks the user in its own form (or gives its own confirmation id), so the user is asked once.
            assertThat(text(client.callTool(new CallToolRequest("confirmed_write", Map.of()))))
                    .isEqualTo("ran confirmed_write {}");
            assertThat(ran).containsExactly("confirmed_write");
        }
    }

    @Test
    void aClientThatShowsFormsAsksTheUserThere() {
        final List<String> asked = Collections.synchronizedList(new ArrayList<>());
        try (final McpSyncClient client = client(true, request -> {
            asked.add(request.message());
            return new ElicitResult(asked.size() == 1
                    ? Action.ACCEPT
                    : Action.DECLINE, Map.of());
        })) {
            assertThat(text(client.callTool(new CallToolRequest("write_thing", Map.of()))))
                    .isEqualTo("ran write_thing {}");
            assertThat(text(client.callTool(new CallToolRequest("write_thing", Map.of()))))
                    .startsWith("Not run: the user did not allow this call");
            assertThat(asked).hasSize(2).first().asString().contains("write_thing", "changes something in Stroom");
            assertThat(ran).containsExactly("write_thing");
        }
    }

    @Test
    void callsTheUsersPoliciesDontAllowAreRefused() {
        try (final McpSyncClient client = client(false, null)) {
            // Not granted at all, or only for the chat.
            assertThat(client.callTool(new CallToolRequest("admin_thing", Map.of())).isError()).isTrue();
            assertThat(client.callTool(new CallToolRequest("chat_thing", Map.of())).isError()).isTrue();
            // Search is read-only, so its tool that writes isn't offered either.
            assertThat(text(client.callTool(new CallToolRequest("search_write", Map.of()))))
                    .startsWith("Error: there is no tool 'search_write'");
            assertThat(ran).isEmpty();
        }
    }

    @Test
    void aHeavyCallTakesARunSlot() {
        final RunSlot slot = Mockito.mock(RunSlot.class);
        Mockito.when(runManager.takeToolCallSlot("alice")).thenReturn(Optional.of(slot), Optional.empty());
        try (final McpSyncClient client = client(false, null)) {
            assertThat(text(client.callTool(new CallToolRequest("search_thing", Map.of()))))
                    .startsWith("ran search_thing");
            Mockito.verify(slot).close();
            assertThat(text(client.callTool(new CallToolRequest("search_thing", Map.of()))))
                    .startsWith("Not run: you already have as many chat runs and heavy tool calls working");
            assertThat(ran).containsExactly("search_thing");
        }
    }

    @Test
    void aSessionCanOnlyBeUsedByTheUserWhoStartedIt() {
        try (final McpSyncClient client = client(false, null)) {
            currentUser = BOB;

            // As far as Bob can tell, there is no such session.
            assertThatThrownBy(() -> client.callTool(new CallToolRequest("read_thing", Map.of())))
                    .hasMessageContaining("session with server terminated");
            assertThat(ran).isEmpty();
            // The SDK's client then starts a new session (and isn't closed until it has).
            currentUser = ALICE;
            client.listTools();
        }
    }

    @Test
    void noRequestOfAnyKindReachesAnotherUsersSession() throws Exception {
        // Seen in review: only tools/* and resources/* were checked, so another user could answer a form, listen to
        // the session's stream, or end it.
        final String sessionId = rawSession();
        currentUser = BOB;

        final HttpResponse<String> answer = rawPost(sessionId, "{\"jsonrpc\":\"2.0\",\"id\":\"" + sessionId
                                                               + "-0\",\"result\":{\"action\":\"accept\"}}");
        final HttpResponse<String> listen = http.send(HttpRequest.newBuilder(endpoint())
                .header("Accept", "text/event-stream")
                .header("Mcp-Session-Id", sessionId)
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        final HttpResponse<String> delete = http.send(HttpRequest.newBuilder(endpoint())
                .header("Mcp-Session-Id", sessionId)
                .DELETE().build(), HttpResponse.BodyHandlers.ofString());

        assertThat(answer.statusCode()).isEqualTo(404);
        assertThat(listen.statusCode()).isEqualTo(404);
        assertThat(delete.statusCode()).isEqualTo(404);
        assertThat(server.sessionCount()).isEqualTo(1);
        currentUser = ALICE;
        assertThat(server.isOpenFor(sessionId, ALICE)).isTrue();
    }

    @Test
    void anIdleSessionIsEndedAndItsWorkspaceDeleted() {
        try (final McpSyncClient client = client(false, null)) {
            assertThat(server.sessionCount()).isEqualTo(1);
            // Not yet idle for the session retention time (a day).
            server.endIdleSessions(System.currentTimeMillis() + Duration.ofHours(23).toMillis());
            assertThat(server.sessionCount()).isEqualTo(1);

            server.endIdleSessions(System.currentTimeMillis() + Duration.ofDays(2).toMillis());

            assertThat(server.sessionCount()).isZero();
            Mockito.verify(workspaces).delete(Mockito.any());
            // Seen in review: the transport kept it, pinging and notifying it for ever.
            assertThat(new TransportSessions(server.transport()).ids()).isEmpty();
            assertThatThrownBy(() -> client.callTool(new CallToolRequest("read_thing", Map.of())))
                    .hasMessageContaining("session with server terminated");
            // The SDK's client then starts a new session (and isn't closed until it has).
            assertThat(text(client.callTool(new CallToolRequest("read_thing", Map.of())))).startsWith("ran");
        }
    }

    @Test
    void aSessionTheTransportDroppedIsEnded() {
        try (final McpSyncClient client = client(false, null)) {
            final TransportSessions transportSessions = new TransportSessions(server.transport());
            final String sessionId = transportSessions.ids().iterator().next();
            // As the transport does when a send to the client fails.
            transportSessions.remove(sessionId);
            // Not straight away: the transport holds a new session only once it has started.
            server.endDroppedSessions(System.currentTimeMillis());
            assertThat(server.sessionCount()).isEqualTo(1);
            assertThat(server.isOpenFor(sessionId, ALICE)).isFalse();

            server.endDroppedSessions(System.currentTimeMillis() + StroomMcpServer.DROPPED_AFTER.toMillis() + 1);

            assertThat(server.sessionCount()).isZero();
            Mockito.verify(workspaces).delete(Mockito.any());
        }
    }

    @Test
    void aUsersOldestIdleSessionIsEndedToMakeRoom() {
        aiConfig = Mockito.mock(AiConfig.class);
        Mockito.when(aiConfig.getMcpEndpoint()).thenReturn(new McpEndpointConfig(null, null, null, null, null,
                null, 2));
        final TransportSessions transportSessions = new TransportSessions(server.transport());
        try (final McpSyncClient first = client(false, null)) {
            final String firstId = transportSessions.ids().iterator().next();
            try (final McpSyncClient second = client(false, null)) {
                second.listTools();
                try (final McpSyncClient third = client(false, null)) {
                    // The first, least recently used, was ended; the others go on. (The first isn't used again
                    // here: the SDK's client starts a new session when its own is not found.)
                    assertThat(server.sessionCount()).isEqualTo(2);
                    assertThat(transportSessions.ids()).hasSize(2).doesNotContain(firstId);
                    assertThat(server.isOpenFor(firstId, ALICE)).isFalse();
                    assertThat(text(second.callTool(new CallToolRequest("read_thing", Map.of()))))
                            .startsWith("ran");
                    assertThat(text(third.callTool(new CallToolRequest("read_thing", Map.of()))))
                            .startsWith("ran");
                }
            }
        }
    }

    @Test
    void aNewSessionIsRefusedWhenEachOfTheUsersSessionsHasACallRunning() throws Exception {
        aiConfig = Mockito.mock(AiConfig.class);
        Mockito.when(aiConfig.getMcpEndpoint()).thenReturn(new McpEndpointConfig(null, null, null, null, null,
                null, 1));
        final CountDownLatch started = new CountDownLatch(1);
        final CountDownLatch finish = new CountDownLatch(1);
        behaviours.put("slow_thing", context -> {
            started.countDown();
            await(finish);
            return ToolResult.text("done");
        });
        try (final McpSyncClient busy = client(false, null)) {
            final CompletableFuture<CallToolResult> call = CompletableFuture.supplyAsync(() ->
                    busy.callTool(new CallToolRequest("slow_thing", Map.of())));
            assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> client(false, null)).satisfies(e ->
                    assertThat(messages(e)).contains("each with a call running"));

            finish.countDown();
            assertThat(text(call.get(10, TimeUnit.SECONDS))).isEqualTo("done");
        }
    }

    @Test
    void aCallTheClientCancelsIsStopped() throws Exception {
        final CountDownLatch started = new CountDownLatch(1);
        final CompletableFuture<Boolean> interrupted = new CompletableFuture<>();
        behaviours.put("slow_thing", context -> {
            started.countDown();
            try {
                Thread.sleep(10_000);
                interrupted.complete(false);
            } catch (final InterruptedException e) {
                interrupted.complete(true);
            }
            return ToolResult.text("stopped");
        });
        final String sessionId = rawSession();
        final CompletableFuture<HttpResponse<String>> call = http.sendAsync(rawRequest(sessionId,
                "{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/call\",\"params\":{\"name\":\"slow_thing\","
                + "\"arguments\":{}}}"), HttpResponse.BodyHandlers.ofString());
        assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();

        final HttpResponse<String> cancel = rawPost(sessionId, "{\"jsonrpc\":\"2.0\",\"method\":"
                                                               + "\"notifications/cancelled\",\"params\":"
                                                               + "{\"requestId\":7,\"reason\":\"user\"}}");

        assertThat(cancel.statusCode()).isEqualTo(202);
        assertThat(interrupted.get(5, TimeUnit.SECONDS)).isTrue();
        assertThat(call.get(10, TimeUnit.SECONDS).statusCode()).isEqualTo(200);
    }

    @Test
    void aCallIsStoppedWhenItsSessionEnds() throws Exception {
        final CountDownLatch started = new CountDownLatch(1);
        final CompletableFuture<Boolean> interrupted = new CompletableFuture<>();
        behaviours.put("slow_thing", context -> {
            started.countDown();
            try {
                Thread.sleep(10_000);
                interrupted.complete(false);
            } catch (final InterruptedException e) {
                interrupted.complete(true);
            }
            return ToolResult.text("stopped");
        });
        try (final McpSyncClient client = client(false, null)) {
            CompletableFuture.runAsync(() -> client.callTool(new CallToolRequest("slow_thing", Map.of())));
            assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();

            client.closeGracefully();

            assertThat(interrupted.get(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void aCallIsWorkedOnByTheThreadServingItWithinATimeLimit() {
        // Seen in review: calls ran on Reactor's shared boundedElastic pool, with no overall time limit.
        final List<String> threads = Collections.synchronizedList(new ArrayList<>());
        final List<Optional<Duration>> limits = Collections.synchronizedList(new ArrayList<>());
        behaviours.put("slow_thing", context -> {
            threads.add(Thread.currentThread().getName());
            limits.add(ToolDeadline.remaining());
            return ToolResult.text("done");
        });
        try (final McpSyncClient client = client(false, null)) {
            assertThat(text(client.callTool(new CallToolRequest("slow_thing", Map.of())))).isEqualTo("done");
        }

        assertThat(threads).singleElement().asString().doesNotContain("boundedElastic").startsWith("qtp");
        assertThat(limits).singleElement().satisfies(limit -> assertThat(limit).hasValueSatisfying(left ->
                assertThat(left).isPositive().isLessThanOrEqualTo(new AiWorkspaceConfig().getQueryTimeLimit()
                        .getDuration())));
    }

    @Test
    void aHeavyCallGivesBackItsRunSlotWhileItAsksTheUser() {
        // Seen in review: the slot was held while the call's form waited, up to 10 minutes.
        final List<String> events = Collections.synchronizedList(new ArrayList<>());
        Mockito.when(runManager.takeToolCallSlot("alice")).thenAnswer(invocation -> {
            events.add("take");
            return Optional.of((RunSlot) () -> events.add("give back"));
        });
        behaviours.put("asking_search", context -> {
            final ElicitResult answer = context.userQuestions().ask("asking_search", "c1", "Which?",
                    McpForms.NO_FIELDS);
            return ToolResult.text("answered " + answer.action());
        });
        try (final McpSyncClient client = client(true, request -> {
            events.add("asked");
            return new ElicitResult(Action.ACCEPT, Map.of());
        })) {
            assertThat(text(client.callTool(new CallToolRequest("asking_search", Map.of()))))
                    .isEqualTo("answered ACCEPT");
        }

        assertThat(events).containsExactly("take", "give back", "asked", "take", "give back");
    }

    @Test
    void sessionsAreToldOfAPolicyChangeOffTheThreadThatSavedIt() throws InterruptedException {
        // Seen in review: every session was told on the saving thread, each waited for in turn.
        final ArgumentCaptor<Runnable> listener = ArgumentCaptor.forClass(Runnable.class);
        Mockito.verify(policies).addChangeListener(listener.capture());
        final List<List<Tool>> told = Collections.synchronizedList(new ArrayList<>());
        holdNotifications.set(true);
        try (final McpSyncClient client = McpClient.sync(transport())
                .requestTimeout(Duration.ofSeconds(10))
                .toolsChangeConsumer(told::add)
                .build()) {
            client.initialize();

            listener.getValue().run();

            // Nothing sent on the saving thread: a task per session, for the notification threads.
            assertThat(heldNotifications).hasSize(1);
            assertThat(told).isEmpty();
            waitUntil(() -> {
                List.copyOf(heldNotifications).forEach(Runnable::run);
                heldNotifications.clear();
                if (told.isEmpty()) {
                    listener.getValue().run();
                }
                return !told.isEmpty();
            });
        }
    }

    @Test
    void whenAPolicyChangesTheSessionsAreTold() throws InterruptedException {
        final ArgumentCaptor<Runnable> listener = ArgumentCaptor.forClass(Runnable.class);
        Mockito.verify(policies).addChangeListener(listener.capture());
        final List<List<Tool>> told = Collections.synchronizedList(new ArrayList<>());
        try (final McpSyncClient client = McpClient.sync(transport())
                .requestTimeout(Duration.ofSeconds(10))
                .toolsChangeConsumer(told::add)
                .build()) {
            client.initialize();
            grants = new ToolGrants(Map.of(AiToolCategory.CORE, AiToolAccess.READ_ONLY));

            // The client's stream may not be open yet when the first is sent, and a notification sent before then is
            // lost (the client lists its tools when it connects anyway), so the change is told until it is heard.
            waitUntil(() -> {
                if (told.isEmpty()) {
                    listener.getValue().run();
                }
                return !told.isEmpty();
            });
            assertThat(told.getLast()).extracting(Tool::name).containsExactly("read_thing", "slow_thing");
        }
    }

    @Test
    void theGuidesAndConventionsAreServedAsResources() {
        try (final McpSyncClient client = client(false, null)) {
            assertThat(client.getServerCapabilities().resources()).isNotNull();
            assertThat(client.listResources().resources()).extracting(Resource::uri)
                    .contains("stroom://guides", "stroom://guide/xslt", "stroom://guide/agent-instructions",
                            "stroom://conventions/ecs", "stroom://conventions/stroom-flat");
            assertThat(client.listResourceTemplates().resourceTemplates()).extracting(ResourceTemplate::uriTemplate)
                    .containsExactly("stroom://guide/{name}", "stroom://conventions/{name}");

            final TextResourceContents xslt = text(client.readResource(new ReadResourceRequest(
                    "stroom://guide/xslt")));
            assertThat(xslt.mimeType()).isEqualTo("text/markdown");
            assertThat(xslt.text()).startsWith("#");
            assertThat(text(client.readResource(new ReadResourceRequest("stroom://conventions/ecs"))).mimeType())
                    .isEqualTo("text/yaml");
            assertThat(text(client.readResource(new ReadResourceRequest("stroom://guides"))).text())
                    .startsWith("# Guides\n\n- `stroom://guide/agent-instructions`: ")
                    .contains("- `stroom://guide/xslt`: ");
        }
    }

    @Test
    void anUnknownResourceSaysWhatThereIs() {
        try (final McpSyncClient client = client(false, null)) {
            assertThatThrownBy(() -> client.readResource(new ReadResourceRequest("stroom://guide/nope")))
                    .hasMessageContaining("No guide 'nope'. Guides: agent-instructions, cef, data-splitter");
            assertThatThrownBy(() -> client.readResource(new ReadResourceRequest("stroom://conventions/nope")))
                    .hasMessageContaining("No convention 'nope'");
        }
    }

    @Test
    void theResourcesOfASessionAreOnlyForItsUser() {
        try (final McpSyncClient client = client(false, null)) {
            currentUser = BOB;

            // As far as Bob can tell, there is no such session.
            assertThatThrownBy(client::listResources).hasMessageContaining("session with server terminated");
            // The SDK's client then starts a new session, its user's own.
            currentUser = ALICE;
            assertThat(client.listResources().resources()).isNotEmpty();
        }
    }

    @Test
    void theGuideTemplateNamesEveryGuide() {
        // As the Stroom MCP server's test of its guide index: a guide added without its name in the description
        // fails here.
        final String listed = McpResources.GUIDE_DESCRIPTION.replaceFirst("^Working guides: ", "")
                .replaceFirst("\\.$", "");
        assertThat(List.of(listed.split(", "))).containsExactlyInAnyOrderElementsOf(BundledKnowledge.GUIDES);
    }

    @Test
    void theInstructionsKeepATaskListAndGoOnBetweenStages() {
        // Seen in testing: Stroom AI's agent kept no task list and stopped at the end of each stage to report.
        final String text = String.join(" ", StroomMcpServer.INSTRUCTIONS.split("\\s+"));
        assertThat(text)
                .contains("With a task-list tool (Stroom AI: task_list), write the plan's steps into it")
                .contains("A stage's end is no place to stop: don't end your turn to report it, go on to `next`");
        try (final McpSyncClient client = McpClient.sync(transport()).requestTimeout(Duration.ofSeconds(10)).build()) {
            final InitializeResult initialized = client.initialize();
            assertThat(initialized.instructions()).isEqualTo(StroomMcpServer.INSTRUCTIONS);
        }
    }

    @Test
    void theInstructionsSendTheUsersDecisionsToTheClientsQuestionTool() {
        // Asked for by the user: Stroom AI's agent has ask_user, but ended its answer with a "Questions for you"
        // list, following the server's "ask the user" wording.
        final String text = String.join(" ", StroomMcpServer.INSTRUCTIONS.split("\\s+"));
        assertThat(text)
                .contains("a tool for asking the user questions (Stroom AI: ask_user), ask them with it, all in one "
                          + "call")
                .contains("Without such a tool, ask in your answer")
                // Results that fix the options or say to recommend none, forms of the server's and pastes keep
                // their own way.
                .contains("or says to recommend none, keep to it")
                .contains("not what a form from this server will ask")
                .contains("A request to paste something (an index template, a sample) is not such a question");
    }

    @Test
    void theInstructionsNameNoToolMergedIntoAnother() {
        // find_documents and describe_document became folder_contents, find_in_content, document_info,
        // read_document and document_links; find_streams and describe_stream stream_meta; read_stream
        // fetch_events; find_events search_events.
        final String[] merged = {"find_documents", "describe_document", "find_streams", "describe_stream",
                "read_stream", "find_events"};
        assertThat(StroomMcpServer.INSTRUCTIONS).doesNotContain(merged);
        for (final String guide : BundledKnowledge.GUIDES) {
            assertThat(new BundledKnowledge().guide(guide)).hasValueSatisfying(text ->
                    assertThat(text).as(guide).doesNotContain(merged).doesNotContain("STROOM_MCP_"));
        }
    }

    private static TextResourceContents text(final ReadResourceResult result) {
        return (TextResourceContents) result.contents().getFirst();
    }

    private McpSyncClient client(final boolean forms, final Function<ElicitFormRequest, ElicitResult> answer) {
        final McpClient.SyncSpec spec = McpClient.sync(transport())
                .requestTimeout(Duration.ofSeconds(10))
                .capabilities(forms
                        ? ClientCapabilities.builder().elicitation().build()
                        : ClientCapabilities.builder().build());
        if (answer != null) {
            spec.elicitation(answer);
        }
        final McpSyncClient client = spec.build();
        client.initialize();
        return client;
    }

    // A session started over plain HTTP, as a client without the SDK would.
    private String rawSession() throws Exception {
        final HttpResponse<String> response = http.send(rawRequest(null, "{\"jsonrpc\":\"2.0\",\"id\":1,"
                                                                         + "\"method\":\"initialize\",\"params\":"
                                                                         + "{\"protocolVersion\":\"2025-06-18\","
                                                                         + "\"capabilities\":{},\"clientInfo\":"
                                                                         + "{\"name\":\"raw\",\"version\":\"1\"}}}"),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JsonUtil.readValue(response.body(), Map.class)).containsKey("result");
        final String sessionId = response.headers().firstValue("Mcp-Session-Id").orElseThrow();
        assertThat(rawPost(sessionId, "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}")
                .statusCode()).isEqualTo(202);
        return sessionId;
    }

    private HttpResponse<String> rawPost(final String sessionId, final String body) throws Exception {
        return http.send(rawRequest(sessionId, body), HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest rawRequest(final String sessionId, final String body) {
        final HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint())
                .header("Accept", "application/json, text/event-stream")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (sessionId != null) {
            builder.header("Mcp-Session-Id", sessionId);
        }
        return builder.build();
    }

    private URI endpoint() {
        final int port = ((ServerConnector) jetty.getConnectors()[0]).getLocalPort();
        return URI.create("http://127.0.0.1:" + port + McpServlet.PATH);
    }

    // The messages of an exception and its causes.
    private static String messages(final Throwable e) {
        final StringBuilder messages = new StringBuilder();
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            messages.append(cause.getMessage()).append('\n');
        }
        return messages.toString();
    }

    private static void await(final CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private HttpClientStreamableHttpTransport transport() {
        final int port = ((ServerConnector) jetty.getConnectors()[0]).getLocalPort();
        return HttpClientStreamableHttpTransport.builder("http://127.0.0.1:" + port).endpoint("/mcp").build();
    }

    private static void waitUntil(final BooleanSupplier condition) throws InterruptedException {
        final long deadline = System.currentTimeMillis() + 10_000;
        while (!condition.getAsBoolean()) {
            assertThat(System.currentTimeMillis()).as("waited too long").isLessThan(deadline);
            Thread.sleep(200);
        }
    }

    private static String text(final CallToolResult result) {
        return ((TextContent) result.content().getFirst()).text();
    }

    // The tool, as one that confirms each change it makes itself.
    private static StroomTool confirming(final StroomTool tool) {
        return new StroomTool() {
            @Override
            public ToolDefinition definition() {
                return tool.definition();
            }

            @Override
            public AiToolCategory category() {
                return tool.category();
            }

            @Override
            public boolean isReadOnly() {
                return tool.isReadOnly();
            }

            @Override
            public boolean confirmsItself() {
                return true;
            }

            @Override
            public ToolResult execute(final ToolCall call, final ToolCallContext context) {
                return tool.execute(call, context);
            }
        };
    }

    private StroomTool tool(final String name,
                            final AiToolCategory category,
                            final boolean readOnly,
                            final boolean chatOnly,
                            final boolean heavy) {
        return new StroomTool() {
            @Override
            public ToolDefinition definition() {
                return ToolDefinition.builder()
                        .name(name)
                        .description("The " + name + " tool.")
                        .parameters(ObjectSchema.builder().addStringProperty("q", "A query.").build())
                        .build();
            }

            @Override
            public AiToolCategory category() {
                return category;
            }

            @Override
            public boolean isReadOnly() {
                return readOnly;
            }

            @Override
            public boolean isChatOnly() {
                return chatOnly;
            }

            @Override
            public boolean usesRunSlot() {
                return heavy;
            }

            @Override
            public ToolResult execute(final ToolCall call, final ToolCallContext context) {
                ran.add(name);
                contexts.add(context);
                final Function<ToolCallContext, ToolResult> behaviour = behaviours.get(name);
                return behaviour != null
                        ? behaviour.apply(context)
                        : ToolResult.text("ran " + name + " " + call.arguments());
            }
        };
    }
}
