/*
 * Copyright 2020 Crown Copyright
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

package stroom.security.impl;

import stroom.node.api.FindNodeCriteria;
import stroom.node.api.NodeCallUtil;
import stroom.node.api.NodeInfo;
import stroom.node.api.NodeService;
import stroom.security.api.SecurityContext;
import stroom.security.common.impl.UserIdentitySessionUtil;
import stroom.security.shared.AppPermission;
import stroom.security.shared.HasUserRef;
import stroom.security.shared.SessionDetails;
import stroom.security.shared.SessionListResponse;
import stroom.security.shared.SessionResource;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContextFactory;
import stroom.util.jersey.UriBuilderUtil;
import stroom.util.jersey.WebTargetFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.servlet.UserAgentSessionUtil;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.UserRef;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionIdListener;
import jakarta.servlet.http.HttpSessionListener;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

@Singleton
class SessionListListener implements HttpSessionListener, HttpSessionIdListener, SessionListService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SessionListListener.class);

    private final ConcurrentHashMap<String, HttpSession> sessionIdToSessionMap = new ConcurrentHashMap<>();

    private final NodeInfo nodeInfo;
    private final NodeService nodeService;
    private final TaskContextFactory taskContextFactory;
    private final WebTargetFactory webTargetFactory;
    // Need to use the impl rather than iface as iface doesn't support all the
    // user/session/codeFlow stuff
    private final StroomUserIdentityFactory stroomUserIdentityFactory;
    private final SecurityContext securityContext;
    private final Executor executor;

    @Inject
    SessionListListener(final NodeInfo nodeInfo,
                        final NodeService nodeService,
                        final TaskContextFactory taskContextFactory,
                        final WebTargetFactory webTargetFactory,
                        final StroomUserIdentityFactory stroomUserIdentityFactory,
                        final SecurityContext securityContext,
                        final ExecutorProvider executorProvider) {
        this.nodeInfo = nodeInfo;
        this.nodeService = nodeService;
        this.taskContextFactory = taskContextFactory;
        this.webTargetFactory = webTargetFactory;
        this.stroomUserIdentityFactory = stroomUserIdentityFactory;
        this.securityContext = securityContext;
        this.executor = executorProvider.get();
    }

    @Override
    public void sessionCreated(final HttpSessionEvent event) {
        final HttpSession httpSession = event.getSession();
        final String sessionId = httpSession.getId();
        LOGGER.debug("sessionCreated() - sessionId: {}", sessionId);
        sessionIdToSessionMap.put(sessionId, httpSession);
    }

    @Override
    public void sessionDestroyed(final HttpSessionEvent event) {
        final HttpSession httpSession = event.getSession();
        try {
            // In case the session has been destroyed due to it expiring rather than an explicit
            // logout, we need to stop the refresh manager from trying to refresh tokens.
            UserIdentitySessionUtil.getUserFromSession(httpSession)
                    .ifPresent(stroomUserIdentityFactory::logoutUser);

            final UserRef userRef = getUserRefFromSession(httpSession);
            final String userAgent = UserAgentSessionUtil.getUserAgent(httpSession);
            final Instant createTime = Instant.ofEpochMilli(httpSession.getCreationTime());
            final Instant lastAccessedTime = Instant.ofEpochMilli(httpSession.getLastAccessedTime());
            LOGGER.info("sessionDestroyed() - {}, createTime: {} ({}), lastAccessTime: {} ({}), " +
                        "userRef: {}, userAgent: {}",
                    httpSession.getId(),
                    createTime,
                    Duration.between(createTime, Instant.now()),
                    lastAccessedTime,
                    Duration.between(lastAccessedTime, Instant.now()),
                    userRef,
                    userAgent);
        } finally {
            sessionIdToSessionMap.remove(httpSession.getId());
        }
    }

    @Override
    public void sessionIdChanged(final HttpSessionEvent event, final String oldSessionId) {
        final HttpSession httpSession = event.getSession();
        LOGGER.info("sessionIdChanged() - old: {}, new: {}", oldSessionId, httpSession.getId());
        // Re-key, or the entry stays under the old id forever: the container changes the session id on
        // authentication, and sessionDestroyed removes by the *current* id, so without this every login
        // leaves an orphan entry that outlives the session it points at.
        sessionIdToSessionMap.remove(oldSessionId);
        sessionIdToSessionMap.put(httpSession.getId(), httpSession);
    }

//    public ResultPage<SessionDetails> find(final BaseCriteria criteria) {
//        final ArrayList<SessionDetails> rtn = new ArrayList<>();
//        for (final HttpSession httpSession : sessionMap.values()) {
//
//            final UserIdentity userIdentity = UserIdentitySessionUtil.get(httpSession);
//
//            final SessionDetails sessionDetails = new SessionDetails(
//                    userIdentity != null ? userIdentity.getId() : null,
//                    httpSession.getCreationTime(),
//                    httpSession.getLastAccessedTime(),
//                    UserAgentSessionUtil.get(httpSession),
//                    nodeInfo.getThisNodeName());
//
//            rtn.add(sessionDetails);
//        }
//        return ResultPage.createUnboundedList(rtn);
//    }

    public SessionListResponse listSessions(final String nodeName) {
        return securityContext.secureResult(AppPermission.MANAGE_USERS_PERMISSION, () -> {
            LOGGER.debug("listSessions(\"{}\") called", nodeName);
            // TODO add audit logging?
            Objects.requireNonNull(nodeName);

            final SessionListResponse sessionList;

            if (NodeCallUtil.shouldExecuteLocally(nodeInfo, nodeName)) {
                // This is our node so execute locally
                sessionList = listSessionsOnThisNode();

            } else {
                // This is a different node so make a rest call to it to get the result
                final String url = NodeCallUtil.getBaseEndpointUrl(nodeInfo, nodeService, nodeName) +
                                   ResourcePaths.buildAuthenticatedApiPath(
                                           SessionResource.BASE_PATH,
                                           SessionResource.LIST_PATH_PART);

                try {
                    LOGGER.debug("Sending request to {} for node {}", url, nodeName);
                    WebTarget webTarget = webTargetFactory.create(url);
                    webTarget = UriBuilderUtil.addParam(webTarget, SessionResource.NODE_NAME_PARAM, nodeName);
                    try (final Response response = webTarget
                            .request(MediaType.APPLICATION_JSON)
                            .get()) {

                        if (response.getStatus() != 200) {
                            throw new WebApplicationException(response);
                        }

                        sessionList = response.readEntity(SessionListResponse.class);
                    }
                } catch (final Throwable e) {
                    throw NodeCallUtil.handleExceptionsOnNodeCall(nodeName, url, e);
                }
            }
            LOGGER.debug(() -> LogUtil.message("Returning {} session items", sessionList.size()));
            return sessionList;
        });
    }

    private SessionListResponse listSessionsOnThisNode() {
        final String thisNodeName = nodeInfo.getThisNodeName();
        return LOGGER.logDurationIfDebugEnabled(() ->
                        sessionIdToSessionMap.values().stream()
                                .map(httpSession -> describe(httpSession, thisNodeName))
                                .filter(Objects::nonNull)
                                .collect(SessionListResponse.collector(SessionListResponse::new)),
                () -> LogUtil.message("Obtain session list for this node ({})", thisNodeName));
    }

    /**
     * Describe one session, or null if it has been invalidated.
     * <p>
     * Every accessor on an invalidated {@link HttpSession} throws, and a session can be invalidated at any
     * moment - by logout, by expiry, or by another thread - including between taking the snapshot above and
     * reading it. Skipping the individual session matters because the alternative is losing the whole node's
     * list: the caller turns a failure here into an empty response for that node, so one dead session would
     * make it look as though nobody was signed in.
     * </p>
     */
    private SessionDetails describe(final HttpSession httpSession, final String thisNodeName) {
        try {
            return new SessionDetails(
                    getUserRefFromSession(httpSession),
                    httpSession.getCreationTime(),
                    httpSession.getLastAccessedTime(),
                    UserAgentSessionUtil.getUserAgent(httpSession),
                    thisNodeName,
                    sessionHandle(httpSession.getId()));
        } catch (final IllegalStateException e) {
            LOGGER.debug(() -> "Skipping a session that has been invalidated: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * A one-way handle for a session, safe to hand to a client.
     * <p>
     * The session id itself is the session cookie value, so it must never leave the server. Hashing gives a
     * stable name for a session that cannot be replayed as a credential.
     * </p>
     */
    static String sessionHandle(final String sessionId) {
        if (sessionId == null) {
            return null;
        }
        try {
            final byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(sessionId.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (final NoSuchAlgorithmException e) {
            // SHA-256 is required to be present on every JVM.
            throw new IllegalStateException(e);
        }
    }

    /**
     * The session's id, or null if it has been invalidated. Accessors on an invalidated session throw, and a
     * session being torn down concurrently must not break an operation that is walking the map.
     */
    private static String idOrNull(final HttpSession httpSession) {
        try {
            return httpSession.getId();
        } catch (final IllegalStateException e) {
            return null;
        }
    }

    private static String handleOrNull(final HttpSession httpSession) {
        return sessionHandle(idOrNull(httpSession));
    }

    private static UserRef ownerOrNull(final HttpSession httpSession) {
        try {
            return getUserRefFromSession(httpSession);
        } catch (final IllegalStateException e) {
            return null;
        }
    }

    private static UserRef getUserRefFromSession(final HttpSession httpSession) {
        return UserIdentitySessionUtil.getUserFromSession(httpSession)
                .filter(uid -> uid instanceof HasUserRef)
                .map(uid -> ((HasUserRef) uid).getUserRef())
                .orElse(null);
    }

    public SessionListResponse listSessions() {
        return securityContext.secureResult(AppPermission.MANAGE_USERS_PERMISSION, () ->
                taskContextFactory.contextResult("Get session list on all active nodes", parentTaskContext ->
                        nodeService.findNodeNames(FindNodeCriteria.allEnabled())
                                .stream()
                                .map(nodeName -> {
                                    final Supplier<SessionListResponse> listSessionsOnNodeTask =
                                            taskContextFactory.childContextResult(parentTaskContext,
                                                    LogUtil.message("Get session list on node [{}]", nodeName),
                                                    taskContext ->
                                                            listSessions(nodeName));

                                    LOGGER.debug("Creating async task for node {}", nodeName);
                                    return CompletableFuture
                                            .supplyAsync(listSessionsOnNodeTask, executor)
                                            .exceptionally(throwable -> {
                                                LOGGER.error("Error getting session list for node [{}]: {}. " +
                                                             "Enable DEBUG for stacktrace",
                                                        nodeName,
                                                        throwable.getMessage());
                                                LOGGER.debug("Error getting session list for node [{}]",
                                                        nodeName, throwable);
                                                // TODO do we want to silently ignore nodes that error?
                                                // If we can't talk to one node we still want to see the
                                                // results from the other nodes
                                                return SessionListResponse.empty();
                                            });
                                })
                                .map(CompletableFuture::join)
                                .reduce(SessionListResponse.reducer(
                                        SessionListResponse::new,
                                        SessionDetails.class))
                                .orElse(SessionListResponse.empty())).get());
    }

    @Override
    public void evictUserSessions(final String userSubjectId, final String exceptSessionId) {
        if (userSubjectId == null || userSubjectId.isBlank()) {
            return;
        }
        // Fan out to every node, mirroring listSessions(). Authorisation is enforced per node in
        // evictUserSessionsOnThisNode.
        nodeService.findNodeNames(FindNodeCriteria.allEnabled())
                .forEach(nodeName -> {
                    try {
                        evictUserSessionsOnNode(userSubjectId, exceptSessionId, nodeName);
                    } catch (final RuntimeException e) {
                        LOGGER.error("Error terminating sessions for user {} on node {}: {}. " +
                                     "Enable DEBUG for stacktrace", userSubjectId, nodeName, e.getMessage());
                        LOGGER.debug(() -> LogUtil.message(
                                "Error terminating sessions for user {} on node {}", userSubjectId, nodeName), e);
                    }
                });
    }

    @Override
    public int evictUserSessionsOnNode(final String userSubjectId,
                                       final String exceptSessionId,
                                       final String nodeName) {
        Objects.requireNonNull(nodeName);
        if (NodeCallUtil.shouldExecuteLocally(nodeInfo, nodeName)) {
            return evictUserSessionsOnThisNode(userSubjectId, exceptSessionId);
        }
        // A different node, so call it over REST.
        final String url = NodeCallUtil.getBaseEndpointUrl(nodeInfo, nodeService, nodeName)
                           + ResourcePaths.buildAuthenticatedApiPath(
                SessionResource.BASE_PATH, SessionResource.TERMINATE_PATH_PART);
        try {
            WebTarget webTarget = webTargetFactory.create(url);
            webTarget = UriBuilderUtil.addParam(webTarget, SessionResource.NODE_NAME_PARAM, nodeName);
            webTarget = UriBuilderUtil.addParam(webTarget, SessionResource.SUBJECT_ID_PARAM, userSubjectId);
            if (exceptSessionId != null) {
                webTarget = UriBuilderUtil.addParam(
                        webTarget, SessionResource.EXCEPT_SESSION_ID_PARAM, exceptSessionId);
            }
            try (final Response response = webTarget
                    .request(MediaType.APPLICATION_JSON)
                    // The endpoint @Consumes JSON and takes no body, so send an empty JSON entity - a
                    // text/plain body would be rejected 415 by the remote node.
                    .post(Entity.json(""))) {
                if (response.getStatus() != 200) {
                    throw new WebApplicationException(response);
                }
                return response.readEntity(Integer.class);
            }
        } catch (final Throwable e) {
            throw NodeCallUtil.handleExceptionsOnNodeCall(nodeName, url, e);
        }
    }

    private int evictUserSessionsOnThisNode(final String userSubjectId, final String exceptSessionId) {
        // A user may terminate their own sessions; terminating another user's requires MANAGE_USERS.
        // Checked against the effective identity, which a remote node sees as the propagated run-as user.
        final UserRef currentUser = securityContext.getUserRef();
        final boolean terminatingOwnSessions = currentUser != null
                && Objects.equals(currentUser.getSubjectId(), userSubjectId);
        if (terminatingOwnSessions) {
            return doEvictUserSessionsOnThisNode(userSubjectId, exceptSessionId);
        }
        return securityContext.secureResult(AppPermission.MANAGE_USERS_PERMISSION, () ->
                doEvictUserSessionsOnThisNode(userSubjectId, exceptSessionId));
    }

    @Override
    public boolean evictSession(final String sessionHandle) {
        if (sessionHandle == null || sessionHandle.isBlank()) {
            return false;
        }
        // A session lives on exactly one node, so stop as soon as a node reports terminating it.
        // Authorisation is enforced per node in evictSessionOnThisNode.
        for (final String nodeName : nodeService.findNodeNames(FindNodeCriteria.allEnabled())) {
            try {
                if (evictSessionOnNode(sessionHandle, nodeName)) {
                    return true;
                }
            } catch (final RuntimeException e) {
                LOGGER.error("Error terminating session {} on node {}: {}. Enable DEBUG for stacktrace",
                        sessionHandle, nodeName, e.getMessage());
                LOGGER.debug(() -> LogUtil.message(
                        "Error terminating session {} on node {}", sessionHandle, nodeName), e);
            }
        }
        return false;
    }

    @Override
    public boolean evictSessionOnNode(final String sessionHandle, final String nodeName) {
        Objects.requireNonNull(nodeName);
        if (NodeCallUtil.shouldExecuteLocally(nodeInfo, nodeName)) {
            return evictSessionOnThisNode(sessionHandle);
        }
        // A different node, so call it over REST.
        final String url = NodeCallUtil.getBaseEndpointUrl(nodeInfo, nodeService, nodeName)
                           + ResourcePaths.buildAuthenticatedApiPath(
                SessionResource.BASE_PATH, SessionResource.TERMINATE_SESSION_PATH_PART);
        try {
            WebTarget webTarget = webTargetFactory.create(url);
            webTarget = UriBuilderUtil.addParam(webTarget, SessionResource.NODE_NAME_PARAM, nodeName);
            webTarget = UriBuilderUtil.addParam(
                    webTarget, SessionResource.SESSION_HANDLE_PARAM, sessionHandle);
            try (final Response response = webTarget
                    .request(MediaType.APPLICATION_JSON)
                    // The endpoint @Consumes JSON and takes no body, so send an empty JSON entity - a
                    // text/plain body would be rejected 415 by the remote node.
                    .post(Entity.json(""))) {
                if (response.getStatus() != 200) {
                    throw new WebApplicationException(response);
                }
                return Boolean.TRUE.equals(response.readEntity(Boolean.class));
            }
        } catch (final Throwable e) {
            throw NodeCallUtil.handleExceptionsOnNodeCall(nodeName, url, e);
        }
    }

    private boolean evictSessionOnThisNode(final String sessionHandle) {
        // Matched by handle rather than by id, because the caller is never given a session id - see
        // SessionDetails.getSessionHandle(). Linear over the sessions this node holds, which is a small map,
        // and only on an explicit admin action.
        final HttpSession session = sessionIdToSessionMap.values().stream()
                .filter(candidate -> Objects.equals(sessionHandle, handleOrNull(candidate)))
                .findFirst()
                .orElse(null);
        if (session == null) {
            // Not on this node. Deliberately no permission check and no distinct response - the caller
            // cannot tell "absent" from "not yours", and during a fan-out most nodes legitimately hit
            // this path, including for a user terminating their own session.
            return false;
        }
        // A user may terminate their own session; terminating another user's requires MANAGE_USERS.
        final UserRef sessionUser = getUserRefFromSession(session);
        final UserRef currentUser = securityContext.getUserRef();
        final boolean ownSession = currentUser != null
                                   && sessionUser != null
                                   && Objects.equals(currentUser.getSubjectId(), sessionUser.getSubjectId());
        if (ownSession) {
            return doEvictSessionOnThisNode(session, sessionUser);
        }
        return securityContext.secureResult(AppPermission.MANAGE_USERS_PERMISSION, () ->
                doEvictSessionOnThisNode(session, sessionUser));
    }

    private boolean doEvictSessionOnThisNode(final HttpSession session, final UserRef sessionUser) {
        // Capture the id up front - getId() throws once the session has been invalidated.
        final String sessionId = session.getId();
        try {
            session.invalidate();
            LOGGER.info(() -> LogUtil.message(
                    "Terminated session {} for user {} on this node", sessionId, sessionUser));
            return true;
        } catch (final IllegalStateException e) {
            // Already invalidated concurrently - nothing for this call to do.
            LOGGER.debug(() -> LogUtil.message("Session {} was already invalidated", sessionId), e);
            return false;
        }
    }

    private int doEvictUserSessionsOnThisNode(final String userSubjectId, final String exceptSessionId) {
        final List<HttpSession> toEvict = sessionIdToSessionMap.values().stream()
                .filter(session -> !Objects.equals(idOrNull(session), exceptSessionId))
                .filter(session -> {
                    final UserRef userRef = ownerOrNull(session);
                    return userRef != null && Objects.equals(userRef.getSubjectId(), userSubjectId);
                })
                .toList();
        int count = 0;
        for (final HttpSession session : toEvict) {
            try {
                session.invalidate();
                count++;
            } catch (final IllegalStateException e) {
                // Already invalidated concurrently - ignore.
            }
        }
        final int evicted = count;
        if (evicted > 0) {
            LOGGER.info(() -> LogUtil.message(
                    "Terminated {} session(s) for user {} on this node", evicted, userSubjectId));
        }
        return count;
    }
}
