package stroom.visualisation.client.presenter;

import stroom.util.shared.NullSafe;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.event.shared.HasHandlers;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

/**
 * Dialog box used when adding a file or folder to the tree.
 */
public class VisualisationAssetsAddItemDialogPresenter
        extends MyPresenterWidget<VisualisationAssetsAddItemDialogPresenter.VisualisationAssetsAddFolderDialogView>
        implements HasHandlers {

    private String illegalAssetNameCharacters;

    private DialogType dialogType;

    /** Width of dialog */
    private static final int DIALOG_WIDTH = 300;

    /** Height of dialog */
    private static final int DIALOG_HEIGHT = 300;

    /** Type of this dialog - adding Folder or File */
    public enum DialogType {

        FOLDER_DIALOG("folder"),
        FILE_DIALOG("file");

        private final String name;

        DialogType(final String name) {
            this.name = name;
        }

        public String toString() {
            return name;
        }

        public String toTitleCaseString() {
            return name.substring(0, 1).toUpperCase() +
                   name.substring(1);
        }
    }

    /**
     * Injected constructor.
     */
    @SuppressWarnings("unused")
    @Inject
    public VisualisationAssetsAddItemDialogPresenter(final EventBus eventBus,
                                                     final VisualisationAssetsAddFolderDialogView view) {
        super(eventBus, view);
    }

    /**
     * Call to prepare and show the dialog.
     *
     * @param path The path that we're adding the item at.
     */
    public void setupPopup(final ShowPopupEvent.Builder builder,
                           final String path,
                           final String illegalAssetNameCharacters,
                           final DialogType dialogType) {

        this.getView().setPath(path);
        this.dialogType = dialogType;

        builder.popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.resizable(DIALOG_WIDTH, DIALOG_HEIGHT))
                .caption("New " + dialogType.toTitleCaseString())
                .modal(true);
        this.illegalAssetNameCharacters = illegalAssetNameCharacters;
    }

    /**
     * @return true if the form is valid, false if not.
     */
    public boolean isValid() {
        return getValidationErrorMessage() == null;
    }

    /**
     * @return The form validation error message, or null if everything is ok.
     */
    public String getValidationErrorMessage() {
        String retval = null;
        final String folderName = getView().getName();
        if (NullSafe.isBlankString(folderName)) {
            retval = "Please set the name of the "
                     + dialogType
                     + "you wish to create";
        } else {
            if (illegalAssetNameCharacters != null) {
                for (int i = 0; i < illegalAssetNameCharacters.length(); ++i) {
                    final CharSequence subSequence = illegalAssetNameCharacters.subSequence(i, i + 1);
                    if (folderName.contains(subSequence)) {
                        retval = dialogType.toTitleCaseString()
                                 + " names must not contain the character '" + subSequence + "'";
                    }
                }
            }
        }

        return retval;
    }

    public interface VisualisationAssetsAddFolderDialogView extends View {

        /**
         * Sets the path where this asset will be added.
         */
        void setPath(String path);

        /**
         * Gets the file upload widget.
         */
        String getName();

    }

}
