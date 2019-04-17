package stroom.node.impl;

import com.caucho.hessian.server.HessianServlet;
import stroom.proxy.status.remote.GetStatusRequest;
import stroom.proxy.status.remote.GetStatusResponse;

import javax.inject.Inject;

class RemoteStatusServiceRPC extends HessianServlet {
    private RemoteStatusService remoteStatusService;

    @Inject
    RemoteStatusServiceRPC(final RemoteStatusService remoteStatusService) {
        this.remoteStatusService = remoteStatusService;
    }

    public GetStatusResponse getStatus(final GetStatusRequest request) {
        return remoteStatusService.getStatus(request);
    }
}
