package stroom.receive.common;

import com.caucho.hessian.server.HessianServlet;
import stroom.proxy.feed.remote.GetFeedStatusRequest;
import stroom.proxy.feed.remote.GetFeedStatusResponse;
import stroom.util.shared.IsServlet;
import stroom.util.shared.Unauthenticated;

import javax.inject.Inject;
import java.util.Set;

@Unauthenticated
public class RemoteFeedServiceRPC extends HessianServlet implements IsServlet {
    private static final Set<String> PATH_SPECS = Set.of("/remoting/remotefeedservice.rpc");

    private final FeedStatusService feedStatusService;

    @Inject
    RemoteFeedServiceRPC(final FeedStatusService feedStatusService) {
        this.feedStatusService = feedStatusService;
    }

    public GetFeedStatusResponse getFeedStatus(final GetFeedStatusRequest request) {
        return feedStatusService.getFeedStatus(request);
    }

    /**
     * @return The part of the path that will be in addition to any base path,
     * e.g. "/datafeed".
     */
    @Override
    public Set<String> getPathSpecs() {
        return PATH_SPECS;
    }
}
