package stroom.annotations.client.gin;

import stroom.annotations.client.AnnotationsIndexPlugin;
import stroom.annotations.client.presenter.AnnotationsIndexExternalPresenter;
import stroom.core.client.gin.PluginModule;

public class AnnotationsIndexModule extends PluginModule {
    @Override
    protected void configure() {
        bindPlugin(AnnotationsIndexPlugin.class);
        bind(AnnotationsIndexExternalPresenter.class);
    }
}