/*
 * Copyright 2026 Crown Copyright
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

package stroom.document.asset.client.component;

import stroom.document.asset.shared.DocumentAsset;
import stroom.svg.shared.SvgImage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/// An item in the asset tree ([DocumentAssetTree]): a file (a leaf) or a folder, with its children
/// and whether it is open. Changing an item that is in a tree redraws the tree.
public class DocumentAssetTreeItem {

    // The icon of a file whose extension isn't recognised
    private static final SvgImage DEFAULT_ICON = SvgImage.FILE;

    private static final SvgImage FOLDER_ICON = SvgImage.FOLDER;

    // Each recognised filename extension's icon
    private static final Map<String, SvgImage> ICONS = new HashMap<>();

    static {
        ICONS.put("png", SvgImage.FILE_IMAGE);
        ICONS.put("jpg", SvgImage.FILE_IMAGE);
        ICONS.put("gif", SvgImage.FILE_IMAGE);
        ICONS.put("webp", SvgImage.FILE_IMAGE);
        ICONS.put("svg", SvgImage.FILE_IMAGE);
        ICONS.put("css", SvgImage.FILE_RAW);
        ICONS.put("htm", SvgImage.FILE_FORMATTED);
        ICONS.put("html", SvgImage.FILE_FORMATTED);
        ICONS.put("js", SvgImage.FILE_SCRIPT);
    }

    private final String id;
    private final boolean isLeaf;
    private final String label;
    private final SvgImage icon;
    private final List<DocumentAssetTreeItem> children = new ArrayList<>();

    private DocumentAssetTreeItem parent;
    // The tree this item is in, if any, told when the item changes
    private DocumentAssetTree tree;
    private boolean open;

    private DocumentAssetTreeItem(final String id,
                                  final String label,
                                  final boolean isLeaf) {
        this.id = id;
        this.isLeaf = isLeaf;
        this.label = label;
        this.icon = iconOf(label, isLeaf);
    }

    /// Creates a folder item with a new ID.
    ///
    /// @param text The name of the folder.
    /// @return The folder item.
    public static DocumentAssetTreeItem createNewFolderItem(final String text) {
        return new DocumentAssetTreeItem(UUID.randomUUID().toString(), text, false);
    }

    /// Creates an item for an asset from the server, a file or a folder, with the asset's ID.
    ///
    /// @param asset The asset that the item is for.
    /// @param text  The item's label, the last part of the asset's path.
    /// @return The item.
    public static DocumentAssetTreeItem createItemFromAsset(final DocumentAsset asset,
                                                            final String text) {
        return new DocumentAssetTreeItem(asset.getId(), text, !asset.isFolder());
    }

    // A folder's icon, or a file's from its extension
    private static SvgImage iconOf(final String label, final boolean isLeaf) {
        if (!isLeaf) {
            return FOLDER_ICON;
        }
        final int dotIndex = label.lastIndexOf('.');
        if (dotIndex == -1) {
            return DEFAULT_ICON;
        }
        final String extension = label.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
        return Objects.requireNonNullElse(ICONS.get(extension), DEFAULT_ICON);
    }

    /// @return The ID of the asset (or the folder) that this item is for.
    public String getId() {
        return id;
    }

    /// @return Whether this is a file (a leaf) rather than a folder.
    public boolean isLeaf() {
        return isLeaf;
    }

    /// @return The item's name, the last part of its path.
    public String getLabel() {
        return label;
    }

    /// @return The icon drawn beside the item's name.
    public SvgImage getIcon() {
        return icon;
    }

    /// @return The number of the item's children, none for a file.
    public int getChildCount() {
        return children.size();
    }

    /// @param index The child's index.
    /// @return The item's child at the index.
    public DocumentAssetTreeItem getChild(final int index) {
        return children.get(index);
    }

    /// @return The folder this item is in, or null if it is at the top of the tree (or in no tree).
    public DocumentAssetTreeItem getParentItem() {
        return parent;
    }

    /// Adds a child to the end of this item's children, taking it from wherever it was. A file can't
    /// have children, so this does nothing to one.
    ///
    /// @param item The child.
    public void addItem(final DocumentAssetTreeItem item) {
        if (!isLeaf) {
            item.detach();
            item.parent = this;
            item.setTree(tree);
            children.add(item);
            changed();
        }
    }

    /// Removes all this item's children.
    public void removeItems() {
        for (final DocumentAssetTreeItem child : children) {
            child.parent = null;
            child.setTree(null);
        }
        children.clear();
        changed();
    }

    /// @return Whether the item is open, showing its children.
    public boolean getState() {
        return open;
    }

    /// Opens or closes the item, showing or hiding its children.
    ///
    /// @param open Whether the item is open.
    public void setState(final boolean open) {
        if (this.open != open) {
            this.open = open;
            changed();
        }
    }

    /// Checks whether a name is taken by one of this folder's children.
    ///
    /// @param itemText The name to check.
    /// @param itemId   The ID of the item with the name, so it doesn't match itself, or null.
    /// @return Whether a child other than the one with the ID has the name.
    public boolean labelExists(final String itemText, final String itemId) {
        for (final DocumentAssetTreeItem child : children) {
            if (!Objects.equals(child.getId(), itemId)
                && Objects.equals(itemText, child.getLabel())) {
                return true;
            }
        }
        return false;
    }

    /// @return The item's depth in its tree, 0 at the top.
    int getDepth() {
        int depth = 0;
        DocumentAssetTreeItem ancestor = parent;
        while (ancestor != null) {
            depth++;
            ancestor = ancestor.parent;
        }
        return depth;
    }

    /// @return The tree that the item is in, or null.
    DocumentAssetTree getTree() {
        return tree;
    }

    /// Puts the item and its children in a tree, or in none.
    ///
    /// @param tree The tree, or null.
    void setTree(final DocumentAssetTree tree) {
        this.tree = tree;
        for (final DocumentAssetTreeItem child : children) {
            child.setTree(tree);
        }
    }

    /// Takes the item from its parent folder, or from the top of its tree.
    void detach() {
        if (parent != null) {
            parent.children.remove(this);
            parent.changed();
            parent = null;
        } else if (tree != null) {
            tree.removeItem(this);
        }
        setTree(null);
    }

    private void changed() {
        if (tree != null) {
            tree.onItemsChanged();
        }
    }

    @Override
    public String toString() {
        return label;
    }
}
