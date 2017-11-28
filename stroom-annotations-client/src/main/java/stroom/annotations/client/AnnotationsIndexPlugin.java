package stroom.annotations.client;

import com.google.web.bindery.event.shared.EventBus;
import stroom.annotations.client.presenter.AnnotationsIndexExternalPresenter;
import stroom.annotations.shared.AnnotationsIndex;
import stroom.core.client.ContentManager;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.document.client.DocumentPluginEventManager;
import stroom.entity.client.EntityPlugin;
import stroom.entity.client.presenter.DocumentEditPresenter;

import javax.inject.Inject;
import javax.inject.Provider;

public class AnnotationsIndexPlugin extends EntityPlugin<AnnotationsIndex> {
    private final Provider<AnnotationsIndexExternalPresenter> editorProvider;

    @Inject
    public AnnotationsIndexPlugin(final EventBus eventBus,
                                  final Provider<AnnotationsIndexExternalPresenter> editorProvider,
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
        return AnnotationsIndex.ENTITY_TYPE;
    }
}
