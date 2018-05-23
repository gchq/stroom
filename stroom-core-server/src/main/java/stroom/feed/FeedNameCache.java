package stroom.feed;

import stroom.feed.shared.Feed;

public interface FeedNameCache {
    Feed get(String feedName);
}
