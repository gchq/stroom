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

package stroom.planb.impl.data;

import stroom.cluster.task.api.TargetNodeSetFactory;
import stroom.node.api.NodeCallException;
import stroom.node.api.NodeCallUtil;
import stroom.node.api.NodeInfo;
import stroom.node.api.NodeService;
import stroom.planb.impl.PlanBConfig;
import stroom.security.api.SecurityContext;
import stroom.task.api.ExecutorProvider;
import stroom.util.jersey.WebTargetFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PermissionException;
import stroom.util.shared.ResourcePaths;
import stroom.util.zip.ZipUtil;

import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Singleton
public class FileTransferClientImpl implements FileTransferClient {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FileTransferClientImpl.class);

    private final Provider<PlanBConfig> configProvider;
    private final NodeService nodeService;
    private final NodeInfo nodeInfo;
    private final TargetNodeSetFactory targetNodeSetFactory;
    private final WebTargetFactory webTargetFactory;
    private final PartDestination partDestination;
    private final SecurityContext securityContext;
    private final Executor executor;

    @Inject
    public FileTransferClientImpl(final Provider<PlanBConfig> configProvider,
                                  final NodeService nodeService,
                                  @Nullable final NodeInfo nodeInfo,
                                  @Nullable final TargetNodeSetFactory targetNodeSetFactory,
                                  final WebTargetFactory webTargetFactory,
                                  final PartDestination partDestination,
                                  final SecurityContext securityContext,
                                  final ExecutorProvider executorProvider) {
        this.configProvider = configProvider;
        this.nodeService = nodeService;
        this.nodeInfo = nodeInfo;
        this.targetNodeSetFactory = targetNodeSetFactory;
        this.webTargetFactory = webTargetFactory;
        this.partDestination = partDestination;
        this.securityContext = securityContext;
        this.executor = executorProvider.get();
    }

    @Override
    public void storePart(final FileDescriptor fileDescriptor,
                          final Path path,
                          final boolean synchroniseMerge) {
        securityContext.asProcessingUser(() -> {
            final Set<String> targetNodes = new HashSet<>();

            // Now post to all nodes.
            final PlanBConfig planBConfig = configProvider.get();
            final List<String> configuredNodes = planBConfig.getNodeList();
            if (configuredNodes == null || configuredNodes.isEmpty()) {
                LOGGER.warn("No node list configured for PlanB, assuming this is a single node test setup");
                if (nodeInfo != null) {
                    targetNodes.add(nodeInfo.getThisNodeName());
                }

            } else {
                try {
                    if (targetNodeSetFactory != null) {
                        final Set<String> enabledNodes = targetNodeSetFactory.getEnabledTargetNodeSet();
                        for (final String node : configuredNodes) {
                            if (enabledNodes.contains(node)) {
                                targetNodes.add(node);
                            } else {
                                throw new RuntimeException("Plan B target node '" +
                                                           node +
                                                           "' is not enabled");
                            }
                        }
                    }
                } catch (final Exception e) {
                    LOGGER.error(e::getMessage, e);
                    throw new RuntimeException(e.getMessage(), e);
                }
            }

            // Send the data to all nodes.
            final List<CompletableFuture<?>> futures = new ArrayList<>(targetNodes.size());
            final List<RuntimeException> collectedExceptions = Collections.synchronizedList(new ArrayList<>());
            for (final String nodeName : targetNodes) {
                futures.add(CompletableFuture.runAsync(() ->
                        securityContext.asProcessingUser(() -> {
                            try {
                                LOGGER.debug(() -> LogUtil.message(
                                        "Plan B sending data {} to {}",
                                        fileDescriptor.getInfo(path),
                                        nodeName));

                                if (nodeInfo == null || NodeCallUtil.shouldExecuteLocally(nodeInfo, nodeName)) {
                                    // Allow file move if the only target is the local node.
                                    final boolean allowMove = targetNodes.size() == 1;
                                    storePartLocally(
                                            fileDescriptor,
                                            path,
                                            allowMove,
                                            synchroniseMerge);
                                } else {
                                    storePartRemotely(
                                            nodeName,
                                            fileDescriptor,
                                            path,
                                            synchroniseMerge);
                                }
                            } catch (final IOException e) {
                                LOGGER.error(e::getMessage, e);
                                final UncheckedIOException uncheckedIOException = new UncheckedIOException(e);
                                collectedExceptions.add(uncheckedIOException);
                                throw uncheckedIOException;
                            }
                        }), executor));
            }

            // Wait for all futures to complete or cancel them all and throw an exception if one fails.
            try {
                allOfTerminateOnFailure(futures).join();
            } catch (final RuntimeException e) {
                // If we collected an exception then throw that or else throw the completion exception.
                if (!collectedExceptions.isEmpty()) {
                    throw collectedExceptions.getFirst();
                } else {
                    throw e;
                }
            }
        });
    }

    private static CompletableFuture<?> allOfTerminateOnFailure(final List<CompletableFuture<?>> futures) {
        final CompletableFuture<Void> failure = new CompletableFuture<>();
        for (final CompletableFuture<?> f : futures) {
            f.exceptionally(ex -> {
                failure.completeExceptionally(ex);
                return null;
            });
        }
        failure.exceptionally(ex -> {
            futures.forEach(f -> f.cancel(true));
            return null;
        });
        return CompletableFuture.anyOf(failure, CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])));
    }

    private void storePartLocally(final FileDescriptor fileDescriptor,
                                  final Path path,
                                  final boolean allowMove,
                                  final boolean synchroniseMerge) throws IOException {
        partDestination.receiveLocalPart(fileDescriptor, path, allowMove, synchroniseMerge);
    }

    private void storePartRemotely(final String targetNode,
                                   final FileDescriptor fileDescriptor,
                                   final Path path,
                                   final boolean synchroniseMerge) throws IOException {
        final String baseEndpointUrl = NodeCallUtil.getBaseEndpointUrl(nodeInfo, nodeService, targetNode);
        final String url = baseEndpointUrl + ResourcePaths.buildAuthenticatedApiPath(FileTransferResource.BASE_PATH,
                FileTransferResource.SEND_PART_PATH_PART);
        final WebTarget webTarget = webTargetFactory.create(url);
        try {
            storePartRemotely(webTarget, fileDescriptor, path, synchroniseMerge);
        } catch (final Exception e) {
            LOGGER.error(e::getMessage, e);
            throw new IOException("Unable to send file to '" + targetNode + "': " + e.getMessage(), e);
        }
    }

    void storePartRemotely(final WebTarget webTarget,
                           final FileDescriptor fileDescriptor,
                           final Path path,
                           final boolean synchroniseMerge) throws IOException {
        try (final InputStream inputStream = new BufferedInputStream(Files.newInputStream(path))) {
            try (final Response response = webTarget
                    .request()
                    .header("createTime", fileDescriptor.createTimeMs())
                    .header("metaId", fileDescriptor.metaId())
                    .header("fileHash", fileDescriptor.fileHash())
                    .header("fileName", path.getFileName().toString())
                    .header("synchroniseMerge", synchroniseMerge)
                    .post(Entity.entity(inputStream, MediaType.APPLICATION_OCTET_STREAM))) {
                if (response.getStatus() == Status.UNAUTHORIZED.getStatusCode()) {
                    throw new PermissionException(null, response.getStatusInfo().getReasonPhrase());
                } else if (response.getStatus() != Status.OK.getStatusCode()) {
                    throw new RuntimeException(response.getStatusInfo().getReasonPhrase());
                }
            }
        }
    }

    @Override
    public Instant fetchSnapshot(final String nodeName,
                                 final SnapshotRequest request,
                                 final Path snapshotDir) {
        return securityContext.asProcessingUserResult(() -> {
            String url = null;
            try {
                LOGGER.info(() -> "Fetching snapshot from '" +
                                  nodeName +
                                  "' for '" +
                                  request.getPlanBDocRef() +
                                  "'");
                url = NodeCallUtil.getBaseEndpointUrl(nodeInfo, nodeService, nodeName)
                      + ResourcePaths.buildAuthenticatedApiPath(
                        FileTransferResource.BASE_PATH,
                        FileTransferResource.FETCH_SNAPSHOT_PATH_PART);
                final WebTarget webTarget = webTargetFactory.create(url);
                return fetchSnapshot(webTarget, request, snapshotDir);
            } catch (final Exception e) {
                // Distinguish 'we couldn't reach this node' from 'this node answered and told us no'. Only the
                // former is worth trying another node for, as every configured node holds a copy of the same
                // data, so an answer from one is the answer from all. See gh-5689.
                if (isUnreachable(e)) {
                    throw new NodeCallException(nodeName, url, e);
                }

                throw new RuntimeException("Error fetching snapshot from '" +
                                           nodeName +
                                           "' for '" +
                                           request.getPlanBDocRef() +
                                           "': " +
                                           e.getMessage(), e);
            }
        });
    }

    /**
     * Is this failure the node being unreachable, rather than the node answering with an error? Only transport
     * level failures count, so a response of any status, including a server error, is treated as an answer.
     */
    private static boolean isUnreachable(final Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ConnectException
                || current instanceof UnknownHostException
                || current instanceof NoRouteToHostException
                || current instanceof SocketTimeoutException) {
                return true;
            }
            current = current.getCause() == current
                    ? null
                    : current.getCause();
        }
        return false;
    }

    Instant fetchSnapshot(final WebTarget webTarget,
                          final SnapshotRequest request,
                          final Path snapshotDir) throws IOException {
        try (final Response response = webTarget
                .request(MediaType.APPLICATION_OCTET_STREAM)
                .post(Entity.json(request))) {
            if (response.getStatus() == Status.NOT_MODIFIED.getStatusCode()) {
                throw new NotModifiedException(describeError(response));
            } else if (response.getStatus() == Status.UNAUTHORIZED.getStatusCode()) {
                throw new PermissionException(null, describeError(response));
            } else if (response.getStatus() == Status.NOT_FOUND.getStatusCode()) {
                throw new SnapshotNotFoundException(describeError(response));
            } else if (response.getStatus() != Status.OK.getStatusCode()) {
                throw new RuntimeException(describeError(response));
            }

            try (final InputStream stream = (InputStream) response.getEntity()) {
                // Should be OK to unzip from an inputStream as stroom is in full control of the
                // ZIP creation, so we won't have any spurious zip entries.
                ZipUtil.unzip(stream, snapshotDir);
            }
            final String info = Files.readString(snapshotDir.resolve(Shard.SNAPSHOT_INFO_FILE_NAME));
            return Instant.parse(info);
        }
    }

    /**
     * Describe an error response. The remote end puts the reason in the response body because the status reason
     * phrase is just the generic text for the status code, e.g. 'Not Found', which says nothing about the cause.
     */
    private String describeError(final Response response) {
        final int status = response.getStatus();
        final String reasonPhrase = response.getStatusInfo().getReasonPhrase();

        String body = null;
        try {
            if (response.hasEntity()) {
                body = response.readEntity(String.class);
            }
        } catch (final Exception e) {
            // Never let a failure to read the error body mask the error itself.
            LOGGER.debug(() -> "Unable to read error response body: " + e.getMessage(), e);
        }

        if (NullSafe.isBlankString(body)) {
            return status + " " + reasonPhrase;
        }
        return status + " " + reasonPhrase + " - " + body.trim();
    }
}
