package stroom.core.receive;

import stroom.feed.shared.FeedDoc;
import stroom.meta.api.AttributeMap;

import java.util.Optional;

public interface ContentAutoCreationService {

    /**
     * Will create a feed using the supplied name if auto-content creation
     * is enabled.
     */
    Optional<FeedDoc> createFeed(final String feedName,
                                 final String subjectId,
                                 final AttributeMap attributeMap);

}
