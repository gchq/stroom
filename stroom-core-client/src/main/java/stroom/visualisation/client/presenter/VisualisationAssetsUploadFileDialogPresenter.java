package stroom.visualisation.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.importexport.client.presenter.ImportUtil;
import stroom.util.shared.NullSafe;
import stroom.visualisation.client.presenter.VisualisationAssetsUploadFileDialogPresenter.VisualisationAssetsUploadFileDialogView;
import stroom.visualisation.client.presenter.assets.VisualisationAssetTreeItem;
import stroom.widget.form.client.CustomFileUpload;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.event.shared.HasHandlers;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

public class VisualisationAssetsUploadFileDialogPresenter
        extends MyPresenterWidget<VisualisationAssetsUploadFileDialogView>
        implements HasHandlers {

    /**
     * Allows us to stop the hide request for the dialog
     */
    private HidePopupRequestEvent currentHideRequest;

    /**
     * What to call when the upload has been successful
     */
    private VisualisationAssetsAddFileCallback addFileCallback;

    /**
     * Where this file is being added
     */
    private VisualisationAssetTreeItem parentFolderItem;

    /**
     * Characters that are illegal in filenames
     */
    private String illegalAssetNameCharacters;

    /**
     * Width of dialog
     */
    private static final int DIALOG_WIDTH = 430;

    /**
     * Height of dialog
     */
    private static final int DIALOG_HEIGHT = 300;

    /**
     * Injected constructor.
     */
    @SuppressWarnings("unused")
    @Inject
    public VisualisationAssetsUploadFileDialogPresenter(final EventBus eventBus,
                                                        final VisualisationAssetsUploadFileDialogView view) {
        super(eventBus, view);

        view.getFileUpload().setAction(ImportUtil.getImportFileURL());
        view.getFileUpload()
                .onSuccess(resourceKey -> {
                    final String fileName =
                            addFileCallback.getNonClashingLabel(
                                    parentFolderItem,
                                    parseFakeFilename(getView().getFileUpload().getFilename()),
                                    null);

                    addFileCallback.addUploadedFile(parentFolderItem, fileName, resourceKey);
                    currentHideRequest.hide();
                })
                .onFailure(message -> AlertEvent.fireError(VisualisationAssetsUploadFileDialogPresenter.this,
                        message,
                        currentHideRequest::reset))
                .taskMonitorFactory(this, "Uploading file");
    }

    /**
     * Call to prepare and show the dialog.
     *
     * @param addFileCallback            Something to call when the file has been uploaded
     * @param parentFolderItem           Where the item is going to be added in the tree. Can be null if adding at the root.
     * @param path                       The path that we're adding the item at.
     * @param illegalAssetNameCharacters Characters that we don't accept in the filename.
     */
    public void fireShowPopup(final VisualisationAssetsAddFileCallback addFileCallback,
                              final VisualisationAssetTreeItem parentFolderItem,
                              final String path,
                              final String illegalAssetNameCharacters) {

        this.getView().setPath(path);
        this.parentFolderItem = parentFolderItem;
        this.addFileCallback = addFileCallback;
        this.illegalAssetNameCharacters = illegalAssetNameCharacters;
        this.currentHideRequest = null;

        final ShowPopupEvent.Builder builder = ShowPopupEvent.builder(this);
        builder.popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.resizable(DIALOG_WIDTH, DIALOG_HEIGHT))
                .caption("Add File")
                .modal(true)
                .onHideRequest(e -> {
                    currentHideRequest = e;
                    if (e.isOk()) {
                        if (checkValid()) {
                            // Upload the selected file via XHR (attaches the X-CSRF header).
                            getView().getFileUpload().submit();
                        } else {
                            e.reset();
                        }
                    } else {
                        e.hide();
                    }
                }).fire();
    }

    /**
     * Checks if the data in the dialog is valid. If not shows a warning dialog and returns false.
     *
     * @return true if everything is ok. False if not ok.
     */
    public boolean checkValid() {
        final String filename = getView().getFileUpload().getFilename();
        if (NullSafe.isBlankString(filename)) {
            AlertEvent.fireWarn(this, "File not set", null);
            return false;
        }

        return true;
    }

    /**
     * Removes any paths from the filename returned by the browser.
     * Chrome returns a path like C:\fakepath\actual-filename.ext on Linux.
     * Not sure about other browsers.
     * <p>
     * Also removes any illegal characters, deleting them from the filename.
     * </p>
     *
     * @param fakeFilename The filename given by the browser.
     * @return The filename part of the path.
     */
    private String parseFakeFilename(final String fakeFilename) {
        String filename = fakeFilename;

        final int iSlash = fakeFilename.lastIndexOf('\\');
        if ((iSlash != -1) && (iSlash + 1 < fakeFilename.length())) {
            filename = fakeFilename.substring(iSlash + 1);
        }

        // Strip out any illegal characters
        if (illegalAssetNameCharacters != null) {
            for (int i = 0; i < illegalAssetNameCharacters.length(); ++i) {
                final CharSequence c = illegalAssetNameCharacters.subSequence(i, i + 1);
                filename = filename.replace(c, "");
            }
        }

        return filename;
    }

    public interface VisualisationAssetsUploadFileDialogView extends View {

        /**
         * Sets the path where this asset will be added.
         */
        void setPath(String path);

        /**
         * Gets the file upload widget.
         */
        CustomFileUpload getFileUpload();
    }
}
