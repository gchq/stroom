package stroom.visualisation.client.view;

import stroom.visualisation.client.presenter.VisualisationAssetsAddItemDialogPresenter.VisualisationAssetsAddFolderDialogView;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewImpl;

/**
 * View for the dialog that uploads files into the Visualisation web asset manager.
 */
public class VisualisationAssetsAddItemDialogViewImpl extends ViewImpl implements
        VisualisationAssetsAddFolderDialogView {

    /** GWT widget */
    private final Widget widget;

    @UiField
    TextBox txtName;

    @UiField
    Label lblPath;

    /**
     * Injected constructor.
     */
    @Inject
    @SuppressWarnings("unused")
    public VisualisationAssetsAddItemDialogViewImpl(final Binder binder) {
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
     * Gets the name entered for the folder.
     */
    @Override
    public String getName() {
        return txtName.getText();
    }

    /**
     * Interface to keep GWT UiBinder happy.
     */
    public interface Binder extends UiBinder<Widget, VisualisationAssetsAddItemDialogViewImpl> {
        // No code
    }
}
