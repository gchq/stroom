package stroom.elastic.client;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import stroom.core.client.ContentManager;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.document.client.DocumentPluginEventManager;
import stroom.elastic.client.presenter.ElasticIndexPresenter;
import stroom.elastic.shared.ElasticIndex;
import stroom.entity.client.EntityPlugin;
import stroom.entity.client.presenter.DocumentEditPresenter;

public class ElasticIndexPlugin extends EntityPlugin<ElasticIndex> {
    private final Provider<ElasticIndexPresenter> editorProvider;

    @Inject
    public ElasticIndexPlugin(final EventBus eventBus,
                              final Provider<ElasticIndexPresenter> editorProvider,
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
    public String getType() {
        return ElasticIndex.ENTITY_TYPE;
    }
}
