package stroom.feed.impl;

import stroom.data.shared.StreamTypeNames;
import stroom.feed.api.FeedProperties;
import stroom.feed.shared.FeedDoc;
import stroom.feed.shared.FeedDoc.FeedStatus;
import stroom.util.io.StreamUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.Optional;
import javax.inject.Inject;

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

    /**
     * @param feedName            The feed name.
     * @param streamTypeName      The name of the stream type, e.g. Raw Reference
     * @param childStreamTypeName The name of the child stream type, e.g. Context, or null for the data child stream
     *                            or if not applicable
     * @return The applicable encoding
     */
    public String getEncoding(final String feedName,
                              final String streamTypeName,
                              final String childStreamTypeName) throws UnsupportedEncodingException {

        final Optional<FeedDoc> optionalFeedDoc = feedDocCache.get(feedName);
        if (optionalFeedDoc.isPresent()) {
            final FeedDoc feedDoc = optionalFeedDoc.get();
            if (StreamTypeNames.CONTEXT.equals(childStreamTypeName)) {
                return resolveEncoding(feedName, childStreamTypeName, feedDoc.getContextEncoding());

            } else if (RawStreamTypes.isRawType(streamTypeName)
                    && childStreamTypeName == null) {
                // Child stream type is null for the data child streams
                // Only raw streams have a custom encoding, everything internal is the default charset,
                // i.e. UTF8
                return resolveEncoding(feedName, streamTypeName, feedDoc.getContextEncoding());

            } else {
                return StreamUtil.DEFAULT_CHARSET_NAME;
            }
        }
        return StreamUtil.DEFAULT_CHARSET_NAME;
    }

    private String resolveEncoding(final String feedName,
                                   final String streamTypeName,
                                   final String encoding) throws UnsupportedEncodingException {
        if (encoding != null && !encoding.isBlank()) {
            try {
                if (Charset.isSupported(encoding)) {
                    return encoding;
                }
            } catch (final RuntimeException e) {
                // Ignore.
            }

            final String message = "Unsupported encoding '" +
                    encoding +
                    "' for feed '" +
                    feedName +
                    "' and type '" +
                    streamTypeName +
                    "'. Using default '" +
                    StreamUtil.DEFAULT_CHARSET_NAME +
                    "'.";
            LOGGER.debug(message);
            throw new UnsupportedEncodingException(message);
        }

        return StreamUtil.DEFAULT_CHARSET_NAME;
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
                .orElse(null);
    }
}
