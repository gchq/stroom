package stroom.search.impl;

import stroom.node.api.NodeCallUtil;
import stroom.node.api.NodeInfo;
import stroom.node.api.NodeService;
import stroom.query.api.v2.Query;
import stroom.task.api.TaskContext;
import stroom.util.jersey.UriBuilderUtil;
import stroom.util.jersey.WebTargetFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ResourcePaths;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import javax.inject.Inject;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

public class RemoteNodeSearch implements NodeSearch {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(RemoteNodeSearch.class);

    private final NodeService nodeService;
    private final NodeInfo nodeInfo;
    private final WebTargetFactory webTargetFactory;

    @Inject
    public RemoteNodeSearch(final NodeService nodeService,
                            final NodeInfo nodeInfo,
                            final WebTargetFactory webTargetFactory) {
        this.nodeService = nodeService;
        this.nodeInfo = nodeInfo;
        this.webTargetFactory = webTargetFactory;
    }

    public void searchNode(final String sourceNode,
                           final String targetNode,
                           final List<Long> shards,
                           final AsyncSearchTask task,
                           final Query query,
                           final TaskContext taskContext) {
        LOGGER.debug(() -> task.getSearchName() + " - start searching node: " + targetNode);
        taskContext.info(() -> task.getSearchName() + " - start searching node: " + targetNode);
        final String queryKey = task.getKey().getUuid();
        final ClusterSearchResultCollector resultCollector = task.getResultCollector();

        // Start remote cluster search execution.
        final ClusterSearchTask clusterSearchTask = new ClusterSearchTask(
                taskContext.getTaskId(),
                "Cluster Search",
                task.getKey(),
                query,
                shards,
                task.getSettings(),
                task.getDateTimeSettings(),
                task.getNow());
        LOGGER.debug(() -> "Dispatching clusterSearchTask to node: " + targetNode);
        try {
            final boolean success = startRemoteSearch(targetNode, clusterSearchTask);
            if (!success) {
                LOGGER.debug(() -> "Failed to start remote search on node: " + targetNode);
                final SearchException searchException = new SearchException(
                        "Failed to start remote search on node: " + targetNode);
                resultCollector.onFailure(targetNode, searchException);
                throw searchException;
            }
        } catch (final Throwable e) {
            LOGGER.debug(e::getMessage, e);
            final SearchException searchException = new SearchException(e.getMessage(), e);
            resultCollector.onFailure(targetNode, searchException);
            throw searchException;
        }

        try {
            LOGGER.debug(() -> task.getSearchName() + " - searching node: " + targetNode + "...");
            taskContext.info(() -> task.getSearchName() + " - searching node: " + targetNode + "...");

            // Poll for results until completion.
            boolean complete = false;
            while (!Thread.currentThread().isInterrupted() && !complete) {
                complete = pollRemoteSearch(targetNode, queryKey, resultCollector);
            }

        } catch (final Throwable e) {
            LOGGER.debug(e::getMessage, e);
            resultCollector.onFailure(sourceNode, e);

        } finally {
            LOGGER.debug(() -> task.getSearchName() + " - finished searching node: " + targetNode);
            taskContext.info(() -> task.getSearchName() + " - finished searching node: " + targetNode);

            // Destroy search results.
            try {
                final boolean success = destroyRemoteSearch(targetNode, queryKey);
                if (!success) {
                    LOGGER.debug(() -> "Failed to destroy remote search on node: " + targetNode);
                    resultCollector.onFailure(targetNode, new SearchException("Failed to destroy remote search"));
                }
            } catch (final Throwable e) {
                resultCollector.onFailure(targetNode, e);
            }
        }
    }

    private Boolean startRemoteSearch(final String nodeName, final ClusterSearchTask clusterSearchTask) {
        final String url = NodeCallUtil.getBaseEndpointUrl(nodeInfo, nodeService, nodeName)
                + ResourcePaths.buildAuthenticatedApiPath(
                RemoteSearchResource.BASE_PATH,
                RemoteSearchResource.START_PATH_PART);

        try {
            final Response response = webTargetFactory
                    .create(url)
                    .request(MediaType.APPLICATION_JSON)
                    .post(Entity.json(clusterSearchTask));
            if (response.getStatus() == Status.NOT_FOUND.getStatusCode()) {
                throw new NotFoundException(response);
            } else if (response.getStatus() != Status.OK.getStatusCode()) {
                throw new WebApplicationException(response);
            }

            return response.readEntity(Boolean.class);
        } catch (Throwable e) {
            LOGGER.debug(e::getMessage, e);
            throw NodeCallUtil.handleExceptionsOnNodeCall(nodeName, url, e);
        }
    }

    private Boolean pollRemoteSearch(final String nodeName,
                                     final String queryKey,
                                     final ClusterSearchResultCollector resultCollector) throws IOException {
        boolean complete;
        final String url = NodeCallUtil.getBaseEndpointUrl(nodeInfo, nodeService, nodeName)
                + ResourcePaths.buildAuthenticatedApiPath(
                RemoteSearchResource.BASE_PATH,
                RemoteSearchResource.POLL_PATH_PART);

        WebTarget webTarget = webTargetFactory.create(url);
        webTarget = UriBuilderUtil.addParam(webTarget, "queryKey", queryKey);

        try (final InputStream inputStream = webTarget
                .request(MediaType.APPLICATION_OCTET_STREAM)
                .get(InputStream.class)) {

            LOGGER.debug(() -> "Receive result for node: " + nodeName);
            complete = resultCollector.onSuccess(nodeName, inputStream);
        }
        return complete;
    }

    private Boolean destroyRemoteSearch(final String nodeName,
                                        final String queryKey) {
        final String url = NodeCallUtil.getBaseEndpointUrl(nodeInfo, nodeService, nodeName)
                + ResourcePaths.buildAuthenticatedApiPath(
                RemoteSearchResource.BASE_PATH,
                RemoteSearchResource.DESTROY_PATH_PART);

        try {
            WebTarget webTarget = webTargetFactory.create(url);
            webTarget = UriBuilderUtil.addParam(webTarget, "queryKey", queryKey);

            final Response response = webTarget
                    .request(MediaType.APPLICATION_JSON)
                    .get();
            if (response.getStatus() == Status.NOT_FOUND.getStatusCode()) {
                throw new NotFoundException(response);
            } else if (response.getStatus() != Status.OK.getStatusCode()) {
                throw new WebApplicationException(response);
            }

            return response.readEntity(Boolean.class);
        } catch (Throwable e) {
            LOGGER.debug(e::getMessage, e);
            throw NodeCallUtil.handleExceptionsOnNodeCall(nodeName, url, e);
        }
    }
}
