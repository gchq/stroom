package stroom.planb.impl.data;

import stroom.cluster.task.api.TargetNodeSetFactory;
import stroom.node.api.NodeCallUtil;
import stroom.node.api.NodeInfo;
import stroom.node.api.NodeService;
import stroom.planb.impl.PlanBConfig;
import stroom.util.jersey.WebTargetFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ResourcePaths;
import stroom.util.zip.ZipUtil;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
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
    private final SequentialFileStore fileStore;

    @Inject
    public FileTransferClientImpl(final Provider<PlanBConfig> configProvider,
                                  final NodeService nodeService,
                                  final NodeInfo nodeInfo,
                                  final TargetNodeSetFactory targetNodeSetFactory,
                                  final WebTargetFactory webTargetFactory,
                                  final SequentialFileStore fileStore) {
        this.configProvider = configProvider;
        this.nodeService = nodeService;
        this.nodeInfo = nodeInfo;
        this.targetNodeSetFactory = targetNodeSetFactory;
        this.webTargetFactory = webTargetFactory;
        this.fileStore = fileStore;
    }

    @Override
    public void storePart(final FileDescriptor fileDescriptor,
                          final Path path) throws IOException {
        final Set<String> targetNodes = new HashSet<>();

        // Now post to all nodes.
        final PlanBConfig planBConfig = configProvider.get();
        final List<String> configuredNodes = planBConfig.getNodeList();
        if (configuredNodes == null || configuredNodes.isEmpty()) {
            LOGGER.warn("No node list configured for PlanB, assuming this is a single node test setup");
            targetNodes.add(nodeInfo.getThisNodeName());

        } else {
            try {
                final Set<String> enabledActiveNodes = targetNodeSetFactory.getEnabledActiveTargetNodeSet();
                for (final String node : configuredNodes) {
                    if (enabledActiveNodes.contains(node)) {
                        targetNodes.add(node);
                    } else {
                        throw new RuntimeException("PlanB target node '" + node + "' is not enabled or active");
                    }
                }
            } catch (final Exception e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }

        // Send the data to all nodes.
        for (final String nodeName : targetNodes) {
            if (NodeCallUtil.shouldExecuteLocally(nodeInfo, nodeName)) {
                storePartLocally(
                        fileDescriptor,
                        path);
            } else {
                storePartRemotely(
                        nodeInfo.getThisNodeName(),
                        nodeName,
                        fileDescriptor,
                        path);
            }
        }
    }

    private void storePartLocally(final FileDescriptor fileDescriptor,
                                  final Path path) throws IOException {
        fileStore.add(fileDescriptor, path);
    }

    private void storePartRemotely(final String sourceNode,
                                   final String targetNode,
                                   final FileDescriptor fileDescriptor,
                                   final Path path) throws IOException {
        final String baseEndpointUrl = NodeCallUtil.getBaseEndpointUrl(nodeInfo, nodeService, targetNode);
        final String url = baseEndpointUrl + ResourcePaths.buildServletPath(FileTransferResource.BASE_PATH,
                FileTransferResource.SEND_PART_PATH_PART);
        final WebTarget webTarget = webTargetFactory.create(url);
        try (final Response response = storePartRemotely(webTarget, fileDescriptor, path)) {
            if (response.getStatus() != Status.OK.getStatusCode()) {
                throw new IOException("Unable to send file to: " + sourceNode);
            }
        }
    }

    Response storePartRemotely(final WebTarget webTarget,
                               final FileDescriptor fileDescriptor,
                               final Path path) throws IOException {
        try (final InputStream inputStream = new BufferedInputStream(Files.newInputStream(path))) {
            return webTarget
                    .request()
                    .header("createTime", fileDescriptor.createTimeMs())
                    .header("metaId", fileDescriptor.metaId())
                    .header("fileHash", fileDescriptor.fileHash())
                    .header("fileName", path.getFileName().toString())
                    .post(Entity.entity(inputStream, MediaType.APPLICATION_OCTET_STREAM));
        }
    }

    @Override
    public void fetchSnapshot(final String nodeName,
                              final SnapshotRequest request,
                              final Path snapshotDir) throws IOException {
        LOGGER.info(() -> "Fetching snapshot from '" + nodeName + "' for '" + request.getMapName() + "'");
        final String url = NodeCallUtil.getBaseEndpointUrl(nodeInfo, nodeService, nodeName)
                           + ResourcePaths.buildAuthenticatedApiPath(
                FileTransferResource.BASE_PATH,
                FileTransferResource.FETCH_SNAPSHOT_PATH_PART);
        final WebTarget webTarget = webTargetFactory.create(url);
        fetchSnapshot(webTarget, request, snapshotDir);
    }

    void fetchSnapshot(final WebTarget webTarget,
                       final SnapshotRequest request,
                       final Path snapshotDir) throws IOException {
        try (Response response = webTarget
                .request(MediaType.APPLICATION_OCTET_STREAM)
                .post(Entity.json(request))) {
            if (response.getStatus() != 200) {
                throw new WebApplicationException(response);
            }
            try (final InputStream stream = (InputStream) response.getEntity()) {
                ZipUtil.unzip(stream, snapshotDir);
            }
        }
    }
}
