package stroom.contentstore.client.gin;

import stroom.contentstore.client.ContentStorePlugin;
import stroom.contentstore.client.presenter.ContentStorePresenter;
import stroom.contentstore.client.presenter.ContentStorePresenter.ContentStoreView;
import stroom.contentstore.client.view.ContentStoreViewImpl;
import stroom.core.client.gin.PluginModule;

/**
 * Ensures the GIN injection works in GWT.
 */
public class ContentStoreModule extends PluginModule {

    @Override
    protected void configure() {
        // Generate menu item for ContentStore
        bindPlugin(ContentStorePlugin.class);

        // Tie the presenters, View interfaces and Views together
        bindPresenterWidget(ContentStorePresenter.class,
                ContentStoreView.class,
                ContentStoreViewImpl.class);

    }

}
