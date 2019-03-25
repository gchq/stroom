package stroom.feed.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.feed.api.FeedProperties;
import stroom.feed.shared.FeedDoc;
import stroom.feed.shared.FeedDoc.FeedStatus;
import stroom.data.shared.StreamTypeNames;
import stroom.util.io.StreamUtil;

import javax.inject.Inject;
import java.nio.charset.Charset;
import java.util.Optional;

public class FeedPropertiesImpl implements FeedProperties {
    private static final Logger LOGGER = LoggerFactory.getLogger(FeedPropertiesImpl.class);

    private final FeedDocCache feedDocCache;
    private final FeedConfig feedConfig;

    @Inject
    FeedPropertiesImpl(final FeedDocCache feedDocCache,
                       final FeedConfig feedConfig) {
        this.feedDocCache = feedDocCache;
        this.feedConfig = feedConfig;
    }

    public String getDisplayClassification(final String feedName) {
        final Optional<FeedDoc> optional = feedDocCache.get(feedName);
        final String classification = optional
                .map(FeedDoc::getClassification)
                .filter(c -> !c.trim().isEmpty())
                .orElse(feedConfig.getUnknownClassification());

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

    @Override
    public String getStreamTypeName(final String feedName) {
        return feedDocCache.get(feedName)
                .map(FeedDoc::getStreamType)
                .orElse(StreamTypeNames.RAW_EVENTS);
    }

    @Override
    public boolean isReference(final String feedName) {
        return feedDocCache.get(feedName)
                .map(FeedDoc::isReference)
                .orElse(false);
    }

    @Override
    public FeedStatus getStatus(final String feedName) {
        return feedDocCache.get(feedName)
                .map(FeedDoc::getStatus)
                .orElse(FeedStatus.REJECT);
    }
}
