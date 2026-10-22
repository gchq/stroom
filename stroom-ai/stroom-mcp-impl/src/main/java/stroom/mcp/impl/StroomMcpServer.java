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
import stroom.mcp.impl.RunningCalls.RunningCall;
import stroom.security.api.SecurityContext;
import stroom.security.api.UserIdentity;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.ThreadPoolImpl;
import stroom.task.shared.ThreadPool;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.BuildInfo;
import stroom.util.shared.NullSafe;
import stroom.util.shared.UserRef;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.schema.JsonSchemaValidator;
import io.modelcontextprotocol.server.McpAsyncServerExchange;
import io.modelcontextprotocol.server.McpNotificationHandler;
import io.modelcontextprotocol.server.McpRequestHandler;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.ClientCapabilities;
import io.modelcontextprotocol.spec.McpSchema.Implementation;
import io.modelcontextprotocol.spec.McpSchema.InitializeRequest;
import io.modelcontextprotocol.spec.McpSchema.InitializeResult;
import io.modelcontextprotocol.spec.McpSchema.JSONRPCRequest;
import io.modelcontextprotocol.spec.McpSchema.ListToolsResult;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceRequest;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import io.modelcontextprotocol.spec.McpStreamableServerSession;
import io.modelcontextprotocol.spec.McpStreamableServerSession.McpStreamableServerSessionInit;
import io.modelcontextprotocol.spec.McpStreamableServerTransport;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/// Stroom's own MCP server, behind the `/mcp` servlet ([McpServlet]): the MCP Streamable HTTP transport, with
/// handlers of Stroom's own, so that each session is offered only the tools its user's tool policies grant (the
/// MCP SDK's own server offers every session the same tools).
///
/// A session belongs to the user who started it, and only they may use it. Each request is worked on, as the user
/// [stroom.security.impl.SecurityFilter] signed in, by the thread serving it (the transport holds that thread
/// until the answer is sent anyway). A request is cancelled (its thread interrupted, see [RunningCalls]) when the
/// client says so (`notifications/cancelled`), when its HTTP request ends, or when its session ends.
///
/// The transport keeps sessions of its own, which this keeps in step with its own (see [TransportSessions]): a
/// session this ends is taken out of the transport too, and one the transport drops (when a send to its client
/// fails) is ended here. Idle sessions are ended, and dropped ones swept, by a scheduled job ([#maintain()]).
@Singleton
class StroomMcpServer {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StroomMcpServer.class);

    /// Where the transport keeps the user each request was made as.
    static final String USER_IDENTITY_KEY = "stroom.userIdentity";
    /// Where the transport keeps the [McpRequestEnd] of each request.
    static final String REQUEST_END_KEY = "stroom.requestEnd";
    /// Where the Reactor context of a request's work keeps the id of the client's request.
    static final String REQUEST_ID_KEY = "stroom.mcpRequestId";
    /// The notification a client sends to cancel one of its requests.
    static final String METHOD_NOTIFICATION_CANCELLED = "notifications/cancelled";
    static final String SERVER_NAME = "Stroom";
    /// How long a session that the transport doesn't hold is left before it is taken as dropped: the transport
    /// holds a new session only once it has been started.
    static final Duration DROPPED_AFTER = Duration.ofMinutes(1);
    /// How long telling one session that its tools have changed may take.
    static final Duration NOTIFY_TIMEOUT = Duration.ofSeconds(10);
    /// What a client is told about Stroom's tools when it connects: how they run here, then the Stroom MCP
    /// server's instructions (sent to every client at connection, so the rules hold whichever client runs the
    /// model and whether or not it uses the prompts), as they apply to Stroom.
    static final String INSTRUCTIONS = """
            These are Stroom's own tools. Each runs as the signed-in user, with their permissions, and only those \
            their tool policies allow are listed. A call that changes something in Stroom asks the user first. A \
            large result is saved to this session's workspace (a path under /outputs/), which the workspace tools \
            (read_file, grep, jq, table and others) read; the workspace lasts as long as the session.

            """ + BundledKnowledge.serverInstructions();

    private static final ThreadPool NOTIFY_THREAD_POOL = new ThreadPoolImpl("MCP Session Notifications");

    private final SecurityContext securityContext;
    private final McpTools tools;
    private final McpResources resources;
    private final McpWorkspaces workspaces;
    private final Provider<AiConfig> aiConfigProvider;
    private final Provider<BuildInfo> buildInfoProvider;
    private final Map<String, OpenSession> sessions = new ConcurrentHashMap<>();
    private final HttpServletStreamableServerTransportProvider transport;
    private final TransportSessions transportSessions;
    private final Map<String, McpRequestHandler<?>> requestHandlers;
    private final Map<String, McpNotificationHandler> notificationHandlers;
    private final Scheduler notifyScheduler;
    // Held while a session is started, so a user's session limit holds for sessions started at once.
    private final Object startLock = new Object();

    @Inject
    StroomMcpServer(final SecurityContext securityContext,
                    final McpTools tools,
                    final McpResources resources,
                    final McpWorkspaces workspaces,
                    final Provider<AiConfig> aiConfigProvider,
                    final Provider<BuildInfo> buildInfoProvider,
                    final AiToolPolicyService policies,
                    final ExecutorProvider executorProvider) {
        this.securityContext = securityContext;
        this.tools = tools;
        this.resources = resources;
        this.workspaces = workspaces;
        this.aiConfigProvider = aiConfigProvider;
        this.buildInfoProvider = buildInfoProvider;
        this.transport = HttpServletStreamableServerTransportProvider.builder()
                .mcpEndpoint(McpServlet.PATH)
                .jsonMapper(McpJsonDefaults.getMapper())
                .contextExtractor(request -> McpTransportContext.create(transportContext(
                        securityContext.getUserIdentity(), McpRequestEnd.of(request))))
                .keepAliveInterval(config().getKeepAliveInterval().getDuration())
                .build();
        this.transportSessions = new TransportSessions(transport);
        this.requestHandlers = requestHandlers();
        this.notificationHandlers = notificationHandlers();
        this.notifyScheduler = Schedulers.fromExecutor(executorProvider.get(NOTIFY_THREAD_POOL));
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
    /// @param user      The user making the request.
    /// @return True if the session is open on this node (here and in the transport), and is the user's. A request
    /// for any other session is answered 404, so the client starts a new one, as the protocol says: one that has
    /// ended, that is on another node, or that isn't theirs.
    boolean isOpenFor(final String sessionId, final UserRef user) {
        final OpenSession open = sessionId == null
                ? null
                : sessions.get(sessionId);
        return open != null
               && transportSessions.has(sessionId)
               && user != null
               && Objects.equals(open.session().owner().getUuid(), user.getUuid());
    }

    /// The scheduled upkeep of the sessions (see [McpModule]): ends idle sessions, and those the transport has
    /// dropped.
    void maintain() {
        final long nowMs = System.currentTimeMillis();
        endIdleSessions(nowMs);
        endDroppedSessions(nowMs);
    }

    /// Ends the sessions that have had no requests for the session retention time, and have no call running,
    /// deleting their workspaces: a client that goes away without ending its session leaves it open.
    ///
    /// @param nowMs The time now.
    void endIdleSessions(final long nowMs) {
        final long retentionMs = config().getSessionRetention().toMillis();
        if (retentionMs <= 0) {
            return;
        }
        sessions.values().stream()
                .filter(open -> open.session().lastUsedMs() < nowMs - retentionMs)
                .filter(open -> open.session().runningCalls().count() == 0)
                .toList()
                .forEach(open -> end(open, "idle since " + Instant.ofEpochMilli(open.session().lastUsedMs())));
    }

    /// Ends the sessions the transport no longer holds: it drops a session when a send to its client fails.
    ///
    /// @param nowMs The time now.
    void endDroppedSessions(final long nowMs) {
        final Set<String> held = transportSessions.ids();
        if (held == null) {
            return;
        }
        sessions.values().stream()
                .filter(open -> !held.contains(open.session().id()))
                .filter(open -> open.session().startedMs() < nowMs - DROPPED_AFTER.toMillis())
                .toList()
                .forEach(open -> end(open, "dropped by the transport"));
    }

    // Ends a session: out of the transport first, so nothing more reaches it, then closed, which forgets it here
    // (see closed) and cancels its calls.
    private void end(final OpenSession open, final String why) {
        final McpSession session = open.session();
        LOGGER.info(() -> LogUtil.message("Ending an MCP session of {}, {}", session.owner().toDisplayString(), why));
        LOGGER.debug(() -> LogUtil.message("Ending MCP session {}, {}", session.id(), why));
        transportSessions.remove(session.id());
        try {
            open.sdkSession().close();
        } finally {
            forget(session.id());
        }
    }

    // A client's initialize request starts a session, on the request's thread, so the user is the one signed in.
    private McpStreamableServerSessionInit startSession(final InitializeRequest initializeRequest) {
        final UserRef owner = securityContext.getUserRef();
        if (owner == null) {
            throw McpError.builder(McpSchema.ErrorCodes.INVALID_REQUEST)
                    .message("Sign in to Stroom to use its MCP endpoint.")
                    .build();
        }
        final McpStreamableServerSessionInit init;
        synchronized (startLock) {
            makeRoomFor(owner);
            final String id = UUID.randomUUID().toString();
            final TrackedSession session = new TrackedSession(id, initializeRequest.capabilities(),
                    initializeRequest.clientInfo(), config().getFormTimeout().getDuration(), requestHandlers,
                    notificationHandlers, () -> closed(id), McpJsonDefaults.getSchemaValidator());
            init = new McpStreamableServerSessionInit(session, initialize(initializeRequest));
            sessions.put(id, new OpenSession(new McpSession(id, owner, initializeRequest.clientInfo(),
                    initializeRequest.capabilities()), session));
        }
        LOGGER.info(() -> LogUtil.message(
                "MCP session started for {} by {}",
                owner.toDisplayString(),
                NullSafe.get(initializeRequest.clientInfo(), Implementation::name)));
        LOGGER.debug(() -> LogUtil.message(
                "MCP session {} started for {}",
                init.session().getId(),
                owner.toDisplayString()));
        return init;
    }

    // A user may have only so many sessions: when they are at the limit, their least recently used session with no
    // call running is ended (clients often go away without ending theirs, and the session retention time is long);
    // if every one has a call running, the new session is refused.
    private void makeRoomFor(final UserRef owner) {
        final int max = config().getMaxSessionsPerUser();
        final List<OpenSession> owned = sessions.values().stream()
                .filter(open -> Objects.equals(open.session().owner().getUuid(), owner.getUuid()))
                .toList();
        if (owned.size() < max) {
            return;
        }
        final List<OpenSession> idle = owned.stream()
                .filter(open -> open.session().runningCalls().count() == 0)
                .sorted(Comparator.comparingLong(open -> open.session().lastUsedMs()))
                .toList();
        if (idle.isEmpty()) {
            throw McpError.builder(McpSchema.ErrorCodes.INVALID_REQUEST)
                    .message("You have " + owned.size() + " MCP sessions open on this node, each with a call "
                             + "running, as many as you may. Wait for a call to finish, or end a session.")
                    .build();
        }
        // As many as it takes, should the limit have been lowered.
        idle.stream()
                .limit(owned.size() - max + 1L)
                .forEach(open -> end(open, "to make room for a new one (at most " + max + " per user)"));
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

    private Map<String, McpNotificationHandler> notificationHandlers() {
        return Map.of(
                McpSchema.METHOD_NOTIFICATION_INITIALIZED, (exchange, params) -> Mono.empty(),
                METHOD_NOTIFICATION_CANCELLED, (exchange, params) -> Mono.fromRunnable(() ->
                        cancelled(exchange.sessionId(), params)));
    }

    // The client gave up on one of its requests: the work on it is stopped. The servlet has checked that the
    // session is the user's.
    private void cancelled(final String sessionId, final Object params) {
        final OpenSession open = sessionId == null
                ? null
                : sessions.get(sessionId);
        final Object requestId = params instanceof final Map<?, ?> map
                ? map.get("requestId")
                : null;
        if (open != null && requestId != null) {
            final boolean running = open.session().runningCalls().cancel(requestId);
            LOGGER.debug(
                    "The client cancelled request {} of MCP session {}{}",
                    requestId,
                    sessionId,
                    running
                            ? ""
                            : ", which had already ended");
        }
    }

    private static CallToolRequest toCallToolRequest(final Object params) {
        return McpJsonDefaults.getMapper().convertValue(params, CallToolRequest.class);
    }

    // Does the work of a request as its user, on the thread serving it, refusing it if the session isn't theirs.
    // The work is cancelled (its thread interrupted, so a tool that checks stops) when the client cancels the
    // request, its HTTP request ends, or the session ends.
    private <T> Mono<T> asUser(final McpAsyncServerExchange exchange, final SessionWork<T> work) {
        final UserIdentity identity = (UserIdentity) exchange.transportContext().get(USER_IDENTITY_KEY);
        final McpRequestEnd requestEnd = (McpRequestEnd) exchange.transportContext().get(REQUEST_END_KEY);
        final McpSession session = NullSafe.get(sessions.get(exchange.sessionId()), OpenSession::session);
        if (identity == null || session == null) {
            return Mono.error(sessionEnded());
        }
        return Mono.deferContextual(context -> Mono.<T>create(sink -> {
            if (!workspaces.callStarted(session)) {
                sink.error(sessionEnded());
                return;
            }
            final T result;
            try (final RunningCall call = session.runningCalls().start(context.getOrDefault(REQUEST_ID_KEY, null))) {
                sink.onCancel(call::cancel);
                NullSafe.consume(requestEnd, end -> end.onEnd(call::cancel));
                result = securityContext.asUserResult(identity, () -> {
                    final UserRef user = securityContext.getUserRef();
                    if (user == null || !Objects.equals(user.getUuid(), session.owner().getUuid())) {
                        throw McpError.builder(McpSchema.ErrorCodes.INVALID_REQUEST)
                                .message("This MCP session belongs to another user. Start a new one.")
                                .build();
                    }
                    session.used();
                    return work.apply(session);
                });
            } catch (final RuntimeException e) {
                workspaces.callEnded(session);
                sink.error(e);
                return;
            }
            // Answered once the call can no longer be cancelled, so no interrupt reaches the thread as it answers.
            workspaces.callEnded(session);
            sink.success(result);
        }));
    }

    private static McpError sessionEnded() {
        return McpError.builder(McpSchema.ErrorCodes.INVALID_REQUEST)
                .message("This MCP session has ended. Start a new one.")
                .build();
    }

    private Mono<Void> closed(final String sessionId) {
        return Mono.fromRunnable(() -> forget(sessionId));
    }

    // Forgets a session that has ended, however it ended: its calls are cancelled, and its workspace deleted once
    // they have stopped.
    private void forget(final String sessionId) {
        transportSessions.remove(sessionId);
        final OpenSession open = sessions.remove(sessionId);
        if (open != null) {
            LOGGER.info(() -> LogUtil.message("An MCP session of {} ended", open.session().owner().toDisplayString()));
            LOGGER.debug("MCP session {} ended", sessionId);
            open.session().runningCalls().cancelAll();
            workspaces.delete(open.session());
        }
    }

    // Tells the sessions on this node that their tools may have changed, off the thread that saved the change,
    // each within NOTIFY_TIMEOUT, so a client that is slow to read holds up no one. Sessions on other nodes see the
    // change when their clients next list their tools.
    private void toolsChanged() {
        for (final OpenSession open : List.copyOf(sessions.values())) {
            open.sdkSession()
                    .sendNotification(McpSchema.METHOD_NOTIFICATION_TOOLS_LIST_CHANGED, null)
                    .subscribeOn(notifyScheduler)
                    .timeout(NOTIFY_TIMEOUT)
                    .subscribe(ignored -> {
                    }, e -> LOGGER.debug(() -> LogUtil.message(
                            "Unable to tell an MCP session its tools have changed: {}",
                            e.getMessage()),
                            e));
        }
    }

    private McpEndpointConfig config() {
        return NullSafe.getOrElse(aiConfigProvider.get(), AiConfig::getMcpEndpoint, new McpEndpointConfig());
    }

    // What the transport keeps for each request: who made it, and how to know when it ends.
    private static Map<String, Object> transportContext(final UserIdentity identity, final McpRequestEnd end) {
        final Map<String, Object> context = new HashMap<>();
        NullSafe.consume(identity, value -> context.put(USER_IDENTITY_KEY, value));
        NullSafe.consume(end, value -> context.put(REQUEST_END_KEY, value));
        return context;
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

    /// The MCP SDK's session, telling the work on each request the id of the client's request (in the Reactor
    /// context, as [#REQUEST_ID_KEY]), which the SDK doesn't give its handlers, so the client can cancel it.
    static final class TrackedSession extends McpStreamableServerSession {

        /// As [McpStreamableServerSession]'s, which see.
        ///
        /// @param id                   The session's id.
        /// @param clientCapabilities   What the client says it can do.
        /// @param clientInfo           The client's name and version.
        /// @param requestTimeout       How long a request to the client (e.g. a form) waits for its answer.
        /// @param requestHandlers      The handlers of the client's requests, by method.
        /// @param notificationHandlers The handlers of the client's notifications, by method.
        /// @param onClose              What is done when the session closes.
        /// @param jsonSchemaValidator  Checks tools' structured results against their schemas.
        TrackedSession(final String id,
                       final ClientCapabilities clientCapabilities,
                       final Implementation clientInfo,
                       final Duration requestTimeout,
                       final Map<String, McpRequestHandler<?>> requestHandlers,
                       final Map<String, McpNotificationHandler> notificationHandlers,
                       final Supplier<Mono<Void>> onClose,
                       final JsonSchemaValidator jsonSchemaValidator) {
            super(id, clientCapabilities, clientInfo, requestTimeout, requestHandlers, notificationHandlers, onClose,
                    jsonSchemaValidator);
        }

        /// @param request   The client's request.
        /// @param transport Where its answer is sent.
        /// @return The work on it, with its id in the Reactor context.
        @Override
        public Mono<Void> responseStream(final JSONRPCRequest request, final McpStreamableServerTransport transport) {
            final Mono<Void> work = super.responseStream(request, transport);
            return request.id() == null
                    ? work
                    : work.contextWrite(context -> context.put(REQUEST_ID_KEY, request.id()));
        }
    }
}
