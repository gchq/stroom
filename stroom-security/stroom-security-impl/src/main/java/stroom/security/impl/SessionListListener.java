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

import stroom.cluster.api.ClusterMember;
import stroom.cluster.api.ClusterService;
import stroom.cluster.api.EndpointUrlService;
import stroom.cluster.api.RemoteRestUtil;
import stroom.security.api.UserIdentity;
import stroom.security.shared.SessionDetails;
import stroom.security.shared.SessionListResponse;
import stroom.security.shared.SessionResource;
import stroom.task.api.TaskContextFactory;
import stroom.util.jersey.UriBuilderUtil;
import stroom.util.jersey.WebTargetFactory;
import stroom.util.logging.LogUtil;
import stroom.util.servlet.UserAgentSessionUtil;
import stroom.util.shared.ResourcePaths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Singleton
class SessionListListener implements HttpSessionListener, SessionListService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SessionListListener.class);

    private final ConcurrentHashMap<String, HttpSession> sessionMap = new ConcurrentHashMap<>();

    private final ClusterService clusterService;
    private final EndpointUrlService endpointUrlService;
    private final TaskContextFactory taskContextFactory;
    private final WebTargetFactory webTargetFactory;

    @Inject
    SessionListListener(final ClusterService clusterService,
                        final EndpointUrlService endpointUrlService,
                        final TaskContextFactory taskContextFactory,
                        final WebTargetFactory webTargetFactory) {
        this.clusterService = clusterService;
        this.endpointUrlService = endpointUrlService;
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

    public SessionListResponse listSessions(final String memberUuid) {
        final ClusterMember member = new ClusterMember(memberUuid);
        LOGGER.debug("listSessions(\"{}\") called", member);
        // TODO add audit logging?
        Objects.requireNonNull(member);

        final SessionListResponse sessionList;

        if (endpointUrlService.shouldExecuteLocally(member)) {
            // This is our node so execute locally
            sessionList = listSessionsOnThisNode();

        } else {
            // This is a different node so make a rest call to it to get the result

            final String url = endpointUrlService.getRemoteEndpointUrl(member) +
                    ResourcePaths.buildAuthenticatedApiPath(
                            SessionResource.BASE_PATH,
                            SessionResource.LIST_PATH_PART);

            try {
                LOGGER.debug("Sending request to {} for node {}", url, member);
                WebTarget webTarget = webTargetFactory.create(url);
                webTarget = UriBuilderUtil.addParam(webTarget, SessionResource.MEMBER_UUID_PARAM, member);
                final Response response = webTarget
                        .request(MediaType.APPLICATION_JSON)
                        .get();

                if (response.getStatus() != 200) {
                    throw new WebApplicationException(response);
                }

                sessionList = response.readEntity(SessionListResponse.class);
            } catch (Throwable e) {
                throw RemoteRestUtil.handleExceptions(member, url, e);
            }
        }
        return sessionList;
    }

    private SessionListResponse listSessionsOnThisNode() {
        return sessionMap.values().stream()
                .map(httpSession -> {
                    final Optional<UserIdentity> userIdentity = UserIdentitySessionUtil.get(httpSession);
                    return new SessionDetails(
                            userIdentity.map(UserIdentity::getId).orElse(null),
                            httpSession.getCreationTime(),
                            httpSession.getLastAccessedTime(),
                            UserAgentSessionUtil.get(httpSession),
                            clusterService.getLocal().toString());
                })
                .collect(SessionListResponse.collector(SessionListResponse::new));
    }

    public SessionListResponse listSessions() {
        return taskContextFactory.contextResult("Get session list on all active nodes", parentTaskContext ->
                clusterService.getMembers()
                        .stream()
                        .map(member -> {
                            final Supplier<SessionListResponse> listSessionsOnNodeTask =
                                    taskContextFactory.childContextResult(parentTaskContext,
                                            LogUtil.message("Get session list on node [{}]", member),
                                            taskContext ->
                                                    listSessions(member.getUuid()));

                            LOGGER.debug("Creating async task for node {}", member);
                            return CompletableFuture
                                    .supplyAsync(listSessionsOnNodeTask)
                                    .exceptionally(throwable -> {
                                        LOGGER.error("Error getting session list for node [{}]: {}. " +
                                                        "Enable DEBUG for stacktrace",
                                                member,
                                                throwable.getMessage());
                                        LOGGER.debug("Error getting session list for node [{}]",
                                                member, throwable);
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
