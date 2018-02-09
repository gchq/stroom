package stroom.externaldoc.client.presenter;

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
import stroom.widget.iframe.client.presenter.IFrameContentPresenter;
import stroom.widget.iframe.client.presenter.IFramePresenter;

import java.util.Map;

public class ExternalDocRefPresenter
        extends DocumentEditPresenter<IFrameContentPresenter.IFrameContentView, SharedDocRef>
        implements DocumentTabData {

    private final IFrameContentPresenter settingsPresenter;
    private Map<String, String> uiUrls;

    @Inject
    public ExternalDocRefPresenter(final EventBus eventBus,
                                   final IFrameContentPresenter iFramePresenter,
                                   final ClientSecurityContext securityContext,
                                   final ClientPropertyCache clientPropertyCache) {
        super(eventBus, iFramePresenter.getView(), securityContext);
        this.settingsPresenter = iFramePresenter;

        clientPropertyCache.get()
                .onSuccess(result -> this.uiUrls = result.getLookupTable(ClientProperties.EXTERNAL_DOC_REF_TYPES, ClientProperties.URL_DOC_REF_UI_BASE))
                .onFailure(caught -> AlertEvent.fireError(ExternalDocRefPresenter.this, caught.getMessage(), null));
    }

    @Override
    protected void onRead(final DocRef docRef, final SharedDocRef document) {
        final Hyperlink hyperlink = new Hyperlink.HyperlinkBuilder()
                .href(this.uiUrls.get(document.getType()) + "/" + document.getUuid())
                .build();
        this.settingsPresenter.setIcon(getIcon());
        this.settingsPresenter.setHyperlink(hyperlink);
    }

    @Override
    protected void onWrite(final SharedDocRef annotationsIndex) {

    }

    @Override
    public String getType() {
        return getDocRef().getType();
    }

    @Override
    public Icon getIcon() {
        if (null != getDocRef()) {
            switch (getDocRef().getType()) {
                case ExternalDocRefConstants.ANNOTATIONS_INDEX:
                    return SvgPresets.ANNOTATIONS;
                case ExternalDocRefConstants.ELASTIC_INDEX:
                    return SvgPresets.ELASTIC_SEARCH;
            }
        }

        return null;
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
