package stroom.index.client;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import stroom.core.client.ContentManager;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.docstore.shared.DocRefUtil;
import stroom.document.client.DocumentPlugin;
import stroom.document.client.DocumentPluginEventManager;
import stroom.entity.client.presenter.DocumentEditPresenter;
import stroom.index.client.presenter.IndexPresenter;
import stroom.index.shared.IndexDoc;
import stroom.index.shared.IndexResource;

import java.util.function.Consumer;

public class IndexPlugin extends DocumentPlugin<IndexDoc> {
    private static final IndexResource INDEX_RESOURCE = GWT.create(IndexResource.class);

    private final Provider<IndexPresenter> editorProvider;
    private final RestFactory restFactory;

    @Inject
    public IndexPlugin(final EventBus eventBus,
                       final Provider<IndexPresenter> editorProvider,
                       final RestFactory restFactory,
                       final ContentManager contentManager,
                       final DocumentPluginEventManager entityPluginEventManager) {
        super(eventBus, contentManager, entityPluginEventManager);
        this.editorProvider = editorProvider;
        this.restFactory = restFactory;
    }

    @Override
    protected DocumentEditPresenter<?, ?> createEditor() {
        return editorProvider.get();
    }

    @Override
    public void load(final DocRef docRef, final Consumer<IndexDoc> resultConsumer, final Consumer<Throwable> errorConsumer) {
        final Rest<IndexDoc> rest = restFactory.create();
        rest
                .onSuccess(resultConsumer)
                .onFailure(errorConsumer)
                .call(INDEX_RESOURCE)
                .read(docRef);
    }

    @Override
    public void save(final DocRef docRef, final IndexDoc document, final Consumer<IndexDoc> resultConsumer, final Consumer<Throwable> errorConsumer) {
        final Rest<IndexDoc> rest = restFactory.create();
        rest
                .onSuccess(resultConsumer)
                .onFailure(errorConsumer)
                .call(INDEX_RESOURCE)
                .update(document);
    }
    
    @Override
    public String getType() {
        return IndexDoc.DOCUMENT_TYPE;
    }

    @Override
    protected DocRef getDocRef(final IndexDoc document) {
        return DocRefUtil.create(document);
    }
}

