/*
 * Copyright 2016-2026 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.visualisation.client.presenter;

import stroom.util.shared.NullSafe;
import stroom.visualisation.client.presenter.assets.VisualisationAssetTreeItem;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.event.shared.HasHandlers;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

public class VisualisationAssetsEditAssetDialogPresenter
        extends MyPresenterWidget<VisualisationAssetsEditAssetDialogPresenter.VisualisationAssetsEditAssetDialogView>
        implements HasHandlers {

    /** Characters that are illegal in filenames */
    private String illegalAssetNameCharacters;

    /** Width of dialog */
    private static final int DIALOG_WIDTH = 300;

    /** Height of dialog */
    private static final int DIALOG_HEIGHT = 300;

    /**
     * Injected constructor.
     */
    @SuppressWarnings("unused")
    @Inject
    public VisualisationAssetsEditAssetDialogPresenter(final EventBus eventBus,
                                                       final VisualisationAssetsEditAssetDialogView view) {
        super(eventBus, view);
    }

    /**
     * Call to prepare and show the dialog.
     *
     * @param assetTreeItem The tree item that we're editing.
     */
    public void setupPopup(final ShowPopupEvent.Builder builder,
                           final VisualisationAssetTreeItem assetTreeItem,
                           final String illegalAssetNameCharacters) {

        this.getView().setTreeItem(assetTreeItem);

        builder.popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.resizable(DIALOG_WIDTH, DIALOG_HEIGHT))
                .caption("Edit")
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

        final String text = getView().getText();
        final boolean isLeaf = getView().isLeaf();

        if (NullSafe.isBlankString(text)) {
            if (isLeaf) {
                retval = "Please set the name of the file";
            } else {
                retval = "Please set the name of the folder";
            }
        } else {
            for (int i = 0; i < illegalAssetNameCharacters.length(); ++i) {
                final CharSequence cs = illegalAssetNameCharacters.subSequence(i, i + 1);
                if (text.contains(cs)) {
                    retval = "The name must not contain the character '" + cs + "'";
                }
            }
        }

        return retval;
    }

    public interface VisualisationAssetsEditAssetDialogView extends View {

        /**
         * Sets the tree item for editing.
         */
        void setTreeItem(VisualisationAssetTreeItem assetTreeItem);

        /**
         * @return The text set in the UI.
         */
        String getText();

        /**
         * @return Whether a file (true) or a folder (false) is being edited.
         */
        boolean isLeaf();

        /**
         * @return The ID of the asset being edited.
         */
        String getId();

    }
}
