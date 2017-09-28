
package stroom.proxy.handler.db;

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

public class DataBaseValidatorRequestHandler implements RequestHandler {
    private final static Logger LOGGER = LoggerFactory.getLogger(DataBaseValidatorRequestHandler.class);

    private final LocalFeedService localFeedService;
    private final MetaMap metaMap;

    public DataBaseValidatorRequestHandler(final LocalFeedService localFeedService, final MetaMap metaMap) {
        this.localFeedService = localFeedService;
        this.metaMap = metaMap;
    }

    @Override
    public void handleHeader() throws IOException {
        String feedName = metaMap.get(StroomHeaderArguments.FEED);
        if (feedName == null) {
            throw new StroomStreamException(StroomStatusCode.FEED_MUST_BE_SPECIFIED);
        }

        String senderDn = metaMap.get(StroomHeaderArguments.REMOTE_DN);
        GetFeedStatusRequest request = new GetFeedStatusRequest(feedName, senderDn);
        GetFeedStatusResponse response = localFeedService.getFeedStatus(request);

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("handleHeader() - " + request + " -> " + response);
        }

        if (response.getStatus() == FeedStatus.Reject) {
            throw new StroomStreamException(StroomStatusCode.FEED_IS_NOT_SET_TO_RECEIVED_DATA);
        }

        if (response.getStatus() == FeedStatus.Drop) {
            throw new DropStreamException();
        }
    }

    @Override
    public void handleEntryStart(StroomZipEntry stroomZipEntry) throws IOException {
        // NA for DB Validator
    }

    @Override
    public void handleEntryEnd() throws IOException {
        // NA for DB Validator
    }

    @Override
    public void handleEntryData(byte[] buffer, int off, int len) throws IOException {
        // NA for DB Validator
    }

    @Override
    public void handleError() throws IOException {
        // NA for DB Validator
    }

    @Override
    public void handleFooter() throws IOException {
        // NA for DB Validator
    }

    @Override
    public void validate() {
    }

}
