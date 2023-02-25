package stroom.about.client.gin;

import stroom.about.client.AboutPlugin;
import stroom.about.client.presenter.AboutPresenter;
import stroom.about.client.presenter.AboutPresenter.AboutProxy;
import stroom.about.client.presenter.AboutPresenter.AboutView;
import stroom.about.client.view.AboutViewImpl;
import stroom.core.client.gin.PluginModule;

public class AboutModule extends PluginModule {

    @Override
    protected void configure() {
        bind(AboutPlugin.class).asEagerSingleton();

        bindPresenter(AboutPresenter.class, AboutView.class, AboutViewImpl.class, AboutProxy.class);
    }
}
