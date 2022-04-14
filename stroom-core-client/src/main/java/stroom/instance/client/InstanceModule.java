package stroom.instance.client;

import stroom.core.client.gin.PluginModule;

public class InstanceModule extends PluginModule {

    @Override
    protected void configure() {
        bind(ClientApplicationInstance.class).asEagerSingleton();
    }
}
