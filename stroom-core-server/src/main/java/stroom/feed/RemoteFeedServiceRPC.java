package stroom.feed;

import com.caucho.hessian.server.HessianServlet;

import javax.inject.Inject;
import javax.inject.Named;

public class RemoteFeedServiceRPC extends HessianServlet implements RemoteFeedService {
    private final RemoteFeedService remoteFeedService;

    @Inject
    RemoteFeedServiceRPC(@Named("remoteFeedService") final RemoteFeedService remoteFeedService) {
        this.remoteFeedService = remoteFeedService;
    }

    @Override
    public GetFeedStatusResponse getFeedStatus(final GetFeedStatusRequest request) {
        return remoteFeedService.getFeedStatus(request);
    }
}
