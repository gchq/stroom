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
import stroom.ai.impl.AiToolPolicyService;
import stroom.ai.impl.McpEndpointConfig;
import stroom.security.api.SecurityContext;
import stroom.security.api.UserIdentity;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.servlet.HttpServletRequestHolder;
import stroom.util.shared.BuildInfo;
import stroom.util.shared.NullSafe;
import stroom.util.shared.UserRef;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpAsyncServerExchange;
import io.modelcontextprotocol.server.McpNotificationHandler;
import io.modelcontextprotocol.server.McpRequestHandler;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.DefaultMcpStreamableServerSessionFactory;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.Implementation;
import io.modelcontextprotocol.spec.McpSchema.InitializeRequest;
import io.modelcontextprotocol.spec.McpSchema.InitializeResult;
import io.modelcontextprotocol.spec.McpSchema.ListToolsResult;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceRequest;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import io.modelcontextprotocol.spec.McpStreamableServerSession;
import io.modelcontextprotocol.spec.McpStreamableServerSession.McpStreamableServerSessionInit;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/// Stroom's own MCP server, behind the `/mcp` servlet ([McpServlet]): the MCP Streamable HTTP transport, with
/// handlers of Stroom's own, so that each session is offered only the tools its user's tool policies grant (the
/// MCP SDK's own server offers every session the same tools).
///
/// A session belongs to the user who started it, and only they may use it. Each request is made as the user
/// [stroom.security.impl.SecurityFilter] signed in, on a thread of its own: as that thread may have been made while
/// another request was served, the request it inherited (see [HttpServletRequestHolder]) is cleared first.
@Singleton
class StroomMcpServer {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StroomMcpServer.class);

    /// Where the transport keeps the user each request was made as.
    static final String USER_IDENTITY_KEY = "stroom.userIdentity";
    static final String SERVER_NAME = "Stroom";
    /// How often, at most, idle sessions are looked for.
    static final Duration EVICTION_INTERVAL = Duration.ofMinutes(1);
    /// What a client is told about Stroom's tools when it connects: how they run here, then the Stroom MCP
    /// server's instructions (sent to every client at connection, so the rules hold whichever client runs the
    /// model and whether or not it uses the prompts), as they apply to Stroom.
    static final String INSTRUCTIONS = """
            These are Stroom's own tools. Each runs as the signed-in user, with their permissions, and only those \
            their tool policies allow are listed. A call that changes something in Stroom asks the user first. A \
            large result is saved to this session's workspace (a path under /outputs/), which the workspace tools \
            (read_file, grep, jq, table and others) read; the workspace lasts as long as the session.

            """ + BundledKnowledge.serverInstructions();

    private final SecurityContext securityContext;
    private final HttpServletRequestHolder requestHolder;
    private final McpTools tools;
    private final McpResources resources;
    private final McpWorkspaces workspaces;
    private final Provider<AiConfig> aiConfigProvider;
    private final Provider<BuildInfo> buildInfoProvider;
    private final Map<String, OpenSession> sessions = new ConcurrentHashMap<>();
    private final AtomicLong lastEvictionMs = new AtomicLong();
    private final HttpServletStreamableServerTransportProvider transport;
    private final DefaultMcpStreamableServerSessionFactory sessionFactory;

    @Inject
    StroomMcpServer(final SecurityContext securityContext,
                    final HttpServletRequestHolder requestHolder,
                    final McpTools tools,
                    final McpResources resources,
                    final McpWorkspaces workspaces,
                    final Provider<AiConfig> aiConfigProvider,
                    final Provider<BuildInfo> buildInfoProvider,
                    final AiToolPolicyService policies) {
        this.securityContext = securityContext;
        this.requestHolder = requestHolder;
        this.tools = tools;
        this.resources = resources;
        this.workspaces = workspaces;
        this.aiConfigProvider = aiConfigProvider;
        this.buildInfoProvider = buildInfoProvider;
        this.transport = HttpServletStreamableServerTransportProvider.builder()
                .mcpEndpoint(McpServlet.PATH)
                .jsonMapper(McpJsonDefaults.getMapper())
                .contextExtractor(request -> McpTransportContext.create(NullSafe.getOrElse(
                        securityContext.getUserIdentity(),
                        identity -> Map.<String, Object>of(USER_IDENTITY_KEY, identity),
                        Map.of())))
                .keepAliveInterval(config().getKeepAliveInterval().getDuration())
                .build();
        this.sessionFactory = new DefaultMcpStreamableServerSessionFactory(
                config().getFormTimeout().getDuration(),
                this::initialize,
                requestHandlers(),
                notificationHandlers(),
                this::closed,
                McpJsonDefaults.getSchemaValidator());
        transport.setSessionFactory(this::startSession);
        // Open sessions are told their tools may have changed, so their clients list them again.
        policies.addChangeListener(this::toolsChanged);
    }

    /// @return The transport, which serves the servlet's requests.
    HttpServletStreamableServerTransportProvider transport() {
        return transport;
    }

    /// @return How many sessions this node holds.
    int sessionCount() {
        return sessions.size();
    }

    /// @param sessionId A session's id, as a request gave it.
    /// @return True if the session is open on this node. A request for any other session is answered 404, so the
    /// client starts a new one, as the protocol says: one that has ended, or that is on another node.
    boolean isOpen(final String sessionId) {
        return sessionId != null && sessions.containsKey(sessionId);
    }

    /// Ends the sessions that have had no requests for the session retention time, deleting their workspaces: a
    /// client that goes away without ending its session leaves it open. Done at most once a minute.
    ///
    /// @param nowMs The time now.
    void endIdleSessions(final long nowMs) {
        final long last = lastEvictionMs.get();
        if (nowMs - last < EVICTION_INTERVAL.toMillis() || !lastEvictionMs.compareAndSet(last, nowMs)) {
            return;
        }
        final long retentionMs = config().getSessionRetention().toMillis();
        if (retentionMs <= 0) {
            return;
        }
        sessions.values().stream()
                .filter(open -> open.session().lastUsedMs() < nowMs - retentionMs)
                .toList()
                .forEach(open -> {
                    LOGGER.info(() -> "Ending MCP session " + open.session().id() + ", idle since "
                                      + Instant.ofEpochMilli(open.session().lastUsedMs()));
                    // Calls closed(), which forgets it and deletes its workspace.
                    open.sdkSession().close();
                });
    }

    // A client's initialize request starts a session, on the request's thread, so the user is the one signed in.
    private McpStreamableServerSessionInit startSession(final InitializeRequest initializeRequest) {
        final UserRef owner = securityContext.getUserRef();
        if (owner == null) {
            throw McpError.builder(McpSchema.ErrorCodes.INVALID_REQUEST)
                    .message("Sign in to Stroom to use its MCP endpoint.")
                    .build();
        }
        final McpStreamableServerSessionInit init = sessionFactory.startSession(initializeRequest);
        final String id = init.session().getId();
        sessions.put(id, new OpenSession(new McpSession(id, owner, initializeRequest.clientInfo(),
                initializeRequest.capabilities()), init.session()));
        LOGGER.info(() -> "MCP session " + id + " started for " + owner.toDisplayString() + " by "
                          + NullSafe.get(initializeRequest.clientInfo(), Implementation::name));
        return init;
    }

    private Mono<InitializeResult> initialize(final InitializeRequest request) {
        return Mono.fromSupplier(() -> {
            final List<String> versions = transport.protocolVersions();
            // As the protocol says: the version the client asked for if it is one we support, else our latest.
            final String version = versions.contains(request.protocolVersion())
                    ? request.protocolVersion()
                    : versions.getLast();
            return new InitializeResult(
                    version,
                    ServerCapabilities.builder()
                            .tools(true)
                            .resources(false, false)
                            .build(),
                    new Implementation(SERVER_NAME, NullSafe.nonBlankStringElse(
                            NullSafe.get(buildInfoProvider.get(), BuildInfo::getBuildVersion), "unknown")),
                    INSTRUCTIONS,
                    null);
        });
    }

    private Map<String, McpRequestHandler<?>> requestHandlers() {
        return Map.of(
                McpSchema.METHOD_PING, (exchange, params) -> Mono.just(Map.of()),
                McpSchema.METHOD_TOOLS_LIST, (exchange, params) -> asUser(exchange, session ->
                        new ListToolsResult(tools.list(session), null, null)),
                McpSchema.METHOD_TOOLS_CALL, (exchange, params) -> asUser(exchange, session ->
                        tools.call(session, exchange, toCallToolRequest(params))),
                // The guides and convention profiles bundled for agents, the same for every user.
                McpSchema.METHOD_RESOURCES_LIST, (exchange, params) -> asUser(exchange, session ->
                        resources.list()),
                McpSchema.METHOD_RESOURCES_TEMPLATES_LIST, (exchange, params) -> asUser(exchange, session ->
                        resources.templates()),
                McpSchema.METHOD_RESOURCES_READ, (exchange, params) -> asUser(exchange, session ->
                        resources.read(McpJsonDefaults.getMapper()
                                .convertValue(params, ReadResourceRequest.class).uri())));
    }

    private static Map<String, McpNotificationHandler> notificationHandlers() {
        return Map.of(McpSchema.METHOD_NOTIFICATION_INITIALIZED, (exchange, params) -> Mono.empty());
    }

    private static CallToolRequest toCallToolRequest(final Object params) {
        return McpJsonDefaults.getMapper().convertValue(params, CallToolRequest.class);
    }

    // Does the work of a request as its user, on a thread of its own, refusing it if the session isn't theirs. The
    // thread is interrupted if the client gives up on the request, so a tool that checks stops.
    private <T> Mono<T> asUser(final McpAsyncServerExchange exchange, final SessionWork<T> work) {
        final UserIdentity identity = (UserIdentity) exchange.transportContext().get(USER_IDENTITY_KEY);
        final McpSession session = NullSafe.get(sessions.get(exchange.sessionId()), OpenSession::session);
        if (identity == null || session == null) {
            return Mono.error(McpError.builder(McpSchema.ErrorCodes.INVALID_REQUEST)
                    .message("This MCP session has ended. Start a new one.")
                    .build());
        }
        return Mono.<T>create(sink -> {
            final Thread worker = Thread.currentThread();
            sink.onCancel(worker::interrupt);
            try {
                sink.success(securityContext.asUserResult(identity, () -> {
                    // The thread may hold a request it inherited, long finished.
                    requestHolder.set(null);
                    final UserRef user = securityContext.getUserRef();
                    if (user == null || !Objects.equals(user.getUuid(), session.owner().getUuid())) {
                        throw McpError.builder(McpSchema.ErrorCodes.INVALID_REQUEST)
                                .message("This MCP session belongs to another user. Start a new one.")
                                .build();
                    }
                    session.used();
                    return work.apply(session);
                }));
            } catch (final RuntimeException e) {
                sink.error(e);
            } finally {
                // Leave the pooled thread as it was found.
                Thread.interrupted();
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<Void> closed(final String sessionId) {
        return Mono.fromRunnable(() -> {
            final OpenSession open = sessions.remove(sessionId);
            if (open != null) {
                LOGGER.info(() -> "MCP session " + sessionId + " ended");
                workspaces.delete(open.session());
            }
        });
    }

    // Tells the sessions on this node that their tools may have changed. Sessions on other nodes see the change
    // when their clients next list their tools.
    private void toolsChanged() {
        transport.notifyClients(McpSchema.METHOD_NOTIFICATION_TOOLS_LIST_CHANGED, null)
                .subscribe(ignored -> {
                }, e -> LOGGER.debug(() -> "Unable to tell MCP sessions their tools have changed", e));
    }

    private McpEndpointConfig config() {
        return NullSafe.getOrElse(aiConfigProvider.get(), AiConfig::getMcpEndpoint, new McpEndpointConfig());
    }


    // --------------------------------------------------------------------------------


    /// A session open on this node: ours, and the MCP SDK's, which ends it.
    private record OpenSession(McpSession session, McpStreamableServerSession sdkSession) {

    }

    /// The work of a request, for its session.
    @FunctionalInterface
    private interface SessionWork<T> {

        T apply(McpSession session);
    }
}
