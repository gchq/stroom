package stroom.visualisation.client.presenter.assets;

import stroom.svg.shared.SvgImage;
import stroom.visualisation.shared.VisualisationAsset;

import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.client.ui.TreeItem;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents an item in the asset tree.
 */
public class VisualisationAssetTreeItem extends TreeItem {

    /** Additional style to use when nothing else matches */
    private static final SvgImage DEFAULT_ICON = SvgImage.FILE;

    /** Additional style to use when this is a folder */
    private static final SvgImage FOLDER_ICON = SvgImage.FOLDER;

    /** Map of extension to CSS class name */
    private static final Map<String, SvgImage> ICONS = new HashMap<>();

    /** The unique ID for this node */
    private final String id;

    /** Whether this is a leaf (file) or not (folder / directory) */
    private final boolean isLeaf;

    /** The label of this item. Use this instead of getText()! */
    private final String label;

    /*
     * Initialise the map of extension to CSS class name. The class name determines the icon.
     */
    static {
        ICONS.put("png",  SvgImage.FILE_IMAGE);
        ICONS.put("jpg",  SvgImage.FILE_IMAGE);
        ICONS.put("gif",  SvgImage.FILE_IMAGE);
        ICONS.put("webp", SvgImage.FILE_IMAGE);
        ICONS.put("svg",  SvgImage.FILE_IMAGE);
        ICONS.put("css",  SvgImage.FILE_RAW);
        ICONS.put("htm",  SvgImage.FILE_FORMATTED);
        ICONS.put("html", SvgImage.FILE_FORMATTED);
        ICONS.put("js",   SvgImage.FILE_SCRIPT);
    }

    /**
     * Returns a tree node that is for use as a Folder.
     * This node will have a new UUID.
     * @param text The name of the folder.
     */
    public static VisualisationAssetTreeItem createNewFolderItem(final String text) {
        return new VisualisationAssetTreeItem(UUID.randomUUID().toString(), text, false);
    }

    /**
     * Returns a tree node that was created from the VisualisationAsset sent from
     * the server. Can create a folder or a file. Either way, the ID will be that of the asset.
     * @param asset The asset that this represents.
     * @param text The label associated with the asset.
     */
    public static VisualisationAssetTreeItem createItemFromAsset(final VisualisationAsset asset,
                                                                 final String text) {
        return new VisualisationAssetTreeItem(asset.getId(), text, !asset.isFolder());
    }

    private VisualisationAssetTreeItem(final String id,
                                       final String text,
                                       final boolean isLeaf) {

        this.id = id;
        this.isLeaf = isLeaf;
        this.label = text;
        setState(false);

        SvgImage icon;
        if (isLeaf) {
            final int dotIndex = text.lastIndexOf('.');
            if (dotIndex != -1) {
                // Got an extension - look it up
                final String extension = text.substring(dotIndex + 1);
                icon = ICONS.get(extension);
                if (icon == null) {
                    // Default - extension not recognised
                    icon = DEFAULT_ICON;
                }
            } else {
                // No extension - use default
                icon = DEFAULT_ICON;
            }
        } else {
            // Not a leaf so is a folder
            icon = FOLDER_ICON;
        }

        final SafeHtmlBuilder builder = new SafeHtmlBuilder();
        builder.append(SafeHtmlUtils.fromTrustedString(icon.getSvg()));
        builder.appendEscaped(text);
        super.setHTML(builder.toSafeHtml());
    }

    /**
     * Returns the ID associated with this tree node.
     */
    public String getId() {
        return id;
    }

    /**
     * Returns the number of children of this item.
     */
    @Override
    public int getChildCount() {
        return isLeaf ? 0 : super.getChildCount();
    }

    /**
     * Adds a child item to this item. !this.isLeaf() otherwise does nothing.
     */
    @Override
    public void addItem(final TreeItem item) {
        if (!isLeaf) {
            super.addItem(item);
        }
    }

    /**
     * Checks if the name exists within this item, assuming that this item is a folder.
     *
     * @param itemText The name to check.
     * @param itemId The ID of the item with the given name, to avoid self-matching
     * @return true if the label exists anywhere except the item with the matching ID.
     */
    public boolean labelExists(final String itemText, final String itemId) {
        for (int i = 0; i < super.getChildCount(); ++i) {
            final VisualisationAssetTreeItem assetTreeItem = (VisualisationAssetTreeItem) super.getChild(i);
            if (!Objects.equals(assetTreeItem.getId(), itemId)
                && Objects.equals(itemText, assetTreeItem.getLabel())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns whether this is a leaf (file) or not (folder/directory)
     */
    public boolean isLeaf() {
        return isLeaf;
    }

    /**
     * Returns the label of this item.
     */
    public String getLabel() {
        return label;
    }

    /**
     * For debugging
     */
    @Override
    public String toString() {
        return super.getText();
    }

}
