package stroom.proxy.status.server;

import stroom.node.server.GetStatusRequest;
import stroom.node.server.GetStatusResponse;
import stroom.node.server.GetStatusResponse.Status;
import stroom.node.server.GetStatusResponse.StatusEntry;
import stroom.node.server.RemoteStatusService;

public class RemoteStatusServiceImpl implements RemoteStatusService {
    @Override
    public GetStatusResponse getStatus(GetStatusRequest request) {
        GetStatusResponse response = new GetStatusResponse();
        response.getStatusEntryList().add(new StatusEntry(Status.Info, "SYSTEM", "All Ok"));
        return response;
    }

}
