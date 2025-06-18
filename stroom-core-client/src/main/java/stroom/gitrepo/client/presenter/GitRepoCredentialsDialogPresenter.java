package stroom.gitrepo.client.presenter;

import stroom.gitrepo.shared.GitRepoDoc;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.UiHandlers;
import com.gwtplatform.mvp.client.View;

public class GitRepoCredentialsDialogPresenter
        extends MyPresenterWidget<GitRepoCredentialsDialogPresenter.GitRepoCredentialsDialogView> {

    /**
     * Width of dialog
     */
    private final static int DIALOG_WIDTH = 400;

    /**
     * Height of dialog
     */
    private final static int DIALOG_HEIGHT = 400;

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
     * @param gitRepoDoc The doc - source of the credentials
     * @param builder The builder to show this popup.
     */
    public void setupDialog(final GitRepoDoc gitRepoDoc,
                            final ShowPopupEvent.Builder builder) {
        // Get rid of any existing data
        this.getView().resetData();

        this.getView().setUsername(gitRepoDoc.getUsername());
        this.getView().setPassword(gitRepoDoc.getPassword());

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
        return !this.getView().getUsername().trim().isEmpty()
               && !this.getView().getPassword().isEmpty();
    }

    /**
     * Returns the message to display in the AlertEvent if something
     * isn't valid.
     */
    public String getValidationMessage() {
        if (this.getView().getUsername().trim().isEmpty()) {
            return "Username must not be empty";
        } else if (this.getView().getPassword().isEmpty()) {
            return "Password must not be empty";
        } else {
            return "";
        }
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