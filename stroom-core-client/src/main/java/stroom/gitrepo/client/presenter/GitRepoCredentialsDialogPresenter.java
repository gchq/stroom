package stroom.gitrepo.client.presenter;

import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.UiHandlers;
import com.gwtplatform.mvp.client.View;

import java.util.Objects;

/**
 * Asks for credentials - called from the GitRepo Settings UI.
 */
public class GitRepoCredentialsDialogPresenter
        extends MyPresenterWidget<GitRepoCredentialsDialogPresenter.GitRepoCredentialsDialogView> {

    /**
     * Width of dialog
     */
    private static final int DIALOG_WIDTH = 300;

    /**
     * Height of dialog
     */
    private static final int DIALOG_HEIGHT = 300;

    /**
     * Constructor. Injected.
     */
    @Inject
    public GitRepoCredentialsDialogPresenter(final EventBus eventBus,
                                             final GitRepoCredentialsDialogView view) {
        super(eventBus, view);
    }

    /**
     * Call with the builder to set up this dialog before calling .fire().
     * @param username The initial value of the username. Can be null
     *                 in which case the empty string will be used.
     * @param password The initial value of the password. Can be
     *                 null in which case the empty string will be used.
     * @param builder The builder to show this popup. Must not be null.
     */
    public void setupDialog(final String username,
                            final String password,
                            final ShowPopupEvent.Builder builder) {
        Objects.requireNonNull(builder);

        // Get rid of any existing data
        this.getView().resetData();

        this.getView().setUsername(username == null ? "" : username);
        this.getView().setPassword(password == null ? "" : password);

        // Configure the popup builder for this dialog
        builder.popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.resizable(DIALOG_WIDTH, DIALOG_HEIGHT))
                .caption("Credentials")
                .modal(true);
    }

    /**
     * Determines if the data entered is valid.
     * We allow empty username and password.
     */
    public boolean isValid() {
        return true;
    }

    /**
     * Returns the message to display in the AlertEvent if something
     * isn't valid.
     */
    public String getValidationMessage() {
        return "";
    }

    /**
     * Interface for UI handlers.
     */
    public interface GitRepoCredentialsDialogUiHandlers extends UiHandlers {
        // No code
    }

    /**
     * Interface for the View.
     */
    public interface GitRepoCredentialsDialogView extends View {

        /**
         * Sets the username in the UI.
         */
        void setUsername(String username);

        /**
         * @return The username entered by the user.
         */
        String getUsername();

        /**
         * Sets the password in the UI.
         */
        void setPassword(String password);

        /**
         * @return The password entered by the user.
         */
        String getPassword();

        /**
         * Resets all dialog information for the next use.
         */
        void resetData();

    }

}
