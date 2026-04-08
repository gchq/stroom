package stroom.visualisation.client.presenter;

import stroom.util.shared.ResourceKey;
import stroom.visualisation.client.presenter.assets.VisualisationAssetTreeItem;

/**
 * Callback interface, so that the Add File dialog can record what it has done back
 * into the VisualisationAssetsAddDialogPresenter.
 */
public interface VisualisationAssetsAddFileCallback {

    /**
     * Method for the Add File dialog to call when it has uploaded a file.
     * @param parentFolderItem The node that the file has been added to.
     * @param fileName The name of the file that was uploaded.
     * @param resourceKey The resource key of the file, so that the server can find it later.
     */
    void addUploadedFile(VisualisationAssetTreeItem parentFolderItem,
                         String fileName,
                         ResourceKey resourceKey);

    /**
     * Generates a label that doesn't clash with other files/folders in the same directory.
     * Adds an integer to the end, incrementing until an integer is found that doesn't
     * clash with anything else.
     * @param parentItem The node that holds the directory. Can be null
     * @param itemLabel The label that we're trying to put into the directory.
     * @param itemId The ID of the item owning the label, so we don't signal a clash with ourselves.
     *               Can be null if this is a new item with no ID yet.
     * @return A label that doesn't clash with anything else.
     */
    String getNonClashingLabel(VisualisationAssetTreeItem parentItem, String itemLabel, String itemId);

}
