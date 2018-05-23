package stroom.externaldoc.client;

import com.google.web.bindery.event.shared.EventBus;
import stroom.alert.client.event.AlertEvent;
import stroom.externaldoc.client.presenter.ExternalDocRefPresenter;
import stroom.core.client.ContentManager;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.document.client.DocumentPlugin;
import stroom.document.client.DocumentPluginEventManager;
import stroom.entity.client.presenter.DocumentEditPresenter;
import stroom.entity.shared.ExternalDocRefConstants;
import stroom.entity.shared.SharedDocRef;
import stroom.node.client.ClientPropertyCache;
import stroom.node.shared.ClientProperties;
import stroom.docref.DocRef;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.Map;

public class ExternalDocRefPlugin extends DocumentPlugin<SharedDocRef> {
    private final Provider<ExternalDocRefPresenter> editorProvider;

    @Inject
    public ExternalDocRefPlugin(final EventBus eventBus,
                                final Provider<ExternalDocRefPresenter> editorProvider,
                                final ClientDispatchAsync dispatcher,
                                final ContentManager contentManager,
                                final DocumentPluginEventManager entityPluginEventManager,
                                final ClientPropertyCache clientPropertyCache) {
        super(eventBus, dispatcher, contentManager, entityPluginEventManager);
        this.editorProvider = editorProvider;

        clientPropertyCache.get()
                .onSuccess(result -> {
                    final Map<String, String> docRefTypes = result.getLookupTable(ClientProperties.EXTERNAL_DOC_REF_TYPES, ClientProperties.URL_DOC_REF_UI_BASE);
                    docRefTypes.keySet().forEach(this::registerAsPluginForType);
                })
                .onFailure(caught -> AlertEvent.fireError(ExternalDocRefPlugin.this, caught.getMessage(), null));
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
        return null;
    }
}
