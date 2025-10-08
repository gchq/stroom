package stroom.credentials.client.presenter;

import stroom.credentials.client.presenter.CredentialsDetailsDialogPresenter.CredentialsDetailsDialogView;
import stroom.credentials.shared.Credentials;
import stroom.credentials.shared.CredentialsSecret;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import javax.inject.Inject;

/**
 * Dialog to show and edit the details of one set of Credentials.
 */
public class CredentialsDetailsDialogPresenter
        extends MyPresenterWidget<CredentialsDetailsDialogView> {

    /** Width of dialog */
    private static final int DIALOG_WIDTH = 650;

    /** Height of dialog */
    private static final int DIALOG_HEIGHT = 560;

    /**
     * Constructor. Injected.
     */
    @Inject
    public CredentialsDetailsDialogPresenter(final EventBus eventBus,
                                             final CredentialsDetailsDialogView view) {
        super(eventBus, view);
    }

    /**
     * Call with the builder to set up this dialog before calling .fire().
     * @param credentials; provides information to display to the user.
     * @param builder The builder to show this popup.
     */
    public void setupDialog(final Credentials credentials,
                            final CredentialsSecret secret,
                            final ShowPopupEvent.Builder builder) {

        // Populate the UI
        this.getView().setCredentials(credentials, secret);

        // Configure the popup builder for this dialog
        builder.popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.resizable(DIALOG_WIDTH, DIALOG_HEIGHT))
                .caption("Credentials")
                .modal(true);
    }

    /**
     * Determines if the data entered is valid.
     */
    public boolean isValid() {
        return getView().isValid();
    }

    /**
     * Returns the message to display in the AlertEvent if something
     * isn't valid.
     */
    public String getValidationMessage() {
        return getView().getValidationMessage();
    }

    /**
     * View that this presents. Provides access to the data in the UI.
     */
    public interface CredentialsDetailsDialogView extends View {

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
