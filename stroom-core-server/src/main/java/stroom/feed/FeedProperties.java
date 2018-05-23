package stroom.feed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.feed.shared.FeedDoc;
import stroom.streamstore.shared.StreamType;
import stroom.util.config.StroomProperties;
import stroom.util.io.StreamUtil;

import javax.inject.Inject;
import java.nio.charset.Charset;
import java.util.Optional;

public class FeedProperties {
    private static final Logger LOGGER = LoggerFactory.getLogger(FeedProperties.class);

    private final FeedNameCache feedNameCache;

    @Inject
    FeedProperties(final FeedNameCache feedNameCache) {
        this.feedNameCache = feedNameCache;
    }

    public String getDisplayClassification(final String feedName) {
        String classification = null;

        final Optional<FeedDoc> feed = feedNameCache.get(feedName);
        if (feed.isPresent()) {
            classification = feed.get().getClassification();
        }

        if (classification == null || classification.trim().isEmpty()) {
            return StroomProperties.getProperty("stroom.unknownClassification");
        }

        return classification.trim().toUpperCase();
    }

    public String getEncoding(final String feedName, final StreamType streamType) {
        final FeedDoc feed = feedNameCache.get(feedName);
        String encoding = null;
        if (feed != null && streamType != null) {
            if (StreamType.CONTEXT.equals(streamType)) {
                encoding = feed.getContextEncoding();
            } else if (streamType.isStreamTypeRaw()) {
                encoding = feed.getEncoding();
            }
        }

        if (encoding == null || encoding.trim().length() == 0) {
            encoding = StreamUtil.DEFAULT_CHARSET_NAME;
        }

        // Make sure the requested charset is supported.
        boolean supported = false;
        try {
            supported = Charset.isSupported(encoding);
        } catch (final RuntimeException e) {
            // Ignore.
        }
        if (!supported) {
            LOGGER.error(
                    "Unsupported charset '" + encoding + "'. Using default '" + StreamUtil.DEFAULT_CHARSET_NAME + "'.");
            encoding = StreamUtil.DEFAULT_CHARSET_NAME;
        }

        return encoding;
    }
}
