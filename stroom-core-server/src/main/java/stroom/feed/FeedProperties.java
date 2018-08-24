package stroom.feed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.datafeed.DataFeedConfig;
import stroom.feed.shared.FeedDoc;
import stroom.streamstore.shared.RawStreamTypes;
import stroom.util.io.StreamUtil;

import javax.inject.Inject;
import java.nio.charset.Charset;
import java.util.Optional;

public class FeedProperties {
    private static final Logger LOGGER = LoggerFactory.getLogger(FeedProperties.class);

    private final FeedDocCache feedDocCache;
    private final DataFeedConfig dataFeedConfig;

    @Inject
    FeedProperties(final FeedDocCache feedDocCache,
                   final DataFeedConfig dataFeedConfig) {
        this.feedDocCache = feedDocCache;
        this.dataFeedConfig = dataFeedConfig;
    }

    public String getDisplayClassification(final String feedName) {
        final Optional<FeedDoc> optional = feedDocCache.get(feedName);
        final String classification = optional
                .map(FeedDoc::getClassification)
                .filter(c -> !c.trim().isEmpty())
                .orElse(dataFeedConfig.getUnknownClassification());

        return classification.trim().toUpperCase();
    }

    public String getEncoding(final String feedName, final String streamTypeName) {
        final Optional<FeedDoc> optional = feedDocCache.get(feedName);
        String encoding = null;
        if (optional.isPresent() && streamTypeName != null) {
            if ("Context".equals(streamTypeName)) {
                encoding = optional.get().getContextEncoding();
            } else if (RawStreamTypes.isRawType(streamTypeName)) {
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
