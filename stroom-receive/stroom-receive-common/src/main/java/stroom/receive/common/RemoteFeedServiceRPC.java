package stroom.receive.common;

import stroom.proxy.feed.remote.GetFeedStatusRequest;
import stroom.proxy.feed.remote.GetFeedStatusResponse;
import stroom.util.shared.IsServlet;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.Unauthenticated;

import java.util.Set;
import jakarta.inject.Inject;

@Unauthenticated
// TODO: 31/10/2023 jakarta - Hessian uses javax.servlet so we can't use it (maybe unless we shade it)
//public class RemoteFeedServiceRPC extends HessianServlet implements IsServlet {
public class RemoteFeedServiceRPC implements IsServlet {

    private static final Set<String> PATH_SPECS = Set.of(
            ResourcePaths.addUnauthenticatedPrefix("/remoting/remotefeedservice.rpc"));

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
