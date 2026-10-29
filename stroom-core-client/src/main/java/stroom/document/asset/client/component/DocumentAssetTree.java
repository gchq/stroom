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

import stroom.svg.shared.SvgImage;
import stroom.util.shared.Expander;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.DataGridProfile;
import stroom.widget.datagrid.client.DataGridSelectionEventManager;
import stroom.widget.util.client.ElementUtil;
import stroom.widget.util.client.MouseUtil;
import stroom.widget.util.client.MultiSelectEvent;
import stroom.widget.util.client.MultiSelectionModelImpl;
import stroom.widget.util.client.SelectionType;
import stroom.widget.util.client.SvgImageUtil;

import com.google.gwt.cell.client.AbstractCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.BrowserEvents;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.safecss.shared.SafeStyles;
import com.google.gwt.safecss.shared.SafeStylesUtils;
import com.google.gwt.safehtml.client.SafeHtmlTemplates;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.Focus;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.CellPreviewEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/// A document's assets, its files and folders, on the data grid with its tree profile
/// ([DataGridProfile#TREE]), drawn as the explorer's tree is: a `tree` of `treeitem`s with WAI-ARIA's
/// tree keys (Up and Down through the items shown, Right and Left to open and close a folder, or to
/// its first child and its parent, Home, End, `*`, and typing to go to an item by its name). One item
/// is chosen at a time, and the choice follows the focus. Enter on a file, or a double click on one,
/// opens it (see [#setOpenHandler]); on a folder, they open or close it.
///
/// The items ([DocumentAssetTreeItem]) are the tree's model: the top ones are added to the tree,
/// their children to them, and changing them redraws the tree.
public class DocumentAssetTree extends Composite implements Focus {

    private static final Binder BINDER = GWT.create(Binder.class);
    private static final String EXPANDER_CLASS_NAME = "explorer-cell__expander";

    private static Template template;

    private final List<DocumentAssetTreeItem> items = new ArrayList<>();
    @UiField
    DataGrid<DocumentAssetTreeItem> grid;
    private final MultiSelectionModelImpl<DocumentAssetTreeItem> selectionModel = new MultiSelectionModelImpl<>();

    private List<DocumentAssetTreeItem> rows = new ArrayList<>();
    private DocumentAssetTreeItem selected;
    private Consumer<DocumentAssetTreeItem> openHandler;
    private Consumer<DocumentAssetTreeItem> onSelect;
    private boolean redrawScheduled;

    /// Creates an empty tree, e.g. one a template declares; name it with [#setAccessibleName].
    public DocumentAssetTree() {
        if (template == null) {
            template = GWT.create(Template.class);
        }

        initWidget(BINDER.createAndBindUi(this));
        grid.setPageSize(DataGrid.MASSIVE_LIST_PAGE_SIZE);
        grid.addColumn(new Column<DocumentAssetTreeItem, DocumentAssetTreeItem>(new ItemCell()) {
            @Override
            public DocumentAssetTreeItem getValue(final DocumentAssetTreeItem item) {
                return item;
            }
        }, "", 100);
        grid.setTreeRows(DocumentAssetTree::expanderOf, DocumentAssetTreeItem::setState);
        grid.setSelectionModel(selectionModel, new SelectionEventManager());
        selectionModel.addSelectionHandler(this::onSelect);

    }

    /// Names the tree for screen readers, e.g. `Assets`.
    ///
    /// @param accessibleName The name.
    public void setAccessibleName(final String accessibleName) {
        grid.setAccessibleName(accessibleName);
    }

    /// Sets what is told when the choice changes (it follows the focus).
    ///
    /// @param onSelect Given the item chosen, or null for none.
    public void setOnSelect(final Consumer<DocumentAssetTreeItem> onSelect) {
        this.onSelect = onSelect;
    }

    // An item's level, and whether it is open, or a leaf (a file or an empty folder)
    private static Expander expanderOf(final DocumentAssetTreeItem item) {
        return new Expander(item.getDepth(), item.getState(), item.getChildCount() == 0);
    }

    /// Sets what opens a file, as Enter or a double click on it does, e.g. confirming a dialog's choice.
    ///
    /// @param openHandler Given the file, or null to do nothing.
    public void setOpenHandler(final Consumer<DocumentAssetTreeItem> openHandler) {
        this.openHandler = openHandler;
    }

    @Override
    public void focus() {
        grid.setFocus(true);
    }

    /// @return The number of items at the top of the tree.
    public int getItemCount() {
        return items.size();
    }

    /// @param index The item's index.
    /// @return The item at the index among those at the top of the tree.
    public DocumentAssetTreeItem getItem(final int index) {
        return items.get(index);
    }

    /// Adds an item to the end of those at the top of the tree, taking it from wherever it was.
    ///
    /// @param item The item.
    public void addItem(final DocumentAssetTreeItem item) {
        item.detach();
        items.add(item);
        item.setTree(this);
        onItemsChanged();
    }

    /// Removes all the items, and the choice, without telling the selection handlers.
    public void clear() {
        removeItems();
    }

    /// Removes all the items, and the choice if it was one of them, without telling the selection
    /// handlers.
    public void removeItems() {
        for (final DocumentAssetTreeItem item : items) {
            item.setTree(null);
        }
        items.clear();
        onItemsChanged();
    }

    /// @return The chosen item, or null if none is.
    public DocumentAssetTreeItem getSelectedItem() {
        return selected;
    }

