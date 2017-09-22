package stroom.feed.server;

import org.springframework.remoting.caucho.HessianServiceExporter;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import javax.inject.Named;

@Component(RemoteFeedServiceRPC.BEAN_NAME)
public class RemoteFeedServiceRPC extends HessianServiceExporter {
    public static final String BEAN_NAME = "remoteFeedServiceRPC";

    @Inject
    RemoteFeedServiceRPC(@Named("remoteFeedService") final RemoteFeedService remoteFeedService) {
        setService(remoteFeedService);
        setServiceInterface(RemoteFeedService.class);
    }
}
