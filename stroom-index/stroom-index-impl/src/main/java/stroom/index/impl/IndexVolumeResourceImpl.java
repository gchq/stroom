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

package stroom.index.impl;

import stroom.entity.shared.ExpressionCriteria;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.index.shared.IndexVolume;
import stroom.index.shared.IndexVolumeResource;
import stroom.index.shared.ValidationResult;
import stroom.node.api.NodeCallUtil;
import stroom.node.api.NodeInfo;
import stroom.node.api.NodeService;
import stroom.util.jersey.UriBuilderUtil;
import stroom.util.jersey.WebTargetFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.rest.RestUtil;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@AutoLogged
class IndexVolumeResourceImpl implements IndexVolumeResource {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(IndexVolumeResourceImpl.class);

    private final Provider<IndexVolumeService> indexVolumeServiceProvider;
    private final Provider<NodeService> nodeServiceProvider;
    private final Provider<NodeInfo> nodeInfoProvider;
    private final Provider<WebTargetFactory> webTargetFactoryProvider;

    @Inject
    public IndexVolumeResourceImpl(final Provider<IndexVolumeService> indexVolumeServiceProvider,
                                   final Provider<NodeService> nodeServiceProvider,
                                   final Provider<NodeInfo> nodeInfoProvider,
                                   final Provider<WebTargetFactory> webTargetFactoryProvider) {
        this.indexVolumeServiceProvider = indexVolumeServiceProvider;
        this.nodeServiceProvider = nodeServiceProvider;
        this.nodeInfoProvider = nodeInfoProvider;
        this.webTargetFactoryProvider = webTargetFactoryProvider;
    }

    @Override
    public ResultPage<IndexVolume> find(final ExpressionCriteria criteria) {
        return indexVolumeServiceProvider.get().find(criteria);
    }

    @Override
    public ValidationResult validate(final IndexVolume indexVolume) {
        if (indexVolume.isShared()) {
            return validateSharedVolume(indexVolume);
        } else if (indexVolume.getNodeName() == null || indexVolume.getNodeName().isEmpty()) {
            return indexVolumeServiceProvider.get().validate(indexVolume);
        } else {
            return nodeServiceProvider.get().remoteRestResult(
                    indexVolume.getNodeName(),
                    ValidationResult.class,
                    () -> ResourcePaths.buildAuthenticatedApiPath(
                            IndexVolumeResource.BASE_PATH,
                            IndexVolumeResource.VALIDATE_SUB_PATH),
                    () ->
                            indexVolumeServiceProvider.get().validate(indexVolume),
                    builder ->
                            builder.post(Entity.json(indexVolume)));
        }
    }

    // A shared volume has no node, so the rules are checked on this node and then every other node is asked
    // whether it can use the path. A node that lacks the mount would otherwise write its shards to a local
    // directory that no other node can read.
    private ValidationResult validateSharedVolume(final IndexVolume indexVolume) {
        final IndexVolumeService indexVolumeService = indexVolumeServiceProvider.get();
        final ValidationResult localResult = indexVolumeService.validate(indexVolume);
        if ((!localResult.isOk() && !localResult.isWarning()) || !isPathOrTypeChanged(indexVolume)) {
            return localResult;
        }

        final String thisNodeName = nodeInfoProvider.get().getThisNodeName();
        final List<String> failures = new ArrayList<>();
        final List<String> unreachableNodes = new ArrayList<>();
        for (final String nodeName : nodeServiceProvider.get().getEnabledNodesByPriority()) {
            if (!Objects.equals(nodeName, thisNodeName)) {
                try {
                    final ValidationResult nodeResult = nodeServiceProvider.get().remoteRestResult(
                            nodeName,
                            ValidationResult.class,
                            () -> ResourcePaths.buildAuthenticatedApiPath(
                                    IndexVolumeResource.BASE_PATH,
                                    IndexVolumeResource.VALIDATE_PATH_SUB_PATH),
                            () -> indexVolumeService.validatePath(indexVolume),
                            builder -> builder.post(Entity.json(indexVolume)));
                    if (nodeResult == null) {
                        unreachableNodes.add(nodeName);
                    } else if (!nodeResult.isOk()) {
                        failures.add("Node '" + nodeName + "': " + nodeResult.getMessage());
                    }
                } catch (final RuntimeException e) {
                    LOGGER.debug(() -> LogUtil.message(
                            "Unable to check the path of a shared index volume on node {}: {}",
                            nodeName, LogUtil.exceptionMessage(e)), e);
                    unreachableNodes.add(nodeName);
                }
            }
        }

        if (!failures.isEmpty()) {
            return ValidationResult.error(
                    "Every node must be able to use the path of a shared volume, but these cannot:\n"
                    + String.join("\n", failures));
        } else if (!unreachableNodes.isEmpty()) {
            final String unreachableMessage = LogUtil.message("""
                            The path could not be checked on node{} [{}] because {} could not be reached.
                            Only continue if the path is the same shared mount on {}.
                            Click OK to mark the volume as shared anyway.""",
                    unreachableNodes.size() > 1
                            ? "s"
                            : "",
                    unreachableNodes.stream()
                            .map(nodeName -> "'" + nodeName + "'")
                            .collect(Collectors.joining(", ")),
                    unreachableNodes.size() > 1
                            ? "they"
                            : "it",
                    unreachableNodes.size() > 1
                            ? "those nodes"
                            : "that node");
            return ValidationResult.warning(localResult.isWarning()
                    ? localResult.getMessage() + "\n\n" + unreachableMessage
                    : unreachableMessage);
        } else {
            return localResult;
        }
    }

