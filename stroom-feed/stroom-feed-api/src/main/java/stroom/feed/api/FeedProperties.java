package stroom.feed.api;

import stroom.docref.DocRef;
import stroom.feed.shared.FeedDoc.FeedStatus;

import java.io.UnsupportedEncodingException;

public interface FeedProperties {

    String getDisplayClassification(final String feedName);

    /**
     * @param feedName            The name of the feed
     * @param streamTypeName      The name of the stream type, e.g. Raw Reference. Can be null if not known.
     * @param childStreamTypeName The name of the child stream type, e.g. Context, or null for the data child stream
     *                            or if not applicable
     * @return The applicable encoding
     */
    String getEncoding(final String feedName,
                       final String streamTypeName,
                       final String childStreamTypeName) throws UnsupportedEncodingException;

    String getStreamTypeName(final String feedName);

    boolean isReference(String feedName);

    boolean exists(String feedName);

    /**
     * DEPRECATED: To be replaced with policy based status decision, one day, maybe.
     */
    @Deprecated
    FeedStatus getStatus(String feedName);

    /**
     * Get a DocRef for a feed doc by name.
     *
     * @param feedName The feed name to get the DocRef for.
     * @return The DocRef of the feed doc if found, else null.
     */
    DocRef getDocRefForName(final String feedName);
}
