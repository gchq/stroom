package stroom.credentials.client.gin;

import stroom.core.client.gin.PluginModule;
import stroom.credentials.client.CredentialsPlugin;
import stroom.credentials.client.presenter.CredentialsDetailsDialogPresenter;
import stroom.credentials.client.presenter.CredentialsPresenter;
import stroom.credentials.client.presenter.CredentialsPresenter.CredentialsView;
import stroom.credentials.client.view.CredentialsDetailsDialogViewImpl;
import stroom.credentials.client.view.CredentialsViewImpl;

/**
 * Ensures the GIN injection works in GWT.
 */
public class CredentialsModule extends PluginModule {

    @Override
    protected void configure() {
        // Generate menu item for Credentials UI
        bindPlugin(CredentialsPlugin.class);

        // Tie the presenters, View interfaces and Views together
        bindPresenterWidget(CredentialsPresenter.class,
                CredentialsView.class,
                CredentialsViewImpl.class);

        bindPresenterWidget(CredentialsDetailsDialogPresenter.class,
                CredentialsDetailsDialogPresenter.CredentialsDetailsDialogView.class,
                CredentialsDetailsDialogViewImpl.class);

    }

}
