package stroom.elastic.client.presenter;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import stroom.alert.client.event.AlertEvent;
import stroom.cell.clickable.client.Hyperlink;
import stroom.document.client.DocumentTabData;
import stroom.elastic.shared.ElasticIndex;
import stroom.entity.client.presenter.DocumentEditPresenter;
import stroom.node.client.ClientPropertyCache;
import stroom.node.shared.ClientProperties;
import stroom.security.client.ClientSecurityContext;
import stroom.svg.client.Icon;
import stroom.svg.client.SvgPresets;
import stroom.widget.iframe.client.presenter.IFramePresenter;

public class ElasticIndexExternalPresenter
        extends DocumentEditPresenter<IFramePresenter.IFrameView, ElasticIndex>
        implements DocumentTabData {

    private final IFramePresenter settingsPresenter;
    private String elasticQueryUiUrl;

    @Inject
    public ElasticIndexExternalPresenter(final EventBus eventBus,
                                         final IFramePresenter iFramePresenter,
                                         final ClientSecurityContext securityContext,
                                         final ClientPropertyCache clientPropertyCache) {
        super(eventBus, iFramePresenter.getView(), securityContext);
        this.settingsPresenter = iFramePresenter;
        this.settingsPresenter.setIcon(getIcon());

        clientPropertyCache.get()
                .onSuccess(result -> this.elasticQueryUiUrl = result.get(ClientProperties.URL_ELASTIC_QUERY_UI))
                .onFailure(caught -> AlertEvent.fireError(ElasticIndexExternalPresenter.this, caught.getMessage(), null));
    }

    @Override
    protected void onRead(final ElasticIndex elasticIndex) {
        final Hyperlink hyperlink = new Hyperlink.HyperlinkBuilder()
                .href(this.elasticQueryUiUrl + "/" + elasticIndex.getUuid())
                .build();
        this.settingsPresenter.setHyperlink(hyperlink);
    }

    @Override
    protected void onWrite(final ElasticIndex elasticIndex) {

    }

    @Override
    public String getType() {
        return ElasticIndex.ENTITY_TYPE;
    }

    @Override
    public Icon getIcon() {
        return SvgPresets.ELASTIC_SEARCH;
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
