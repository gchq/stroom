package stroom.feed.api;

import stroom.feed.shared.FeedDoc.FeedStatus;

public interface FeedProperties {
    String getDisplayClassification(final String feedName);

    String getEncoding(String feedName, String streamTypeName);

    String getStreamTypeName(final String feedName);

    boolean isReference(String feedName);

    @Deprecated // To be replaced with policy based status decision.
    FeedStatus getStatus(String feedName);
}
