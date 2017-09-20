package stroom.proxy.handler.remoteclient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.feed.MetaMap;
import stroom.feed.StroomHeaderArguments;
import stroom.feed.StroomStatusCode;
import stroom.feed.StroomStreamException;
import stroom.feed.server.FeedStatus;
import stroom.feed.server.GetFeedStatusRequest;
import stroom.feed.server.GetFeedStatusResponse;
import stroom.proxy.handler.DropStreamException;
import stroom.proxy.handler.LocalFeedService;
import stroom.proxy.handler.RequestHandler;
import stroom.proxy.repo.StroomZipEntry;

import javax.annotation.Resource;
import java.io.IOException;

public class RemoteClientValidatingRequestHandler implements RequestHandler {
    private static Logger LOGGER = LoggerFactory.getLogger(RemoteClientValidatingRequestHandler.class);

    @Resource
    LocalFeedService localFeedService;

    @Resource
    MetaMap metaMap;

    @Override
    public void handleHeader() throws IOException {
        String feedName = metaMap.get(StroomHeaderArguments.FEED);
        if (feedName == null) {
            throw new StroomStreamException(StroomStatusCode.FEED_MUST_BE_SPECIFIED);
        }

        String senderDn = metaMap.get(StroomHeaderArguments.REMOTE_DN);

        GetFeedStatusRequest request = new GetFeedStatusRequest(feedName, senderDn);
        GetFeedStatusResponse response = new GetFeedStatusResponse();

        try {
            response = localFeedService.getFeedStatus(request);
        } catch (Exception ex) {
            LOGGER.error("handleHeader() - Unable to check cache for feed service.... will assume the post is OK - %s",
                    ex.getMessage());
            LOGGER.debug("handleHeader() - Unable to check cache for feed service.... will assume the post is OK", ex);
        }

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("handleHeader() - " + request + " -> " + response);
        }

        if (response.getStatus() == FeedStatus.Reject) {
            throw new StroomStreamException(response.getStroomStatusCode());
        }
        if (response.getStatus() == FeedStatus.Drop) {
            throw new DropStreamException();
        }
    }

    @Override
    public void handleEntryStart(StroomZipEntry stroomZipEntry) throws IOException {
        // NA for RemoteClientValidatingRequestHandler
    }

    @Override
    public void handleEntryEnd() throws IOException {
        // NA for RemoteClientValidatingRequestHandler
    }

    @Override
    public void handleEntryData(byte[] buffer, int off, int len) throws IOException {
        // NA for RemoteClientValidatingRequestHandler
    }

    @Override
    public void handleError() throws IOException {
        // NA for RemoteClientValidatingRequestHandler
    }

    @Override
    public void handleFooter() throws IOException {
        // NA for RemoteClientValidatingRequestHandler
    }

    @Override
    public void validate() {
    }

}
