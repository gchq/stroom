package stroom.gitrepo.client.presenter;

import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import javax.inject.Inject;

/**
 * Dialog to ask users for Commit message when pushing a commit to Git.
 */
public class GitRepoCommitDialogPresenter
    extends MyPresenterWidget<GitRepoCommitDialogPresenter.GitRepoCommitDialogView> {

    /** Width of dialog */
    private static final int DIALOG_WIDTH = 400;

    /** Height of dialog */
    private static final int DIALOG_HEIGHT = 400;

    /**
     * Constructor. Injected.
     */
    @Inject
    public GitRepoCommitDialogPresenter(final EventBus eventBus,
                                        final GitRepoCommitDialogView view) {
        super(eventBus, view);
    }

    /**
     * Prepares the dialog for display.
     * @param builder The builder, which will have values set to suit this dialog.
     */
    public void setupDialog(final ShowPopupEvent.Builder builder) {

        // Get rid of any existing data
        this.getView().resetData();

        // Configure the builder
        builder.popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.resizable(DIALOG_WIDTH, DIALOG_HEIGHT))
                .caption("Git Push")
                .modal(true);
    }

    /**
     * @return Whether the fields are valid.
     */
    public boolean isValid() {
        return !this.getView().getCommitMessage().isEmpty();
    }

    /**
     * @return A message to display if a field isn't valid.
     */
    public String getValidationMessage() {
        if (this.getView().getCommitMessage().isEmpty()) {
            return "Please enter a commit message";
        } else {
            return "";
        }
    }

    /**
     * View that this presents. Provides access to the data in the UI.
     */
    public interface GitRepoCommitDialogView extends View {

        /**
         * Returns the commit message.
         */
        String getCommitMessage();

        /**
         * Resets all dialog information for the next use.
         */
        void resetData();

    }
}
