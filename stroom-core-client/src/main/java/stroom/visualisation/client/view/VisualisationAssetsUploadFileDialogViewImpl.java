package stroom.visualisation.client.view;

import stroom.visualisation.client.presenter.VisualisationAssetsUploadFileDialogPresenter.VisualisationAssetsUploadFileDialogView;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.FileUpload;
import com.google.gwt.user.client.ui.FormPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewImpl;

/**
 * View for the dialog that uploads files into the Visualisation web asset manager.
 */
public class VisualisationAssetsUploadFileDialogViewImpl extends ViewImpl implements
        VisualisationAssetsUploadFileDialogView {

    /** GWT widget */
    private final Widget widget;

    @UiField
    FormPanel form;

    @UiField
    FileUpload fileUpload;

    @UiField
    Label lblPath;

    /**
     * Injected constructor.
     */
    @Inject
    @SuppressWarnings("unused")
    public VisualisationAssetsUploadFileDialogViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    /**
     * Sets the path where this file will be placed. Provided as an aid for the
     * user so they know where stuff will go.
     * @param path The path to display to the user.
     */
    @Override
    public void setPath(final String path) {
        lblPath.setText(path);
    }

    /**
     * Gets the file upload widget.
     */
    @Override
    public FileUpload getFileUpload() {
        return fileUpload;
    }

    /**
     * Gets the panel that the file upload widget is in.
     */
    public FormPanel getForm() {
        return form;
    }

    /**
     * Interface to keep GWT UiBinder happy.
     */
    public interface Binder extends UiBinder<Widget, VisualisationAssetsUploadFileDialogViewImpl> {
        // No code
    }
}
