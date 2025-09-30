package stroom.credentials.client.presenter;

import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

/**
 * Dialog to be called from clients when they want to pick an available credential.
 */
public class CredentialsManagerDialogPresenter
extends MyPresenterWidget<CredentialsManagerDialogPresenter.CredentialsManagerDialogView> {

    /** Width of dialog */
    private static final int DIALOG_WIDTH = 700;

    /** Height of dialog */
    private static final int DIALOG_HEIGHT = 700;

    /**
     * Injected constructor.
     */
    @Inject
    public CredentialsManagerDialogPresenter(final EventBus eventBus,
                                             final CredentialsManagerDialogView view,
                                             final CredentialsListPresenter credentialsListPresenter) {
        super(eventBus, view);
        credentialsListPresenter.setParentPresenter(this);
        credentialsListPresenter.setDefaultSelection(false);
        view.setCredentialsList(credentialsListPresenter);
        this.setInSlot(CredentialsListPresenter.CREDENTIALS_LIST, credentialsListPresenter);
    }

    /**
     * Call to prepare the dialog to be shown.
     * @param builder The builder for the dialog.
     * @param credentialsId The UUID of the currently selected credentials. Can be null
     *                      if nothing is selected.
     */
    public void setupDialog(final ShowPopupEvent.Builder builder,
                            final String credentialsId) {

        builder.popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.resizable(DIALOG_WIDTH, DIALOG_HEIGHT))
                .caption("Credentials")
                .modal(true);
        this.getView().setCredentialsId(credentialsId);
    }

    /**
     * @return The selected UUID, or null if nothing is selected.
     */
    public String getCredentialsId() {
        return this.getView().getCredentialsId();
    }

    /**
     * Interface for GWT.
     */
    public interface CredentialsManagerDialogView extends View {

        void setCredentialsList(CredentialsListPresenter credentialsList);

        /**
         * @param uuid The UUID that is currently selected.
         */
        void setCredentialsId(String uuid);

        /**
         * @return The selected UUID.
         */
        String getCredentialsId();

    }
}
