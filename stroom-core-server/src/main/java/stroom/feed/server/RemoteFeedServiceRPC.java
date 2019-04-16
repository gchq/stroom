package stroom.feed.server;

import com.caucho.hessian.server.HessianServlet;
import org.springframework.stereotype.Component;
import stroom.proxy.feed.remote.GetFeedStatusRequest;
import stroom.proxy.feed.remote.GetFeedStatusResponse;

import javax.inject.Inject;

@Component
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
