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

import stroom.visualisation.client.presenter.assets.VisualisationAssetTreeItem;
import stroom.visualisation.shared.VisualisationAsset;

import com.google.gwt.user.client.ui.Tree;
import com.google.gwt.user.client.ui.TreeItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Utility methods used by VisualisationAssetsPresenter.
 * Separated out as the main class was getting too big.
 */
public class VisualisationAssetsPresenterUtils {

    /** Slash / character */
    private final static String SLASH = "/";

    /**
     * Convert the paths in the VisualisationAssets into the tree model.
     * @param tree The GWT tree to add paths to. Must not be null.
     * @param assets The list of assets from the server. Might be null.
     */
    static void addPathsToTree(final Tree tree,
                               final List<VisualisationAsset> assets) {
        if (assets != null) {
            // Convert list of paths into a tree
            for (final VisualisationAsset asset : assets) {
                String path = asset.getPath();
                // Ignore leading slash
                if (path.startsWith("/")) {
                    path = path.substring(1);
                }
                final String[] pathItems = path.split(SLASH);
                TreeItem treeItem = null;

                for (int iPath = 0; iPath < pathItems.length; ++iPath) {
                    final String pathItem = pathItems[iPath];
                    final boolean isLast = iPath == pathItems.length - 1;

                    // Search for anything existing that matches this pathItem
                    VisualisationAssetTreeItem existingTreeItem = null;
                    if (treeItem == null) {
                        for (int iChild = 0; iChild < tree.getItemCount(); ++iChild) {
                            final VisualisationAssetTreeItem item = (VisualisationAssetTreeItem) tree.getItem(iChild);
                            if (pathItem.equals(item.getLabel())) {
                                existingTreeItem = item;
                                break;
                            }
                        }
                    } else {
                        for (int iChild = 0; iChild < treeItem.getChildCount(); ++iChild) {
                            final VisualisationAssetTreeItem item =
                                    (VisualisationAssetTreeItem) treeItem.getChild(iChild);
                            if (pathItem.equals(item.getLabel())) {
                                existingTreeItem = item;
                                break;
                            }
                        }
                    }

                    final TreeItem newChildItem;
                    if (existingTreeItem == null) {
                        if (isLast) {
                            // Last item so set whether it is a folder or not
                            // The last item takes the ID of the asset too.
                            newChildItem = VisualisationAssetTreeItem.createItemFromAsset(asset, pathItem);
                        } else {
                            // Not last item so must be a folder
                            // Its ID isn't important at this stage so it gets a new ID
                            newChildItem = VisualisationAssetTreeItem.createNewFolderItem(pathItem);
                        }
                        if (treeItem == null) {
                            tree.addItem(newChildItem);
                        } else {
                            treeItem.addItem(newChildItem);
                        }
                    } else {
                        newChildItem = existingTreeItem;
                    }

                    treeItem = newChildItem;
                }
            }
            sortTree(tree);
        }

    }

    /**
     * Stores the state of the tree in the variable treeItemPathToOpenState.
     */
    static void storeOpenClosedState(final Tree tree, final Set<String> treeItemPathToOpenState) {
        for (int i = 0; i < tree.getItemCount(); ++i) {
            final VisualisationAssetTreeItem treeItem = (VisualisationAssetTreeItem) tree.getItem(i);
            recurseStoreOpenClosedState(treeItem, treeItemPathToOpenState);
        }
    }

    /**
     * Recurses down the TreeItems, storing the open/closed state in treeItemPathToOpenState.
     * @param assetTreeItem The item to recurse.
     */
    private static void recurseStoreOpenClosedState(final VisualisationAssetTreeItem assetTreeItem,
                                                    final Set<String> treeItemPathToOpenState) {
        if (assetTreeItem.getState()) {
            // Item is open so store its state and everything under it
            treeItemPathToOpenState.add(getItemPath(assetTreeItem));
            for (int i = 0; i < assetTreeItem.getChildCount(); ++i) {
                final VisualisationAssetTreeItem child = (VisualisationAssetTreeItem) assetTreeItem.getChild(i);
                recurseStoreOpenClosedState(child, treeItemPathToOpenState);
            }
        }
    }

    /**
     * Sets the state of the tree from the variable treeItemIdToOpenState.
     */
    static void restoreOpenClosedState(final Tree tree, final Set<String> treeItemPathToOpenState) {
        for (int i = 0; i < tree.getItemCount(); ++i) {
            final VisualisationAssetTreeItem treeItem = (VisualisationAssetTreeItem) tree.getItem(i);
            recurseRestoreOpenClosedState(treeItem, treeItemPathToOpenState);
        }
        treeItemPathToOpenState.clear();
    }

    /**
     * Recurses down the TreeItems, restoring the open/closed state from
     * treeItemPathToOpenState.
     * @param treeItem The item to recurse.
     */
    private static void recurseRestoreOpenClosedState(final VisualisationAssetTreeItem treeItem,
                                                      final Set<String> treeItemPathToOpenState) {
        final String itemPath = getItemPath(treeItem);
        if (treeItemPathToOpenState.contains(itemPath)) {
            treeItem.setState(true);
            for (int i = 0; i < treeItem.getChildCount(); ++i) {
                final VisualisationAssetTreeItem child = (VisualisationAssetTreeItem) treeItem.getChild(i);
                recurseRestoreOpenClosedState(child, treeItemPathToOpenState);
            }
        }
    }

