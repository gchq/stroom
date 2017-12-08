package stroom.proxy.handler.remoteclient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.feed.server.GetFeedStatusRequest;
import stroom.feed.server.GetFeedStatusResponse;
import stroom.feed.server.RemoteFeedService;
import stroom.proxy.handler.LocalFeedService;

import javax.annotation.Resource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RemoteFeedServiceCacheable implements LocalFeedService {
    private static Logger LOGGER = LoggerFactory.getLogger(RemoteFeedServiceCacheable.class);

    @Resource
    private RemoteFeedService remoteFeedService;

    private Map<GetFeedStatusRequest, GetFeedStatusResponse> lastKnownRepsonse = new ConcurrentHashMap<GetFeedStatusRequest, GetFeedStatusResponse>();

    public GetFeedStatusResponse getFeedStatus(GetFeedStatusRequest request) {
        GetFeedStatusResponse response = null;
        try {
            response = remoteFeedService.getFeedStatus(request);
            lastKnownRepsonse.put(request, response);
        } catch (Exception ex) {
            LOGGER.debug("handleHeader() - Unable to check remote feed service", ex);
            response = lastKnownRepsonse.get(request);
            if (response != null) {
                LOGGER.error(
                        "handleHeader() - Unable to check remote feed service (%s).... will use last response (%s) - %s",
                        request, response, ex.getMessage());
            } else {
                response = new GetFeedStatusResponse();
                LOGGER.error("handleHeader() - Unable to check remote feed service (%s).... will assume OK (%s) - %s",
                        request, response, ex.getMessage());
            }
        }

        LOGGER.debug("getFeedStatus() " + request + " -> " + response);
        return response;
    }
}
