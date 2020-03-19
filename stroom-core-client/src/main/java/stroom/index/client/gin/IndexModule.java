package stroom.index.client.gin;

import stroom.core.client.gin.PluginModule;
import stroom.index.client.IndexPlugin;
import stroom.index.client.presenter.IndexFieldEditPresenter;
import stroom.index.client.presenter.IndexFieldEditPresenter.IndexFieldEditView;
import stroom.index.client.presenter.IndexPresenter;
import stroom.index.client.presenter.IndexSettingsPresenter;
import stroom.index.client.presenter.IndexSettingsPresenter.IndexSettingsView;
import stroom.index.client.presenter.IndexVolumeEditPresenter;
import stroom.index.client.presenter.IndexVolumeGroupEditPresenter;
import stroom.index.client.presenter.IndexVolumeGroupPresenter;
import stroom.index.client.view.IndexFieldEditViewImpl;
import stroom.index.client.view.IndexSettingsViewImpl;
import stroom.index.client.view.IndexVolumeEditViewImpl;

public class IndexModule extends PluginModule {
    @Override
    protected void configure() {
        bindPlugin(IndexPlugin.class);
        bind(IndexPresenter.class);

        bind(IndexVolumeGroupPresenter.class);
        bind(IndexVolumeGroupEditPresenter.class);

        bindPresenterWidget(IndexVolumeEditPresenter.class, IndexVolumeEditPresenter.IndexVolumeEditView.class, IndexVolumeEditViewImpl.class);
        bindPresenterWidget(IndexSettingsPresenter.class, IndexSettingsView.class, IndexSettingsViewImpl.class);
        bindPresenterWidget(IndexFieldEditPresenter.class, IndexFieldEditView.class, IndexFieldEditViewImpl.class);
    }
}
