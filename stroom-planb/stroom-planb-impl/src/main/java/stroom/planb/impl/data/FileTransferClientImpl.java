/*
 * Copyright 2025 Crown Copyright
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
import stroom.util.concurrent.ThreadUtil;
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
import jakarta.ws.rs.ProcessingException;
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
import java.util.function.Predicate;

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
                    // Debug only as we rethrow, so the caller reports the failure, e.g. to the pipeline error
                    // receiver, which records it in the stream processing error file. See gh-5706.
                    LOGGER.debug(e::getMessage, e);
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
                                // Debug only as the exception is collected and rethrown to the caller, which
                                // reports it. See gh-5706.
                                LOGGER.debug(e::getMessage, e);
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

        final PlanBConfig planBConfig = configProvider.get();
        // Not always 1 despite the @Min(1) on the property. A planb config block that omits the property leaves
        // Jackson with nothing to pass to the creator for a primitive, giving 0, and validation of an explicit 0
        // only halts boot if haltBootOnConfigValidationFailure is set. The loop always makes one attempt anyway,
        // so this just keeps the count reported below honest.
        final int maxAttempts = Math.max(1, planBConfig.getSendPartAttempts());
        for (int attempt = 1; ; attempt++) {
            try {
                storePartRemotely(webTarget, fileDescriptor, path, synchroniseMerge);
                return;

            } catch (final Exception e) {
                // Debug only as we eventually rethrow, so the caller reports the failure. A network blip, e.g. a
                // DNS lookup failure, is expected in normal operation and fails the processing task, which puts
                // the reason in the stream processing error file. Logging it here as well just duplicates it.
                // See gh-5706.
                final int attemptNumber = attempt;
                LOGGER.debug(() -> "Attempt " +
                                   attemptNumber +
                                   " of " +
                                   maxAttempts +
                                   " to send file to '" +
                                   targetNode +
                                   "' failed: " +
                                   e.getMessage(), e);

                if (attempt >= maxAttempts || !isWorthRetrying(e)) {
                    throw new IOException("Unable to send file to '" +
                                          targetNode +
                                          "' after " +
                                          attempt +
                                          " attempt(s): " +
                                          e.getMessage(), e);
                }

                // Interruption, i.e. task termination, aborts the retries by throwing.
                ThreadUtil.sleep(planBConfig.getSendPartRetryDelay());
            }
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
            } catch (final NotModifiedException e) {
                // Not a failure, but the node's confirmation that the snapshot the caller already holds is
                // current. Rethrow as is, as wrapping it would hide the type from the caller, which would then
                // treat this as a fetch failure and eventually fail reads for a store that simply hasn't
                // changed. See gh-5705.
                LOGGER.debug(() -> "Snapshot not modified on '" +
                                   nodeName +
                                   "' for '" +
                                   request.getPlanBDocRef() +
                                   "'");
                throw e;
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
        return hasCauseMatching(throwable, t -> t instanceof ConnectException
                                                || t instanceof UnknownHostException
                                                || t instanceof NoRouteToHostException
                                                || t instanceof SocketTimeoutException);
    }

    /**
     * Is this failure worth sending the part again for? Only transport level failures are, i.e. those where the
     * node gave no answer, such as the DNS lookup failing during a network blip. Jersey reports those as a
     * {@link ProcessingException}, so anything else is either an answer from the node, which sending again will
     * not change, or a local failure such as being unable to read the part.
     * <p>
     * Sending again is safe even where the node may have received the part already, e.g. the request completed
     * but the response was lost, because merging a part twice does not double count. The additive stores
     * (histogram and metric) skip a source they have already merged, keyed by the instance UUID the part carries
     * with it, and all other stores merge by put, so are naturally idempotent. See gh-5706.
     */
    private static boolean isWorthRetrying(final Throwable throwable) {
        return hasCauseMatching(throwable, t -> t instanceof ProcessingException);
    }

    private static boolean hasCauseMatching(final Throwable throwable,
                                            final Predicate<Throwable> predicate) {
        Throwable current = throwable;
        while (current != null) {
            if (predicate.test(current)) {
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
