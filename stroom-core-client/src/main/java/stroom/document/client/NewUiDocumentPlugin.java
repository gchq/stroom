package stroom.document.client;

import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import stroom.core.client.ContentManager;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.docref.DocRef;
import stroom.docstore.shared.Doc;
import stroom.docstore.shared.DocRefUtil;
import stroom.document.client.presenter.NewUiDocRefPresenter;
import stroom.entity.client.presenter.DocumentEditPresenter;
import stroom.index.shared.IndexDoc;

import javax.inject.Inject;

public class NewUiDocumentPlugin extends DocumentPlugin<Doc> {
    private final Provider<NewUiDocRefPresenter> editorProvider;

    @Inject
    public NewUiDocumentPlugin(final EventBus eventBus,
                               final Provider<NewUiDocRefPresenter> editorProvider,
                               final ClientDispatchAsync dispatcher,
                               final ContentManager contentManager,
                               final DocumentPluginEventManager documentPluginEventManager) {
        super(eventBus, dispatcher, contentManager, documentPluginEventManager);

        this.editorProvider = editorProvider;
        documentPluginEventManager.registerPlugin(IndexDoc.DOCUMENT_TYPE, this);
    }

    @Override
    protected DocumentEditPresenter<?, ?> createEditor() {
        return editorProvider.get();
    }

    @Override
    protected DocRef getDocRef(Doc document) {
        return DocRefUtil.create(document);
    }

    @Override
    public String getType() {
        return null;
    }
}
