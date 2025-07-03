package stroom.planb.impl.data;

import stroom.cluster.task.api.TargetNodeSetFactory;
import stroom.node.api.NodeCallUtil;
import stroom.node.api.NodeInfo;
import stroom.node.api.NodeService;
import stroom.planb.impl.PlanBConfig;
import stroom.security.api.SecurityContext;
import stroom.util.jersey.WebTargetFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    @Inject
    public FileTransferClientImpl(final Provider<PlanBConfig> configProvider,
                                  final NodeService nodeService,
                                  @Nullable final NodeInfo nodeInfo,
                                  @Nullable final TargetNodeSetFactory targetNodeSetFactory,
                                  final WebTargetFactory webTargetFactory,
                                  final PartDestination partDestination,
                                  final SecurityContext securityContext) {
        this.configProvider = configProvider;
        this.nodeService = nodeService;
        this.nodeInfo = nodeInfo;
        this.targetNodeSetFactory = targetNodeSetFactory;
        this.webTargetFactory = webTargetFactory;
        this.partDestination = partDestination;
        this.securityContext = securityContext;
    }

    @Override
    public void storePart(final FileDescriptor fileDescriptor,
                          final Path path,
                          final boolean synchroniseMerge) {
        securityContext.asProcessingUser(() -> {
            try {
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
                            final Set<String> enabledActiveNodes = targetNodeSetFactory.getEnabledActiveTargetNodeSet();
                            for (final String node : configuredNodes) {
                                if (enabledActiveNodes.contains(node)) {
                                    targetNodes.add(node);
                                } else {
                                    throw new RuntimeException("Plan B target node '" +
                                                               node +
                                                               "' is not enabled or active");
                                }
                            }
                        }
                    } catch (final Exception e) {
                        LOGGER.error(e::getMessage, e);
                        throw new RuntimeException(e.getMessage(), e);
                    }
                }

                // Send the data to all nodes.
                for (final String nodeName : targetNodes) {
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
                }
            } catch (final IOException e) {
                LOGGER.error(e::getMessage, e);
                throw new UncheckedIOException(e);
            }
        });
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
            try {
                LOGGER.info(() -> "Fetching snapshot from '" +
                                  nodeName +
                                  "' for '" +
                                  request.getPlanBDocRef() +
                                  "'");
                final String url = NodeCallUtil.getBaseEndpointUrl(nodeInfo, nodeService, nodeName)
                                   + ResourcePaths.buildAuthenticatedApiPath(
                        FileTransferResource.BASE_PATH,
                        FileTransferResource.FETCH_SNAPSHOT_PATH_PART);
                final WebTarget webTarget = webTargetFactory.create(url);
                return fetchSnapshot(webTarget, request, snapshotDir);
            } catch (final Exception e) {
                throw new RuntimeException("Error fetching snapshot from '" +
                                           nodeName +
                                           "' for '" +
                                           request.getPlanBDocRef() +
                                           "'", e);
            }
        });
    }

    Instant fetchSnapshot(final WebTarget webTarget,
                          final SnapshotRequest request,
                          final Path snapshotDir) throws IOException {
        try (final Response response = webTarget
                .request(MediaType.APPLICATION_OCTET_STREAM)
                .post(Entity.json(request))) {
            if (response.getStatus() == Status.NOT_MODIFIED.getStatusCode()) {
                throw new NotModifiedException(response.getStatusInfo().getReasonPhrase());
            } else if (response.getStatus() == Status.UNAUTHORIZED.getStatusCode()) {
                throw new PermissionException(null, response.getStatusInfo().getReasonPhrase());
            } else if (response.getStatus() != Status.OK.getStatusCode()) {
                throw new RuntimeException(response.getStatusInfo().getReasonPhrase());
            }

            try (final InputStream stream = (InputStream) response.getEntity()) {
                ZipUtil.unzip(stream, snapshotDir);
            }
            final String info = Files.readString(snapshotDir.resolve(Shard.SNAPSHOT_INFO_FILE_NAME));
            return Instant.parse(info);
        }
    }
}
