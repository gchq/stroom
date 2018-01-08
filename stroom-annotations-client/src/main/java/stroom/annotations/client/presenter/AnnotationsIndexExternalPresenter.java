package stroom.annotations.client.presenter;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import stroom.alert.client.event.AlertEvent;
import stroom.cell.clickable.client.Hyperlink;
import stroom.document.client.DocumentTabData;
import stroom.entity.client.presenter.DocumentEditPresenter;
import stroom.entity.shared.ExternalDocRefConstants;
import stroom.entity.shared.SharedDocRef;
import stroom.node.client.ClientPropertyCache;
import stroom.node.shared.ClientProperties;
import stroom.query.api.v2.DocRef;
import stroom.security.client.ClientSecurityContext;
import stroom.svg.client.Icon;
import stroom.svg.client.SvgPresets;
import stroom.widget.iframe.client.presenter.IFramePresenter;

public class AnnotationsIndexExternalPresenter
        extends DocumentEditPresenter<IFramePresenter.IFrameView, SharedDocRef>
        implements DocumentTabData {

    private final IFramePresenter settingsPresenter;
    private String uiUrl;

    @Inject
    public AnnotationsIndexExternalPresenter(final EventBus eventBus,
                                             final IFramePresenter iFramePresenter,
                                             final ClientSecurityContext securityContext,
                                             final ClientPropertyCache clientPropertyCache) {
        super(eventBus, iFramePresenter.getView(), securityContext);
        this.settingsPresenter = iFramePresenter;
        this.settingsPresenter.setIcon(getIcon());

        clientPropertyCache.get()
                .onSuccess(result -> this.uiUrl = result.get(ClientProperties.URL_ANNOTATIONS_QUERY_UI))
                .onFailure(caught -> AlertEvent.fireError(AnnotationsIndexExternalPresenter.this, caught.getMessage(), null));
    }

    @Override
    protected void onRead(final SharedDocRef document) {
        final Hyperlink hyperlink = new Hyperlink.HyperlinkBuilder()
                .href(this.uiUrl + "/" + document.getUuid())
                .build();
        this.settingsPresenter.setHyperlink(hyperlink);
    }

    @Override
    protected void onWrite(final SharedDocRef annotationsIndex) {

    }

    @Override
    public String getType() {
        return ExternalDocRefConstants.ANNOTATIONS_INDEX;
    }

    @Override
    public Icon getIcon() {
        return SvgPresets.ANNOTATIONS;
    }

    @Override
    public String getLabel() {
        return getDocRef().getName();
    }

    @Override
    public boolean isCloseable() {
        return true;
    }
}
