package stroom.feed.server;

import com.caucho.hessian.server.HessianServlet;
import org.springframework.stereotype.Component;
//import org.springframework.remoting.caucho.HessianServiceExporter;
//import org.springframework.stereotype.Component;

import javax.inject.Inject;
import javax.inject.Named;

@Component//(RemoteFeedServiceRPC.BEAN_NAME)
public class RemoteFeedServiceRPC extends HessianServlet implements RemoteFeedService {
//    public static final String BEAN_NAME = "remoteFeedServiceRPC";

    private final RemoteFeedService remoteFeedService;

    @Inject
    RemoteFeedServiceRPC(@Named("remoteFeedService") final RemoteFeedService remoteFeedService) {
        this.remoteFeedService = remoteFeedService;
//        setService(remoteFeedService);
//        setServiceInterface(RemoteFeedService.class);
    }

    @Override
    public GetFeedStatusResponse getFeedStatus(final GetFeedStatusRequest request) {
        return remoteFeedService.getFeedStatus(request);
    }
}
