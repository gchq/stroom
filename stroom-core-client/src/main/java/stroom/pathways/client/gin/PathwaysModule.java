package stroom.pathways.client.gin;

import stroom.core.client.gin.PluginModule;
import stroom.pathways.client.PathwaysPlugin;
import stroom.pathways.client.presenter.ConstraintEditPresenter;
import stroom.pathways.client.presenter.ConstraintEditPresenter.ConstraintEditView;
import stroom.pathways.client.presenter.PathwayEditPresenter;
import stroom.pathways.client.presenter.PathwayEditPresenter.PathwayEditView;
import stroom.pathways.client.presenter.PathwayTreePresenter;
import stroom.pathways.client.presenter.PathwayTreePresenter.PathwayTreeView;
import stroom.pathways.client.presenter.PathwaysPresenter;
import stroom.pathways.client.presenter.PathwaysSettingsPresenter;
import stroom.pathways.client.presenter.PathwaysSettingsPresenter.PathwaysSettingsView;
import stroom.pathways.client.presenter.PathwaysSplitPresenter;
import stroom.pathways.client.presenter.PathwaysSplitPresenter.PathwaysSplitView;
import stroom.pathways.client.view.ConstraintEditViewImpl;
import stroom.pathways.client.view.PathwayEditViewImpl;
import stroom.pathways.client.view.PathwayTreeViewImpl;
import stroom.pathways.client.view.PathwaysSettingsViewImpl;
import stroom.pathways.client.view.PathwaysSplitViewImpl;

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
        bindPresenterWidget(PathwaysSplitPresenter.class,
                PathwaysSplitView.class,
                PathwaysSplitViewImpl.class);
        bindPresenterWidget(PathwayTreePresenter.class,
                PathwayTreeView.class,
                PathwayTreeViewImpl.class);
        bindPresenterWidget(ConstraintEditPresenter.class,
                ConstraintEditView.class,
                ConstraintEditViewImpl.class);
    }
}
