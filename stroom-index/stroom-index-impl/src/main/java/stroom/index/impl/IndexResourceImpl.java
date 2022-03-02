package stroom.index.impl;

import stroom.docref.DocRef;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.index.impl.IndexShardManager.IndexShardAction;
import stroom.index.shared.FindIndexShardCriteria;
import stroom.index.shared.IndexDoc;
import stroom.index.shared.IndexResource;
import stroom.index.shared.IndexShard;
import stroom.node.api.NodeCallUtil;
import stroom.node.api.NodeInfo;
import stroom.node.api.NodeService;
import stroom.util.jersey.UriBuilderUtil;
import stroom.util.jersey.WebTargetFactory;
import stroom.util.rest.RestUtil;
import stroom.util.shared.EntityServiceException;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.ResultPage;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

@AutoLogged
class IndexResourceImpl implements IndexResource {

    private final Provider<IndexStore> indexStoreProvider;
    private final Provider<IndexShardService> indexShardServiceProvider;
    private final Provider<IndexShardManager> indexShardManagerProvider;
    private final Provider<NodeService> nodeServiceProvider;
    private final Provider<NodeInfo> nodeInfoProvider;
    private final Provider<WebTargetFactory> webTargetFactoryProvider;

    @Inject
    IndexResourceImpl(final Provider<IndexStore> indexStoreProvider,
                      final Provider<IndexShardService> indexShardServiceProvider,
                      final Provider<IndexShardManager> indexShardManagerProvider,
                      final Provider<NodeService> nodeServiceProvider,
                      final Provider<NodeInfo> nodeInfoProvider,
                      final Provider<WebTargetFactory> webTargetFactoryProvider) {
        this.indexStoreProvider = indexStoreProvider;
        this.indexShardServiceProvider = indexShardServiceProvider;
        this.indexShardManagerProvider = indexShardManagerProvider;
        this.nodeServiceProvider = nodeServiceProvider;
        this.nodeInfoProvider = nodeInfoProvider;
        this.webTargetFactoryProvider = webTargetFactoryProvider;
    }

    @Override
    public IndexDoc fetch(final String uuid) {
        return indexStoreProvider.get().readDocument(getDocRef(uuid));
    }

    @Override
    public IndexDoc update(final String uuid, final IndexDoc doc) {
        if (doc.getUuid() == null || !doc.getUuid().equals(uuid)) {
            throw new EntityServiceException("The document UUID must match the update UUID");
        }
        return indexStoreProvider.get().writeDocument(doc);
    }

    private DocRef getDocRef(final String uuid) {
        return DocRef.builder()
                .uuid(uuid)
                .type(IndexDoc.DOCUMENT_TYPE)
                .build();
    }

    @Override
    public ResultPage<IndexShard> find(final FindIndexShardCriteria criteria) {
        return indexShardServiceProvider.get().find(criteria);
    }

    @Override
    public Long deleteIndexShards(final String nodeName, final FindIndexShardCriteria criteria) {
        return performShardAction(nodeName, criteria, IndexResource.SHARD_DELETE_SUB_PATH, IndexShardAction.DELETE);
    }

    @Override
    @AutoLogged(value = OperationType.PROCESS, verb = "Flushing index shards to disk")
    public Long flushIndexShards(final String nodeName, final FindIndexShardCriteria criteria) {
        return performShardAction(nodeName, criteria, IndexResource.SHARD_FLUSH_SUB_PATH, IndexShardAction.FLUSH);
    }

    private Long performShardAction(final String nodeName,
                                    final FindIndexShardCriteria criteria,
                                    final String subPath,
                                    final IndexShardAction action) {
        RestUtil.requireNonNull(nodeName, "nodeName not supplied");

        // If this is the node that was contacted then just resolve it locally
        if (NodeCallUtil.shouldExecuteLocally(nodeInfoProvider.get(), nodeName)) {
            return indexShardManagerProvider.get().performAction(criteria, action);
        } else {
            final String url = NodeCallUtil
                    .getBaseEndpointUrl(nodeInfoProvider.get(), nodeServiceProvider.get(), nodeName)
                    + ResourcePaths.buildAuthenticatedApiPath(IndexResource.BASE_PATH, subPath);
            try {
                // A different node to make a rest call to the required node
                WebTarget webTarget = webTargetFactoryProvider.get().create(url);
                webTarget = UriBuilderUtil.addParam(webTarget, "nodeName", nodeName);
                final Response response = webTarget
                        .request(MediaType.APPLICATION_JSON)
                        .post(Entity.json(criteria));
                if (response.getStatus() == Status.NOT_FOUND.getStatusCode()) {
                    throw new NotFoundException(response);
                } else if (response.getStatus() != Status.OK.getStatusCode()) {
                    throw new WebApplicationException(response);
                }

                return response.readEntity(Long.class);
            } catch (final Throwable e) {
                throw NodeCallUtil.handleExceptionsOnNodeCall(nodeName, url, e);
            }
        }
    }
}