    // The other nodes only need to be asked when the volume is new, is being marked as shared or is being
    // given another path.
    private boolean isPathOrTypeChanged(final IndexVolume indexVolume) {
        if (indexVolume.getId() == null) {
            return true;
        }
        final IndexVolume existing = indexVolumeServiceProvider.get().read(indexVolume.getId());
        return existing == null
               || !existing.isShared()
               || !Objects.equals(existing.getPath(), indexVolume.getPath());
    }

    @Override
    public ValidationResult validatePath(final IndexVolume indexVolume) {
        return indexVolumeServiceProvider.get().validatePath(indexVolume);
    }

    @Override
    public IndexVolume create(final IndexVolume indexVolume) {
        return indexVolumeServiceProvider.get().create(indexVolume);
    }

    @Override
    public IndexVolume fetch(final Integer id) {
        return indexVolumeServiceProvider.get().read(id);
    }

    @Override
    public IndexVolume update(final Integer id, final IndexVolume indexVolume) {
        return indexVolumeServiceProvider.get().update(indexVolume);
    }

    @Override
    public Boolean delete(final Integer id) {
        return indexVolumeServiceProvider.get().delete(id);
    }

    @Override
    public Boolean rescan(final String nodeName) {
        RestUtil.requireNonNull(nodeName, "nodeName not supplied");

        // If this is the node that was contacted then just resolve it locally
        if (NodeCallUtil.shouldExecuteLocally(nodeInfoProvider.get(), nodeName)) {
            indexVolumeServiceProvider.get().rescan();
        } else {
            final String url = NodeCallUtil.getBaseEndpointUrl(nodeInfoProvider.get(),
                    nodeServiceProvider.get(), nodeName)
                    + ResourcePaths.buildAuthenticatedApiPath(
                    IndexVolumeResource.BASE_PATH,
                    IndexVolumeResource.RESCAN_SUB_PATH);
            try {
                // A different node to make a rest call to the required node
                WebTarget webTarget = webTargetFactoryProvider.get().create(url);
                webTarget = UriBuilderUtil.addParam(webTarget, "nodeName", nodeName);
                final Response response = webTarget
                        .request(MediaType.APPLICATION_JSON)
                        .get();
                if (response.getStatus() == Status.NOT_FOUND.getStatusCode()) {
                    throw new NotFoundException(response);
                } else if (response.getStatus() != Status.OK.getStatusCode()) {
                    throw new WebApplicationException(response);
                }

                return response.readEntity(Boolean.class);
            } catch (final Throwable e) {
                throw NodeCallUtil.handleExceptionsOnNodeCall(nodeName, url, e);
            }
        }

        return true;
    }
}
