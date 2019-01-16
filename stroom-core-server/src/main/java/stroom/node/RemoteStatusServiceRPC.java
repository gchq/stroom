package stroom.node;

import com.caucho.hessian.server.HessianServlet;

import javax.inject.Inject;

class RemoteStatusServiceRPC extends HessianServlet implements RemoteStatusService {
    private RemoteStatusService remoteStatusService;

    @Inject
    RemoteStatusServiceRPC(final RemoteStatusService remoteStatusService) {
        this.remoteStatusService = remoteStatusService;
    }

    @Override
    public GetStatusResponse getStatus(final GetStatusRequest request) {
        return remoteStatusService.getStatus(request);
    }
}
