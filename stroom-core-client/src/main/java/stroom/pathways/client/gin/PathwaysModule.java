package stroom.pathways.client.gin;

import stroom.core.client.gin.PluginModule;
import stroom.pathways.client.PathwaysPlugin;
import stroom.pathways.client.presenter.PathwayEditPresenter;
import stroom.pathways.client.presenter.PathwayEditPresenter.PathwayEditView;
import stroom.pathways.client.view.PathwayEditViewImpl;
import stroom.pathways.client.presenter.PathwaysPresenter;
import stroom.pathways.client.presenter.PathwaysSettingsPresenter;
import stroom.pathways.client.presenter.PathwaysSettingsPresenter.PathwaysSettingsView;
import stroom.pathways.client.view.PathwaysSettingsViewImpl;

public class PathwaysModule extends PluginModule {

    @Override
    protected void configure() {
        bindPlugin(PathwaysPlugin.class);
        bind(PathwaysPresenter.class);
        bindPresenterWidget(PathwaysSettingsPresenter.class,
                PathwaysSettingsView.class,
                PathwaysSettingsViewImpl.class);
        bindPresenterWidget(PathwayEditPresenter.class,
                PathwayEditView.class,
                PathwayEditViewImpl.class);
    }
}
