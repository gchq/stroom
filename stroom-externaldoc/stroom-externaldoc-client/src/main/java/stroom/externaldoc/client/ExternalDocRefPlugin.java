package stroom.externaldoc.client;

import com.google.web.bindery.event.shared.EventBus;
import stroom.alert.client.event.AlertEvent;
import stroom.core.client.ContentManager;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.docref.DocRef;
import stroom.document.client.DocumentPlugin;
import stroom.document.client.DocumentPluginEventManager;
import stroom.entity.client.presenter.DocumentEditPresenter;
import stroom.explorer.shared.SharedDocRef;
import stroom.externaldoc.client.presenter.ExternalDocRefPresenter;
import stroom.ui.config.client.UiConfigCache;
import stroom.ui.config.shared.UrlConfig;

import javax.inject.Inject;
import javax.inject.Provider;

public class ExternalDocRefPlugin extends DocumentPlugin<SharedDocRef> {
    private final Provider<ExternalDocRefPresenter> editorProvider;

    private static final String ANNOTATIONS_INDEX = "AnnotationsIndex";
    private static final String ELASTIC_INDEX = "ElasticIndex";

    @Inject
    public ExternalDocRefPlugin(final EventBus eventBus,
                                final Provider<ExternalDocRefPresenter> editorProvider,
                                final ClientDispatchAsync dispatcher,
                                final ContentManager contentManager,
                                final DocumentPluginEventManager entityPluginEventManager,
                                final UiConfigCache clientPropertyCache) {
        super(eventBus, dispatcher, contentManager, entityPluginEventManager);
        this.editorProvider = editorProvider;

        clientPropertyCache.get()
                .onSuccess(result -> {
                    if (result.getUrlConfig() != null) {
                        final UrlConfig urlConfig = result.getUrlConfig();
                        if (urlConfig.getAnnotations() != null && urlConfig.getAnnotations().length() > 0) {
                            registerAsPluginForType(ANNOTATIONS_INDEX);
                        }
                        if (urlConfig.getElastic() != null && urlConfig.getElastic().length() > 0) {
                            registerAsPluginForType(ELASTIC_INDEX);
                        }
                    }
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
