package stroom.elastic.client.presenter;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import stroom.alert.client.event.AlertEvent;
import stroom.cell.clickable.client.Hyperlink;
import stroom.elastic.shared.ElasticIndex;
import stroom.entity.client.presenter.ContentCallback;
import stroom.entity.client.presenter.DocumentEditTabPresenter;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.node.client.ClientPropertyCache;
import stroom.node.shared.ClientProperties;
import stroom.security.client.ClientSecurityContext;
import stroom.svg.client.SvgPresets;
import stroom.widget.iframe.client.presenter.IFramePresenter;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;

public class ElasticIndexExternalPresenter extends DocumentEditTabPresenter<LinkTabPanelView, ElasticIndex>  {
    private static final TabData SETTINGS = new TabDataImpl("Elasticsearch Index");

    private final IFramePresenter settingsPresenter;
    private String elasticQueryUiUrl;

    @Inject
    public ElasticIndexExternalPresenter(final EventBus eventBus,
                                         final LinkTabPanelView view,
                                         final Provider<IFramePresenter> iFramePresenterProvider,
                                         final ClientSecurityContext securityContext,
                                         final ClientPropertyCache clientPropertyCache) {
        super(eventBus, view, securityContext);
        this.settingsPresenter = iFramePresenterProvider.get();
        this.settingsPresenter.setIcon(SvgPresets.ELASTIC_SEARCH);

        clientPropertyCache.get()
                .onSuccess(result -> this.elasticQueryUiUrl = result.get(ClientProperties.URL_ELASTIC_QUERY_UI))
                .onFailure(caught -> AlertEvent.fireError(ElasticIndexExternalPresenter.this, caught.getMessage(), null));

        addTab(SETTINGS);
        selectTab(SETTINGS);
    }

    @Override
    protected void getContent(final TabData tab, final ContentCallback callback) {
        if (SETTINGS.equals(tab)) {
            callback.onReady(settingsPresenter);
        } else {
            callback.onReady(null);
        }
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
}
