package stroom.externaldoc.client.gin;

import stroom.externaldoc.client.ExternalDocRefPlugin;
import stroom.externaldoc.client.presenter.ExternalDocRefPresenter;
import stroom.core.client.gin.PluginModule;

public class ExternalDocRefModule extends PluginModule {
    @Override
    protected void configure() {
        bindPlugin(ExternalDocRefPlugin.class);
        bind(ExternalDocRefPresenter.class);
    }
}