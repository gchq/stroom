package stroom.feed;

import stroom.feed.shared.FeedDoc;

import java.util.Optional;

public interface FeedNameCache {
    Optional<FeedDoc> get(String feedName);
}
