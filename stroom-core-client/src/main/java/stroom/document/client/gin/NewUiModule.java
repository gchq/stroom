package stroom.document.client.gin;

import stroom.core.client.gin.PluginModule;
import stroom.document.client.NewUiDocumentPlugin;
import stroom.document.client.presenter.NewUiDocRefPresenter;

public class NewUiModule extends PluginModule {

    @Override
    protected void configure() {
        bindPlugin(NewUiDocumentPlugin.class);
        bind(NewUiDocRefPresenter.class);
    }
}
