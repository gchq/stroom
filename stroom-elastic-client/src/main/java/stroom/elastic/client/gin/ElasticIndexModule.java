package stroom.elastic.client.gin;

import stroom.core.client.gin.PluginModule;
import stroom.elastic.client.ElasticIndexPlugin;
import stroom.elastic.client.presenter.ElasticIndexExternalPresenter;

public class ElasticIndexModule extends PluginModule {
    @Override
    protected void configure() {
        bindPlugin(ElasticIndexPlugin.class);
        bind(ElasticIndexExternalPresenter.class);
        //bind(ElasticIndexPresenter.class);
        //bindPresenterWidget(ElasticIndexPresenter.class, ElasticIndexSettingsPresenter.ElasticIndexSettingsView.class, ElasticIndexSettingsViewImpl.class);
    }
}
