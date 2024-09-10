package stroom.contentindex;

import stroom.docref.DocContentHighlights;
import stroom.docref.DocContentMatch;
import stroom.docstore.api.ContentIndex;
import stroom.explorer.shared.FetchHighlightsRequest;
import stroom.explorer.shared.FindInContentRequest;
import stroom.index.impl.ContentIndexConfig;
import stroom.util.entityevent.EntityAction;
import stroom.util.entityevent.EntityEvent;
import stroom.util.entityevent.EntityEventHandler;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

@Singleton
@EntityEventHandler(
        action = {EntityAction.CREATE, EntityAction.UPDATE, EntityAction.DELETE})
public class ContentIndexImpl implements ContentIndex, EntityEvent.Handler {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ContentIndexImpl.class);

    private final Provider<ContentIndexConfig> contentIndexConfigProvider;
    private final Provider<LuceneContentIndex> luceneContentIndexProvider;
    private final Provider<BasicContentIndex> basicContentIndexProvider;

    private volatile EntityEvent.Handler handler = null;
    private volatile ContentIndex contentIndex = null;

    @Inject
    public ContentIndexImpl(final Provider<ContentIndexConfig> contentIndexConfigProvider,
                            final Provider<LuceneContentIndex> luceneContentIndexProvider,
                            final Provider<BasicContentIndex> basicContentIndexProvider) {
        this.contentIndexConfigProvider = contentIndexConfigProvider;
        this.basicContentIndexProvider = basicContentIndexProvider;
        this.luceneContentIndexProvider = luceneContentIndexProvider;
    }

    /**
     * Done like this to avoid circular dependencies in guice bindings
     */
    private ContentIndex getContentIndex() {
        if (contentIndex == null) {
            synchronized (this) {
                if (contentIndex == null) {
                    if (contentIndexConfigProvider.get().isEnabled()) {
                        final LuceneContentIndex index = luceneContentIndexProvider.get();
                        handler = index;
                        contentIndex = index;
                        contentIndex.reindex();
                    } else {
                        handler = event -> {
                        };
                        contentIndex = basicContentIndexProvider.get();
                    }
                }
            }
        }
        return contentIndex;
    }

    @Override
    public ResultPage<DocContentMatch> findInContent(final FindInContentRequest request) {
        return LOGGER.logDurationIfInfoEnabled(() ->
                getContentIndex().findInContent(request), "findInContent");
    }

    @Override
    public DocContentHighlights fetchHighlights(final FetchHighlightsRequest request) {
        return LOGGER.logDurationIfInfoEnabled(() ->
                getContentIndex().fetchHighlights(request), "fetchHighlights");
    }

    @Override
    public void reindex() {
        getContentIndex().reindex();
    }

    @Override
    public void onChange(final EntityEvent event) {
        // This will init the handler
        getContentIndex();
        handler.onChange(event);
    }
}
