package stroom.appstore.client.gin;

import stroom.appstore.client.AppStorePlugin;
import stroom.core.client.gin.PluginModule;

/**
 * Ensures the GIN injection works in GWT.
 */
public class AppStoreModule extends PluginModule {

    @Override
    protected void configure() {
        // Generate menu item for AppStore
        bindPlugin(AppStorePlugin.class);
    }

}
