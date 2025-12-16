package stroom.credentials.client.gin;

import stroom.core.client.gin.PluginModule;
import stroom.credentials.client.CredentialsPlugin;
import stroom.credentials.client.presenter.AccessTokenSecretPresenter;
import stroom.credentials.client.presenter.AccessTokenSecretPresenter.AccessTokenSecretView;
import stroom.credentials.client.presenter.CredentialsManagerDialogPresenter;
import stroom.credentials.client.presenter.CredentialsPresenter;
import stroom.credentials.client.presenter.CredentialsPresenter.CredentialsView;
import stroom.credentials.client.presenter.CredentialSettingsPresenter;
import stroom.credentials.client.presenter.CredentialSettingsPresenter.CredentialSettingsView;
import stroom.credentials.client.presenter.KeyPairSecretPresenter;
import stroom.credentials.client.presenter.KeyPairSecretPresenter.KeyPairSecretView;
import stroom.credentials.client.presenter.KeyStoreSecretPresenter;
import stroom.credentials.client.presenter.KeyStoreSecretPresenter.KeyStoreSecretView;
import stroom.credentials.client.presenter.UsernamePasswordSecretPresenter;
import stroom.credentials.client.presenter.UsernamePasswordSecretPresenter.UsernamePasswordSecretView;
import stroom.credentials.client.view.AccessTokenSecretViewImpl;
import stroom.credentials.client.view.CredentialsManagerViewImpl;
import stroom.credentials.client.view.CredentialsViewImpl;
import stroom.credentials.client.view.CredentialSettingsViewImpl;
import stroom.credentials.client.view.KeyPairSecretViewImpl;
import stroom.credentials.client.view.KeyStoreSecretViewImpl;
import stroom.credentials.client.view.UsernamePasswordSecretViewImpl;

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

        bindPresenterWidget(CredentialSettingsPresenter.class,
                CredentialSettingsView.class,
                CredentialSettingsViewImpl.class);

        bindPresenterWidget(CredentialsManagerDialogPresenter.class,
                CredentialsManagerDialogPresenter.CredentialsManagerDialogView.class,
                CredentialsManagerViewImpl.class);

        bindPresenterWidget(AccessTokenSecretPresenter.class,
                AccessTokenSecretView.class,
                AccessTokenSecretViewImpl.class);
        bindPresenterWidget(KeyPairSecretPresenter.class,
                KeyPairSecretView.class,
                KeyPairSecretViewImpl.class);
        bindPresenterWidget(KeyStoreSecretPresenter.class,
                KeyStoreSecretView.class,
                KeyStoreSecretViewImpl.class);
        bindPresenterWidget(UsernamePasswordSecretPresenter.class,
                UsernamePasswordSecretView.class,
                UsernamePasswordSecretViewImpl.class);
    }
}
