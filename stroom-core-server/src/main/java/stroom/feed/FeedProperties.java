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

    private final FeedDocCache feedDocCache;

    @Inject
    FeedProperties(final FeedDocCache feedDocCache) {
        this.feedDocCache = feedDocCache;
    }

    public String getDisplayClassification(final String feedName) {
        final Optional<FeedDoc> optional = feedDocCache.get(feedName);
        final String classification = optional
                .map(FeedDoc::getClassification)
                .filter(c -> !c.trim().isEmpty())
                .orElse(StroomProperties.getProperty("stroom.unknownClassification"));

        return classification.trim().toUpperCase();
    }

    public String getEncoding(final String feedName, final StreamType streamType) {
        final Optional<FeedDoc> optional = feedDocCache.get(feedName);
        String encoding = null;
        if (optional.isPresent() && streamType != null) {
            if (StreamType.CONTEXT.equals(streamType)) {
                encoding = optional.get().getContextEncoding();
            } else if (streamType.isStreamTypeRaw()) {
                encoding = optional.get().getEncoding();
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
