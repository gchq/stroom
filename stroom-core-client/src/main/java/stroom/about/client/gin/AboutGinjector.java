package stroom.about.client.gin;

import stroom.about.client.AboutPlugin;
import stroom.about.client.presenter.AboutPresenter;

import com.google.gwt.inject.client.AsyncProvider;

public interface AboutGinjector {

    AsyncProvider<AboutPlugin> getAboutPlugin();

    AsyncProvider<AboutPresenter> getAboutPresenter();
}