    /**
     * Marks the tree item as open, so it will be opened on the next load.
     * @param treeItem The tree item that needs to be open
     * @param treeItemPathToOpenState Where to store the state
     */
    public static void markOpenClosedStateOpen(final VisualisationAssetTreeItem treeItem,
                                               final Set<String> treeItemPathToOpenState) {
        treeItemPathToOpenState.add(getItemPath(treeItem));
    }

    /**
     * Sorts the whole tree, from root to leaf.
     * @param tree The tree to sort.
     */
    static void sortTree(final Tree tree) {
        recurseSortTree(tree, null);
    }

    /**
     * Called from sortTree() to recurse down the tree, sorting it,
     * or directly if only part of the tree needs sorting.
     * @param tree The tree to sort.
     * @param assetTreeItem The node to sort and recurse. If null then will start at the root of the tree.
     */
    static void recurseSortTree(final Tree tree,
                                final VisualisationAssetTreeItem assetTreeItem) {
        if (assetTreeItem == null) {
            // We're at the root of the tree so look at the tree widget
            final List<VisualisationAssetTreeItem> childItems = new ArrayList<>();
            for (int i = 0; i < tree.getItemCount(); ++i) {
                childItems.add((VisualisationAssetTreeItem) tree.getItem(i));
            }
            childItems.sort(new TreeItemComparator());
            tree.removeItems();
            for (final VisualisationAssetTreeItem treeItem : childItems) {
                if (!treeItem.isLeaf()) {
                    recurseSortTree(tree, treeItem);
                }
                tree.addItem(treeItem);
            }
        } else {
            // Not at the root so look at the item
            final List<VisualisationAssetTreeItem> childItems = new ArrayList<>();
            for (int i = 0; i < assetTreeItem.getChildCount(); ++i) {
                childItems.add((VisualisationAssetTreeItem) assetTreeItem.getChild(i));
            }
            childItems.sort(new TreeItemComparator());
            assetTreeItem.removeItems();
            for (final VisualisationAssetTreeItem childTreeItem : childItems) {
                if (!childTreeItem.isLeaf()) {
                    recurseSortTree(tree, childTreeItem);
                }
                assetTreeItem.addItem(childTreeItem);
            }
        }
    }

    /**
     * Returns the path to the given item.
     * @param item The item to find the path to. Can be null if this is the root path.
     * @return The path as a String, with / separators. Never returns null.
     */
    static String getItemPath(final VisualisationAssetTreeItem item) {
        final List<String> pathList = new ArrayList<>();
        VisualisationAssetTreeItem currentItem = item;
        while (currentItem != null) {
            pathList.add(currentItem.getLabel());
            currentItem = (VisualisationAssetTreeItem) currentItem.getParentItem();
        }
        Collections.reverse(pathList);

        return SLASH + String.join(SLASH, pathList);
    }

    /**
     * Returns a path to a new item in the tree.
     * @param parent Where the new item is going
     * @param newItemLabel The label of the new item
     * @return A string for the path to the new item.
     */
    static String getNewItemPath(final VisualisationAssetTreeItem parent, final String newItemLabel) {
        final String parentPath = getItemPath(parent);
        if (parentPath.equals(SLASH)) {
            return newItemLabel;
        } else {
            return parentPath + SLASH + newItemLabel;
        }
    }

    /**
     * Generates a potentially non-classing label for an asset.
     */
    static String generateNonClashingLabel(final String label, final int i) {
        final int iDot = label.lastIndexOf('.');
        String namePart = label;
        String extPart = "";
        if (iDot != -1) {
            namePart = label.substring(0, iDot);
            extPart = label.substring(iDot);
        }

        return namePart + "-" + i + extPart;
    }

    /**
     * Returns true if the given label clashes in the tree root.
     * @param itemLabel The label to test.
     * @param itemId The ID of the item with the label. Ensures the label doesn't clash with itself.
     * @return true if there is a clash, false if not.
     */
    static boolean labelClashesInTreeRoot(final Tree tree, final String itemLabel, final String itemId) {
        for (int i = 0; i < tree.getItemCount(); ++i) {
            final VisualisationAssetTreeItem assetTreeItem = (VisualisationAssetTreeItem) tree.getItem(i);
            if (!Objects.equals(assetTreeItem.getId(), itemId)
                && Objects.equals(assetTreeItem.getLabel(), itemLabel)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Finds the treeItem that we're going to add things to. Either the selected folder,
     * or the parent of the selected file (can't add things to a file).
     * @return The node that we're going to add things to. Null if we're adding to the root item.
     */
    static VisualisationAssetTreeItem findFolderForSelectedItem(final VisualisationAssetTreeItem selectedTreeItem) {
        if (selectedTreeItem != null) {
            if (selectedTreeItem.isLeaf()) {
                // File so we want the parent folder
                return (VisualisationAssetTreeItem) selectedTreeItem.getParentItem();
            } else {
                // Folder selected so return it
                return selectedTreeItem;
            }
        }

        return null;
    }

    /**
     * Comparator for sorting tree items.
     */
    private static class TreeItemComparator implements Comparator<VisualisationAssetTreeItem> {

        @Override
        public int compare(final VisualisationAssetTreeItem treeItem1, final VisualisationAssetTreeItem treeItem2) {
            if (!treeItem1.isLeaf() && treeItem2.isLeaf()) {
                // 1 is folder, 2 is file so 1 comes first
                return -1;
            } else if (treeItem1.isLeaf() && !treeItem2.isLeaf()) {
                // 1 is file, 2 is folder so 2 comes first
                return 1;
            } else {
                // Sort on label
                return treeItem1.getLabel().compareTo(treeItem2.getLabel());
            }
        }
    }

}
