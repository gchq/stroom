package stroom.document.client.presenter;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import stroom.docref.DocRef;
import stroom.docstore.shared.Doc;
import stroom.entity.client.presenter.DocumentEditPresenter;
import stroom.iframe.client.presenter.IFramePresenter;
import stroom.security.client.api.ClientSecurityContext;
import stroom.ui.config.client.UiConfigCache;


public class NewUiDocRefPresenter extends DocumentEditPresenter<IFramePresenter.IFrameView, Doc> {

    private final UiConfigCache clientPropertyCache;

    @Inject
    public NewUiDocRefPresenter(final EventBus eventBus,
                               final IFramePresenter.IFrameView view,
                               final ClientSecurityContext securityContext,
                                final UiConfigCache clientPropertyCache
                                ) {
        super(eventBus, view, securityContext);

        this.clientPropertyCache = clientPropertyCache;
    }

    @Override
    public void save() {
    }

    @Override
    protected void onRead(final DocRef docRef,
                          final Doc entity) {
//        clientPropertyCache.get().onSuccess(uiConfig -> {
//            getView().setUrl(uiConfig.getUrl().getEditDoc() + docRef.getUuid());
//        });
    }


    @Override
    protected void onWrite(final Doc entity) {

    }

    @Override
    public String getType() {
        return null;
    }
}