    /// Chooses an item, opening the folders it is in so it is shown.
    ///
    /// @param item       The item, or null to choose none.
    /// @param fireEvents Whether to tell the selection handlers.
    public void setSelectedItem(final DocumentAssetTreeItem item, final boolean fireEvents) {
        if (item != null) {
            DocumentAssetTreeItem ancestor = item.getParentItem();
            while (ancestor != null) {
                ancestor.setState(true);
                ancestor = ancestor.getParentItem();
            }
        }
        final boolean changed = item != selected;
        selected = item;
        selectionModel.setSelected(item, new SelectionType(), false);
        if (changed && fireEvents && onSelect != null) {
            onSelect.accept(item);
        }
    }

    /// Takes an item from the top of the tree.
    ///
    /// @param item The item.
    void removeItem(final DocumentAssetTreeItem item) {
        if (items.remove(item)) {
            onItemsChanged();
        }
    }

    /// Drops the choice if it is no longer in the tree, and redraws the tree once the current
    /// changes to its items are done.
    void onItemsChanged() {
        if (selected != null && selected.getTree() != this) {
            selected = null;
            selectionModel.clear(false);
        }
        if (!redrawScheduled) {
            redrawScheduled = true;
            Scheduler.get().scheduleFinally(this::redraw);
        }
    }

    // Shows the open items' children
    private void redraw() {
        redrawScheduled = false;
        final List<DocumentAssetTreeItem> shown = new ArrayList<>();
        for (final DocumentAssetTreeItem item : items) {
            addShown(item, shown);
        }
        rows = shown;
        grid.setRowData(0, rows);
        grid.setRowCount(rows.size(), true);
    }

    private static void addShown(final DocumentAssetTreeItem item, final List<DocumentAssetTreeItem> shown) {
        shown.add(item);
        if (item.getState()) {
            for (int i = 0; i < item.getChildCount(); i++) {
                addShown(item.getChild(i), shown);
            }
        }
    }

    // The user's choice, by the keys or a click: tell the handlers if it changed, and open on a
    // double select
    private void onSelect(final MultiSelectEvent event) {
        final DocumentAssetTreeItem item = selectionModel.getSelected();
        if (item != selected) {
            selected = item;
            if (onSelect != null) {
                onSelect.accept(item);
            }
        }
        if (item != null && event.getSelectionType().isDoubleSelect()) {
            if (item.isLeaf()) {
                if (openHandler != null) {
                    openHandler.accept(item);
                }
            } else {
                item.setState(!item.getState());
            }
        }
    }


    // --------------------------------------------------------------------------------


    /// The tree's choosing: the keys and clicks of a grid, and a press on a folder's expander opens
    /// or closes it.
    private class SelectionEventManager extends DataGridSelectionEventManager<DocumentAssetTreeItem> {

        SelectionEventManager() {
            super(grid, selectionModel, false);
        }

        @Override
        public void onCellPreview(final CellPreviewEvent<DocumentAssetTreeItem> event) {
            final NativeEvent nativeEvent = event.getNativeEvent();
            final DocumentAssetTreeItem item = event.getValue();
            if (BrowserEvents.MOUSEDOWN.equals(nativeEvent.getType())
                && MouseUtil.isPrimary(nativeEvent)
                && item != null
                && item.getChildCount() > 0
                && ElementUtil.hasClassName(nativeEvent.getEventTarget().cast(), EXPANDER_CLASS_NAME, 5)) {
                final int row = rows.indexOf(item);
                if (row >= 0) {
                    grid.setKeyboardSelectedRow(row, true);
                }
                item.setState(!item.getState());
            } else {
                super.onCellPreview(event);
            }
        }
    }


    // --------------------------------------------------------------------------------


    /// An item's row: its expander, indented to its level, its icon and its name, as the explorer's.
    private static class ItemCell extends AbstractCell<DocumentAssetTreeItem> {

        @Override
        public void render(final Context context, final DocumentAssetTreeItem item, final SafeHtmlBuilder sb) {
            if (item == null) {
                return;
            }
            // Indented by its depth, as the explorer's (explorer-cell.css's explorer-cell__expander)
            final SafeStyles paddingLeft = SafeStylesUtils.fromTrustedNameAndValue(
                    "--explorer-cell__depth", String.valueOf(item.getDepth()));
            final SafeHtml expanderIcon;
            String expanderClassName = EXPANDER_CLASS_NAME;
            if (item.getChildCount() > 0) {
                final SvgImage arrow = item.getState()
                        ? SvgImage.ARROW_DOWN
                        : SvgImage.ARROW_RIGHT;
                expanderIcon = SafeHtmlUtils.fromTrustedString(arrow.getSvg());
                expanderClassName += " " + arrow.getClassName();
            } else {
                expanderIcon = SvgImageUtil.emptySvg();
            }

            final SafeHtmlBuilder content = new SafeHtmlBuilder();
            content.append(template.expander(expanderClassName, paddingLeft, expanderIcon));
            content.append(template.div("explorer-cell__icon-wrapper",
                    SvgImageUtil.toSafeHtml(item.getIcon(), "explorer-cell__icon")));
            content.append(template.text("explorer-cell__text", SafeHtmlUtils.fromString(item.getLabel())));
            sb.append(template.outer(content.toSafeHtml()));
        }
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, DocumentAssetTree> {

    }


    // --------------------------------------------------------------------------------


    interface Template extends SafeHtmlTemplates {

        @Template("<div class=\"{0}\" style=\"{1}\">{2}</div>")
        SafeHtml expander(String className, SafeStyles styles, SafeHtml icon);

        @Template("<div class=\"{0}\">{1}</div>")
        SafeHtml div(String className, SafeHtml content);

        @Template("<div class=\"{0}\">{1}</div>")
        SafeHtml text(String className, SafeHtml content);

        @Template("<div class=\"explorer-cell\">{0}</div>")
        SafeHtml outer(SafeHtml content);
    }
}
