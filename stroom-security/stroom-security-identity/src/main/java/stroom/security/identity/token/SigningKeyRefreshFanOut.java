/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.security.identity.token;

import stroom.node.api.FindNodeCriteria;
import stroom.node.api.NodeCallUtil;
import stroom.node.api.NodeInfo;
import stroom.node.api.NodeService;
import stroom.security.identity.shared.SigningKeyResource;
import stroom.security.openid.api.PublicJsonWebKeyProvider;
import stroom.util.jersey.UriBuilderUtil;
import stroom.util.jersey.WebTargetFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.ResourcePaths;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Objects;

/**
 * Tells every node in the cluster to forget its cached copy of the signing keys.
 * <p>
 * Revoking a key writes one row, but each node decides what to trust from a cache it holds for a minute at a
 * time. Without this the revocation would be honoured only by whichever node happened to serve the request,
 * and the administrator would be told it had worked. Mirrors how ending a user's sessions fans out.
 * </p>
 */
public class SigningKeyRefreshFanOut {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SigningKeyRefreshFanOut.class);

    private final NodeInfo nodeInfo;
    private final NodeService nodeService;
    private final WebTargetFactory webTargetFactory;
    private final Provider<PublicJsonWebKeyProvider> publicJsonWebKeyProviderProvider;

    @Inject
    public SigningKeyRefreshFanOut(final NodeInfo nodeInfo,
                               final NodeService nodeService,
                               final WebTargetFactory webTargetFactory,
                               final Provider<PublicJsonWebKeyProvider> publicJsonWebKeyProviderProvider) {
        this.nodeInfo = nodeInfo;
        this.nodeService = nodeService;
        this.webTargetFactory = webTargetFactory;
        this.publicJsonWebKeyProviderProvider = publicJsonWebKeyProviderProvider;
    }

    /**
     * A node that cannot be reached is logged rather than thrown, exactly as session eviction does: the
     * revocation itself has already happened and is durable, and failing the call would suggest otherwise.
     * The unreachable node reloads within its own cache window regardless.
     */
    public void refreshAllNodes() {
        nodeService.findNodeNames(FindNodeCriteria.allEnabled())
                .forEach(nodeName -> {
                    try {
                        refreshOnNode(nodeName);
                    } catch (final RuntimeException e) {
                        LOGGER.error("Error refreshing signing keys on node {}: {}. "
                                     + "It will reload on its own shortly. Enable DEBUG for stacktrace",
                                nodeName, e.getMessage());
                        LOGGER.debug(() -> LogUtil.message(
                                "Error refreshing signing keys on node {}", nodeName), e);
                    }
                });
    }

    public boolean refreshOnNode(final String nodeName) {
        Objects.requireNonNull(nodeName);
        if (NodeCallUtil.shouldExecuteLocally(nodeInfo, nodeName)) {
            publicJsonWebKeyProviderProvider.get().refresh();
            return true;
        }

        final String url = NodeCallUtil.getBaseEndpointUrl(nodeInfo, nodeService, nodeName)
                           + ResourcePaths.buildAuthenticatedApiPath(
                SigningKeyResource.BASE_PATH, SigningKeyResource.REFRESH_PATH_PART);
        try {
            WebTarget webTarget = webTargetFactory.create(url);
            webTarget = UriBuilderUtil.addParam(webTarget, SigningKeyResource.NODE_NAME_PARAM, nodeName);
            try (final Response response = webTarget
                    .request(MediaType.APPLICATION_JSON)
                    // The endpoint consumes JSON and takes no body, so send an empty JSON entity - a
                    // text/plain body would be refused 415 by the remote node.
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
}
