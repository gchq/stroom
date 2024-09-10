/*
 * Copyright 2017 Crown Copyright
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
import stroom.security.common.impl.UserIdentitySessionUtil;
import stroom.security.shared.HasUserRef;
import stroom.security.shared.SessionDetails;
import stroom.security.shared.SessionListResponse;
import stroom.security.shared.SessionResource;
import stroom.task.api.TaskContextFactory;
import stroom.util.jersey.UriBuilderUtil;
import stroom.util.jersey.WebTargetFactory;
import stroom.util.logging.LogUtil;
import stroom.util.servlet.UserAgentSessionUtil;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.UserRef;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Singleton
class SessionListListener implements HttpSessionListener, SessionListService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SessionListListener.class);

    private final ConcurrentHashMap<String, HttpSession> sessionMap = new ConcurrentHashMap<>();

    private final NodeInfo nodeInfo;
    private final NodeService nodeService;
    private final TaskContextFactory taskContextFactory;
    private final WebTargetFactory webTargetFactory;

    @Inject
    SessionListListener(final NodeInfo nodeInfo,
                        final NodeService nodeService,
                        final TaskContextFactory taskContextFactory,
                        final WebTargetFactory webTargetFactory) {
        this.nodeInfo = nodeInfo;
        this.nodeService = nodeService;
        this.taskContextFactory = taskContextFactory;
        this.webTargetFactory = webTargetFactory;
    }


    @Override
    public void sessionCreated(final HttpSessionEvent event) {
        final HttpSession httpSession = event.getSession();
        LOGGER.info("sessionCreated() - {}", httpSession.getId());
        sessionMap.put(httpSession.getId(), httpSession);
    }

    @Override
    public void sessionDestroyed(final HttpSessionEvent event) {
        final HttpSession httpSession = event.getSession();
        LOGGER.info("sessionDestroyed() - {}", httpSession.getId());
        sessionMap.remove(httpSession.getId());
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
                try (Response response = webTarget
                        .request(MediaType.APPLICATION_JSON)
                        .get()) {

                    if (response.getStatus() != 200) {
                        throw new WebApplicationException(response);
                    }

                    sessionList = response.readEntity(SessionListResponse.class);
                }
            } catch (Throwable e) {
                throw NodeCallUtil.handleExceptionsOnNodeCall(nodeName, url, e);
            }
        }
        return sessionList;
    }

    private SessionListResponse listSessionsOnThisNode() {
        return sessionMap.values().stream()
                .map(httpSession -> {
                    final UserRef userRef = UserIdentitySessionUtil.get(httpSession)
                            .filter(uid -> uid instanceof HasUserRef)
                            .map(uid -> ((HasUserRef) uid).getUserRef())
                            .orElse(null);
                    return new SessionDetails(
                            userRef,
                            httpSession.getCreationTime(),
                            httpSession.getLastAccessedTime(),
                            UserAgentSessionUtil.get(httpSession),
                            nodeInfo.getThisNodeName());
                })
                .collect(SessionListResponse.collector(SessionListResponse::new));
    }

    public SessionListResponse listSessions() {
        return taskContextFactory.contextResult("Get session list on all active nodes", parentTaskContext ->
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
                                    .supplyAsync(listSessionsOnNodeTask)
                                    .exceptionally(throwable -> {
                                        LOGGER.error("Error getting session list for node [{}]: {}. " +
                                                        "Enable DEBUG for stacktrace",
                                                nodeName,
                                                throwable.getMessage());
                                        LOGGER.debug("Error getting session list for node [{}]",
                                                nodeName, throwable);
                                        // TODO do we want to silently ignore nodes that error?
                                        // If we can't talk to one node we still want to see the results from the
                                        // other nodes
                                        return SessionListResponse.empty();
                                    });
                        })
                        .map(CompletableFuture::join)
                        .reduce(SessionListResponse.reducer(
                                SessionListResponse::new,
                                SessionDetails.class))
                        .orElse(SessionListResponse.empty())).get();
    }
}
