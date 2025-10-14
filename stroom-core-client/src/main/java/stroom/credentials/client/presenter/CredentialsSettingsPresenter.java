package stroom.credentials.client.presenter;

import stroom.credentials.client.presenter.CredentialsSettingsPresenter.CredentialsSettingsView;
import stroom.credentials.shared.Credentials;
import stroom.credentials.shared.CredentialsSecret;

import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import javax.inject.Inject;

/**
 * Dialog to show and edit the details of one set of Credentials.
 */
public class CredentialsSettingsPresenter
        extends MyPresenterWidget<CredentialsSettingsView> {

    /**
     * Constructor. Injected.
     */
    @Inject
    public CredentialsSettingsPresenter(final EventBus eventBus,
                                        final CredentialsSettingsView view) {
        super(eventBus, view);
    }

    /**
     * View that this presents. Provides access to the data in the UI.
     */
    public interface CredentialsSettingsView extends View {

        /**
         * @return The credentials object holding credentials metadata.
         */
        Credentials getCredentials();

        /**
         * @return The secrets associated with the credentials.
         */
        CredentialsSecret getSecret();

        /**
         * Sets the credentials displayed by this dialog.
         * @param credentials The credentials meta-data.
         * @param secret The secret stuff.
         */
        void setCredentials(Credentials credentials, CredentialsSecret secret);

        /**
         * @return true if the data in the dialog is valid.
         */
        boolean isValid();

        /**
         * @return A message to display to the user if isValid() == false.
         */
        String getValidationMessage();

    }
}
