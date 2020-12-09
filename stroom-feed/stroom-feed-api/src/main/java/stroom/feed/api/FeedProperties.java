package stroom.feed.api;

import stroom.feed.shared.FeedDoc.FeedStatus;

public interface FeedProperties {
    String getDisplayClassification(final String feedName);

    /**
     * @param feedName The name of the feed
     * @param streamTypeName The name of the stream type, e.g. Raw Reference. Can be null if not known.
     * @param childStreamTypeName The name of the child stream type, e.g. Context, or null for the data child stream
     *                            or if not applicable
     * @return The applicable encoding
     */
    String getEncoding(final String feedName,
                       final String streamTypeName,
                       final String childStreamTypeName);

    String getStreamTypeName(final String feedName);

    boolean isReference(String feedName);

    @Deprecated // To be replaced with policy based status decision.
    FeedStatus getStatus(String feedName);
}
