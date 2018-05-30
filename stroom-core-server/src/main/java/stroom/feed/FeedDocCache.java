package stroom.feed;

import stroom.entity.shared.Clearable;
import stroom.feed.shared.FeedDoc;

import java.util.Optional;

public interface FeedDocCache extends Clearable {
    Optional<FeedDoc> get(String feedName);
}
