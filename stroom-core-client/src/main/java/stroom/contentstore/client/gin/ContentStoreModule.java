package stroom.contentstore.client.gin;

import stroom.contentstore.client.ContentStorePlugin;
import stroom.contentstore.client.presenter.ContentStoreCredentialsDialogPresenter;
import stroom.contentstore.client.presenter.ContentStoreCredentialsDialogPresenter.ContentStoreCredentialsDialogView;
import stroom.contentstore.client.presenter.ContentStorePresenter;
import stroom.contentstore.client.presenter.ContentStorePresenter.ContentStoreView;
import stroom.contentstore.client.view.ContentStoreCredentialsDialogViewImpl;
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

        // Tie up the Credentials dialog
        bindPresenterWidget(ContentStoreCredentialsDialogPresenter.class,
                ContentStoreCredentialsDialogView.class,
                ContentStoreCredentialsDialogViewImpl.class);

    }

}
