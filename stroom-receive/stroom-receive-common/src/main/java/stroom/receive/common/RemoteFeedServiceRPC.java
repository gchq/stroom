package stroom.receive.common;

import com.caucho.hessian.server.HessianServlet;
import stroom.proxy.feed.remote.GetFeedStatusRequest;
import stroom.proxy.feed.remote.GetFeedStatusResponse;

import javax.inject.Inject;

public class RemoteFeedServiceRPC extends HessianServlet {
    private final FeedStatusService feedStatusService;

    @Inject
    RemoteFeedServiceRPC(final FeedStatusService feedStatusService) {
        this.feedStatusService = feedStatusService;
    }

    public GetFeedStatusResponse getFeedStatus(final GetFeedStatusRequest request) {
        return feedStatusService.getFeedStatus(request);
    }
}
