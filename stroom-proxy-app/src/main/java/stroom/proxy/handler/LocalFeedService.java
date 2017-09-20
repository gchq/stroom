package stroom.proxy.handler;

import stroom.feed.server.GetFeedStatusRequest;
import stroom.feed.server.GetFeedStatusResponse;

public interface LocalFeedService {
    GetFeedStatusResponse getFeedStatus(GetFeedStatusRequest request);
}
