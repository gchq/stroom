package stroom.index.client;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import stroom.alert.client.event.AlertEvent;
import stroom.core.client.ContentManager;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.docref.DocRef;
import stroom.docstore.shared.DocRefUtil;
import stroom.document.client.DocumentPlugin;
import stroom.document.client.DocumentPluginEventManager;
import stroom.hyperlink.client.Hyperlink;
import stroom.hyperlink.client.HyperlinkEvent;
import stroom.hyperlink.client.HyperlinkType;
import stroom.iframe.client.presenter.IFrameContentPresenter;
import stroom.index.shared.IndexDoc;
import stroom.svg.client.SvgPreset;
import stroom.svg.client.SvgPresets;
import stroom.ui.config.client.UiConfigCache;

/**
 * A plugin to allow Index documents to be loaded in an iFrame.
 *
 * TODO: This could be easily generalised into an iFrame
 */
public class IndexPlugin extends DocumentPlugin<IndexDoc> {
    private final Provider<IFrameContentPresenter> iFrameContentPresenterProvider;
    private final UiConfigCache clientPropertyCache;

    @Inject
    public IndexPlugin(
            final EventBus eventBus,
            final ClientDispatchAsync dispatcher,
            final ContentManager contentManager,
            final DocumentPluginEventManager documentPluginEventManager,
            final Provider<IFrameContentPresenter> iFrameContentPresenterProvider,
            final UiConfigCache clientPropertyCache) {
        super(eventBus, dispatcher, contentManager, documentPluginEventManager);
        this.iFrameContentPresenterProvider = iFrameContentPresenterProvider;
        this.clientPropertyCache = clientPropertyCache;
    }

    @Override
    protected IFrameContentPresenter createEditor() {
        return iFrameContentPresenterProvider.get();
    }

    @Override
    protected DocRef getDocRef(IndexDoc document) {
        return DocRefUtil.create(document);
    }

    @Override
    public String getType() {
        return IndexDoc.DOCUMENT_TYPE;
    }

    @Override
    public IFrameContentPresenter open(final DocRef docRef, final boolean forceOpen) {
        if(forceOpen) {
            clientPropertyCache.get()
                    .onSuccess(result -> {
                        final String url = result.getUrlConfig().getEditDoc() + docRef.getUuid();
                        final SvgPreset icon = SvgPresets.DATABASE;
                        final Hyperlink hyperlink = new Hyperlink.Builder()
                                .text("Indexes")
                                .href(url)
                                .type(HyperlinkType.TAB + "|Indexes")
                                .icon(icon)
                                .build();
                        HyperlinkEvent.fire(this, hyperlink);
                    })
                    .onFailure(caught -> AlertEvent.fireError(IndexPlugin.this, caught.getMessage(), null));
        }
        return createEditor();
    }
}

