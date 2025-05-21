package stroom.appstore.client.gin;

import stroom.appstore.client.AppStorePlugin;
import stroom.appstore.client.presenter.AppStorePresenter;
import stroom.appstore.client.view.AppStoreViewImpl;
import stroom.core.client.gin.PluginModule;

/**
 * Ensures the GIN injection works in GWT.
 */
public class AppStoreModule extends PluginModule {

    @Override
    protected void configure() {
        // Generate menu item for AppStore
        bindPlugin(AppStorePlugin.class);

        // Tie the presenter, View interface and View together
        bindPresenterWidget(AppStorePresenter.class,
                AppStorePresenter.AppStoreView.class,
                AppStoreViewImpl.class);
    }

}
