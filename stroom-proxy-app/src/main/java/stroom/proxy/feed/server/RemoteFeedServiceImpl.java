package stroom.proxy.feed.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.feed.server.GetFeedStatusRequest;
import stroom.feed.server.GetFeedStatusResponse;
import stroom.feed.server.RemoteFeedService;
import stroom.proxy.datafeed.ProxyHandlerFactory;
import stroom.util.logging.LogExecutionTime;

import javax.annotation.Resource;

public class RemoteFeedServiceImpl implements RemoteFeedService {
    private static Logger LOGGER = LoggerFactory.getLogger(RemoteFeedServiceImpl.class);

    @Resource
    ProxyHandlerFactory proxyHandlerFactory;

    @Override
    public GetFeedStatusResponse getFeedStatus(GetFeedStatusRequest request) {
        LogExecutionTime logExecutionTime = new LogExecutionTime();
        GetFeedStatusResponse response = proxyHandlerFactory.getLocalFeedService().getFeedStatus(request);
        LOGGER.debug("getFeedStatus() - %s -> %s in %s", request, response, logExecutionTime);
        return response;
    }

}
