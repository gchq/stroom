package stroom.index.impl;

import stroom.entity.shared.ExpressionCriteria;
import stroom.index.shared.CreateVolumeRequest;
import stroom.index.shared.IndexVolume;
import stroom.index.shared.IndexVolumeResource;
import stroom.index.shared.IndexVolumeResultPage;
import stroom.node.api.NodeCallUtil;
import stroom.node.api.NodeInfo;
import stroom.node.api.NodeService;
import stroom.util.jersey.WebTargetFactory;
import stroom.util.rest.RestUtil;
import stroom.util.shared.ResourcePaths;

import javax.inject.Inject;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

class IndexVolumeResourceImpl implements IndexVolumeResource {
    private final IndexVolumeService indexVolumeService;
    private final NodeService nodeService;
    private final NodeInfo nodeInfo;
    private final WebTargetFactory webTargetFactory;

    @Inject
    public IndexVolumeResourceImpl(final IndexVolumeService indexVolumeService,
                                   final NodeService nodeService,
                                   final NodeInfo nodeInfo,
                                   final WebTargetFactory webTargetFactory) {
        this.indexVolumeService = indexVolumeService;
        this.nodeService = nodeService;
        this.nodeInfo = nodeInfo;
        this.webTargetFactory = webTargetFactory;
    }

    @Override
    public IndexVolumeResultPage find(final ExpressionCriteria request) {
        return new IndexVolumeResultPage(indexVolumeService.getAll());
    }

    @Override
    public IndexVolume create(final CreateVolumeRequest request) {
        return indexVolumeService.create(request);
    }

    @Override
    public IndexVolume read(final Integer id) {
        return indexVolumeService.getById(id);
    }

    @Override
    public IndexVolume update(final Integer id, final IndexVolume indexVolume) {
        return indexVolumeService.update(indexVolume);
    }

    @Override
    public Boolean delete(final Integer id) {
        return indexVolumeService.delete(id);
    }

    @Override
    public Boolean rescan(final String nodeName) {
        RestUtil.requireNonNull(nodeName, "nodeName not supplied");

        // If this is the node that was contacted then just resolve it locally
        if (NodeCallUtil.shouldExecuteLocally(nodeInfo, nodeName)) {
            indexVolumeService.rescan();
        } else {
            final String url = NodeCallUtil.getBaseEndpointUrl(nodeService, nodeName)
                    + ResourcePaths.buildAuthenticatedApiPath(
                    IndexVolumeResource.BASE_PATH,
                    IndexVolumeResource.RESCAN_SUB_PATH);
            try {
                // A different node to make a rest call to the required node
                final Response response = webTargetFactory
                        .create(url)
                        .queryParam("nodeName", nodeName)
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
