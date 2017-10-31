package stroom.elastic.client.gin;

import stroom.core.client.gin.PluginModule;
import stroom.elastic.client.ElasticIndexPlugin;
import stroom.elastic.client.presenter.ElasticIndexPresenter;
import stroom.elastic.client.presenter.ElasticIndexSettingsPresenter;
import stroom.elastic.client.view.ElasticIndexSettingsViewImpl;

public class ElasticIndexModule extends PluginModule {
    @Override
    protected void configure() {
        bindPlugin(ElasticIndexPlugin.class);
        bind(ElasticIndexPresenter.class);
        bindPresenterWidget(ElasticIndexPresenter.class, ElasticIndexSettingsPresenter.ElasticIndexSettingsView.class, ElasticIndexSettingsViewImpl.class);
    }
}
