package stroom.core.receive;

import stroom.feed.shared.FeedDoc;
import stroom.meta.api.AttributeMap;
import stroom.receive.common.ContentTemplates;
import stroom.util.shared.UserDesc;

import java.nio.file.Path;
import java.util.Optional;

public interface ContentAutoCreationService {

    /**
     * Will create a feed using the supplied name if auto-content creation
     * is enabled.
     */
    Optional<FeedDoc> tryCreateFeed(final String feedName,
                                    final UserDesc userDesc,
                                    final AttributeMap attributeMap);

    void addContentTemplates(final ContentTemplates contentTemplates,
                             final Path sourceFile);

    void removeTemplatesForFile(final Path sourceFile);
}
