package stroom.index.client.gin;

import stroom.core.client.gin.PluginModule;
import stroom.index.client.IndexPlugin;

public class IndexModule extends PluginModule {
    @Override
    protected void configure() {
        bindPlugin(IndexPlugin.class);
    }
}
