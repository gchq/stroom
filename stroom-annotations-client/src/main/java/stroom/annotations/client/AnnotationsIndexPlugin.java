package stroom.annotations.client;

import com.google.web.bindery.event.shared.EventBus;
import stroom.annotations.client.presenter.AnnotationsIndexExternalPresenter;
import stroom.core.client.ContentManager;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.document.client.DocumentPlugin;
import stroom.document.client.DocumentPluginEventManager;
import stroom.entity.client.presenter.DocumentEditPresenter;
import stroom.entity.shared.ExternalDocRefConstants;
import stroom.entity.shared.SharedDocRef;
import stroom.query.api.v2.DocRef;

import javax.inject.Inject;
import javax.inject.Provider;

public class AnnotationsIndexPlugin extends DocumentPlugin<SharedDocRef> {
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
    protected DocRef getDocRef(final SharedDocRef document) {
        return new DocRef.Builder()
                .type(document.getType())
                .uuid(document.getUuid())
                .name(document.getName())
                .build();
    }

    @Override
    public String getType() {
        return ExternalDocRefConstants.ANNOTATIONS_INDEX;
    }
}
