package stroom.elastic.client;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import stroom.core.client.ContentManager;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.document.client.DocumentPlugin;
import stroom.document.client.DocumentPluginEventManager;
import stroom.elastic.client.presenter.ElasticIndexExternalPresenter;
import stroom.entity.client.presenter.DocumentEditPresenter;
import stroom.entity.shared.ExternalDocRefConstants;
import stroom.entity.shared.SharedDocRef;
import stroom.query.api.v2.DocRef;

public class ElasticIndexPlugin extends DocumentPlugin<SharedDocRef> {
    private final Provider<ElasticIndexExternalPresenter> editorProvider;

    @Inject
    public ElasticIndexPlugin(final EventBus eventBus,
                              final Provider<ElasticIndexExternalPresenter> editorProvider,
                              final ClientDispatchAsync dispatcher,
                              final ContentManager contentManager,
                              final DocumentPluginEventManager entityPluginEventManager) {
        super(eventBus, dispatcher, contentManager, entityPluginEventManager);
        this.editorProvider = editorProvider;
    }

    @Override
    protected DocumentEditPresenter<?, ?> createEditor() {
        return editorProvider.get();
    }

    @Override
    protected DocRef getDocRef(final SharedDocRef document) {
        return new DocRef.Builder()
                .type(document.getType())
                .uuid(document.getUuid())
                .name(document.getName())
                .build();
    }

    @Override
    public String getType() {
        return ExternalDocRefConstants.ELASTIC_INDEX;
    }
}
