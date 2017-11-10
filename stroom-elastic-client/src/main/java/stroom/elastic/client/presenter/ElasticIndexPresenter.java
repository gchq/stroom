package stroom.elastic.client.presenter;

import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import stroom.core.client.event.DirtyKeyDownHander;
import stroom.dictionary.shared.Dictionary;
import stroom.elastic.shared.ElasticIndex;
import stroom.entity.client.presenter.ContentCallback;
import stroom.entity.client.presenter.DocumentEditTabPresenter;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.security.client.ClientSecurityContext;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;

public class ElasticIndexPresenter extends DocumentEditTabPresenter<LinkTabPanelView, ElasticIndex>  {
    private static final TabData SETTINGS = new TabDataImpl("Elastic Index");

    private final ElasticIndexSettingsPresenter settingsPresenter;

    @Inject
    public ElasticIndexPresenter(final EventBus eventBus,
                                 final LinkTabPanelView view,
                                 final ElasticIndexSettingsPresenter settingsPresenter,
                                 final ClientSecurityContext securityContext) {
        super(eventBus, view, securityContext);
        this.settingsPresenter = settingsPresenter;

        registerHandler(settingsPresenter.addKeyDownHandler(new DirtyKeyDownHander() {
            @Override
            public void onDirty(final KeyDownEvent event) {
                setDirty(true);
            }
        }));

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
        //settingsPresenter.setIndexName(elasticIndex.getIndexName());
        //settingsPresenter.setIndexedType(elasticIndex.getIndexedType());
    }

    @Override
    protected void onWrite(final ElasticIndex elasticIndex) {
        //elasticIndex.setIndexName(settingsPresenter.getIndexName());
        //elasticIndex.setIndexedType(settingsPresenter.getIndexedType());
    }

    @Override
    public String getType() {
        return ElasticIndex.ENTITY_TYPE;
    }
}
