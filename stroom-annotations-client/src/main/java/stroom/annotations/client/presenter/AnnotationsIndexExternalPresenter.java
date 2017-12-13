package stroom.annotations.client.presenter;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import stroom.alert.client.event.AlertEvent;
import stroom.annotations.shared.AnnotationsIndex;
import stroom.cell.clickable.client.Hyperlink;
import stroom.document.client.DocumentTabData;
import stroom.entity.client.presenter.ContentCallback;
import stroom.entity.client.presenter.DocumentEditPresenter;
import stroom.entity.client.presenter.DocumentEditTabPresenter;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.node.client.ClientPropertyCache;
import stroom.node.shared.ClientProperties;
import stroom.security.client.ClientSecurityContext;
import stroom.svg.client.Icon;
import stroom.svg.client.SvgPresets;
import stroom.widget.iframe.client.presenter.IFramePresenter;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;

public class AnnotationsIndexExternalPresenter
        extends DocumentEditPresenter<IFramePresenter.IFrameView, AnnotationsIndex>
        implements DocumentTabData {

    private static final TabData SETTINGS = new TabDataImpl("Annotations Index");

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
    protected void onRead(final AnnotationsIndex document) {
        final Hyperlink hyperlink = new Hyperlink.HyperlinkBuilder()
                .href(this.uiUrl + "/" + document.getUuid())
                .build();
        this.settingsPresenter.setHyperlink(hyperlink);
    }

    @Override
    protected void onWrite(final AnnotationsIndex annotationsIndex) {

    }

    @Override
    public String getType() {
        return AnnotationsIndex.ENTITY_TYPE;
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
