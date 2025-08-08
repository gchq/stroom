package stroom.pathways.client.gin;

import stroom.pathways.client.PathwaysPlugin;
import stroom.pathways.client.presenter.PathwaysPresenter;
import stroom.pathways.client.presenter.PathwaysSettingsPresenter;

import com.google.gwt.inject.client.AsyncProvider;

public interface PathwaysGinjector {

    AsyncProvider<PathwaysPlugin> getPathwaysPlugin();

    AsyncProvider<PathwaysPresenter> getPathwaysPresenter();

    AsyncProvider<PathwaysSettingsPresenter> getPathwaysSettingsPresenter();
}
