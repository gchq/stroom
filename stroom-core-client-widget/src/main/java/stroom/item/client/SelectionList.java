/*
 * Copyright 2023 Crown Copyright
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

package stroom.item.client;

import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.TokenError;
import stroom.widget.button.client.LinkButton;
import stroom.widget.component.client.AriaPanel;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.DataGridProfile;
import stroom.widget.datagrid.client.DataGridSelectionEventManager;
import stroom.widget.datagrid.client.PagerPanel;
import stroom.widget.quickfilter.client.QuickFilter;
import stroom.widget.util.client.DoubleSelectTester;
import stroom.widget.util.client.MouseUtil;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.MultiSelectionModelImpl;
import stroom.widget.util.client.Selection;
import stroom.widget.util.client.SelectionType;
import stroom.widget.util.client.TypeAhead;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.BrowserEvents;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.HasKeyboardSelectionPolicy.KeyboardSelectionPolicy;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.AsyncDataProvider;
import com.google.gwt.view.client.CellPreviewEvent;
import com.google.gwt.view.client.HasData;
import com.google.gwt.view.client.Range;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/// A list box whose rows are options, with a quick filter above it for a long list, a pager for a very
/// long one, and a path back up through the levels of a list of lists. A selection box's list, and
/// a list on a screen of its own (e.g. query help's).
///
/// Styled by selection-list.css: `selection-list`, `selection-list__filter`, `selection-list__path`
/// (holding `selection-list__path-link`, `selection-list__path-separator` and
/// `selection-list__path-current`) and `selection-list__options`.
public class SelectionList<T, I extends SelectionItem> extends Composite {

    private static final Binder BINDER = GWT.create(Binder.class);
    // The block's classes (selection-list.css); the rest are in the template
    private static final String BLOCK = "selection-list";
    private static final String PATH_LINK = BLOCK + "__path-link";
    private static final String PATH_SEPARATOR = BLOCK + "__path-separator";
    private static final String PATH_CURRENT = BLOCK + "__path-current";

    @UiField
    QuickFilter quickFilter;
    @UiField
    AriaPanel links;
    @UiField
    PagerPanel pagerView;
    private final SelectionEventManager<T, I> selectionEventManager;
    // What has been typed to jump to an option (in a list with no quick filter)
    private final TypeAhead typeAhead = new TypeAhead();
    // The options: a list (listbox) on the data grid
    private final DataGrid<I> cellTable;
    private SelectionListModel<T, I> model;
    private Runnable onClose;
    private final MultiSelectionModelImpl<I> selectionModel;
    private final List<NavigationState<I>> navigationStates = new ArrayList<>();
    private String lastFilter;
    // Whether more than one option can be chosen (see setMultiSelect)
    private boolean multiSelect;

    private final EventBinder eventBinder = new EventBinder() {
        @Override
        protected void onBind() {
            registerHandler(quickFilter.addValueChangeHandler(e -> {
                if (!Objects.equals(e.getValue(), lastFilter)) {
                    lastFilter = e.getValue();
                    refresh(true, false);
                }
            }));
        }
    };

    /// Makes an empty list, e.g. one a template declares; [#setModel] gives it its options.
    public SelectionList() {
        initWidget(BINDER.createAndBindUi(this));
        cellTable = new DataGrid<>(100);
        cellTable.setProfile(DataGridProfile.LISTBOX);
        // Options to choose, not a table to copy or export: the grid's own menu would offer that
        cellTable.hasContextMenu(false);
        // An option with no text (e.g. the blank one that clears a value) is named None
        cellTable.setRowName(item -> NullSafe.isBlankString(item.getLabel())
                ? "None"
                : null);
        selectionModel = new MultiSelectionModelImpl<>();
        selectionEventManager = new SelectionEventManager<>(cellTable,
                selectionModel,
                this);
        cellTable.setSelectionModel(selectionModel, selectionEventManager);
        // A character typed on an option goes to the quick filter, or to the next option starting
        // with it
        selectionEventManager.setTypedHandler(this::typeAhead);
        final AsyncDataProvider<I> dataProvider = new AsyncDataProvider<I>() {
            @Override
            protected void onRangeChanged(final HasData<I> display) {
                refresh(false, false);
            }
        };
        dataProvider.addDataDisplay(cellTable);

        final Column<I, I> expanderColumn =
                new Column<I, I>(new SelectionItemCell<>()) {
                    @Override
                    public I getValue(final I object) {
                        return object;
                    }
                };
        cellTable.addColumn(expanderColumn, "", 100);

        pagerView.setDataWidget(cellTable);

        // After the filter's own keys (Escape clears a filter with text in it): Down goes into the
        // options, and Escape closes the list (a selection popup closes on Escape itself, before
        // this sees it)
        quickFilter.addKeyDownHandler(event -> {
            if (event.isDownArrow()) {
                cellTable.setKeyboardSelectedRow(0, true);
            } else if (event.getNativeKeyCode() == KeyCodes.KEY_ESCAPE) {
                close();
            }
        });
    }

    /// Sets what the quick filter shows as its help, e.g. the fields it can filter on.
    ///
    /// @param popupTextSupplier Gives the help.
    public void setPopupTextProvider(final Supplier<SafeHtml> popupTextSupplier) {
        quickFilter.registerPopupTextProvider(popupTextSupplier);
    }

    /// Names the list for screen readers, e.g. after the box that opened it. With no name, the
    /// list has none, rather than an empty one.
    ///
    /// @param name The list's accessible name, or null or blank for none.
    public void setAccessibleName(final String name) {
        cellTable.setAccessibleName(name);
        if (NullSafe.isBlankString(name)) {
            // Still named, by what it filters, as a placeholder is only a hint
            quickFilter.setAccessibleName("Filter options");
        } else {
            // Its quick filter too, by what it filters
            quickFilter.setAccessibleName("Filter " + name);
        }
    }

    /// Makes the options as high and wide as they need, up to the size their container allows
    /// (its `max-height` and `max-width`), then scrolling, as a selection box's list in its popup.
    /// By default the options fill what holds the list.
    ///
    /// @param fitContent Whether the options are as big as they need.
    public void setFitContent(final boolean fitContent) {
        cellTable.setFitContent(fitContent);
        cellTable.setFitWidth(fitContent);
    }

    /// @return The id of the list's list box element, for a box that controls it (`aria-controls`).
    public String getListBoxId() {
        return cellTable.getAccessibleElementId();
    }

    /// Lets more than one option be chosen, as in WAI-ARIA's list box with multiple selection, for a
    /// list on a form rather than a selection box's. Moving only moves the focus. Space adds the
    /// option with the focus to those chosen, or takes it from them; Shift+Down and Shift+Up move to
    /// the next option and do the same there; Shift+Space chooses the options from the last one
    /// chosen to the one with the focus, keeping the others; a click chooses only the option
    /// clicked, Ctrl+click adds or takes it, and Shift+click chooses the options from the last one
    /// chosen to it; Ctrl+A chooses every option. Enter, or a double click, acts on those chosen
    /// (choosing the option with the focus first, if it isn't), which the selection model announces
    /// as a double select (see [#getSelectionModel()]). The options chosen are kept when the list is
    /// refreshed.
    ///
    /// @param multiSelect Whether more than one option can be chosen.
    public void setMultiSelect(final boolean multiSelect) {
        this.multiSelect = multiSelect;
        cellTable.setMultiSelectable(multiSelect);
    }

    /// Sets what Up on the first option does in a list with no quick filter, e.g. moves the focus
    /// to a field above the list that filters it.
    ///
    /// @param moveUpFromFirst What to do, or null for nothing (the first option keeps the focus).
    public void setMoveUpFromFirst(final Runnable moveUpFromFirst) {
        selectionEventManager.setMoveUpFromFirst(moveUpFromFirst);
    }

    /// Goes to what is typed, as typing in a WAI-ARIA combobox's list does: into the list's quick
    /// filter, if it has one, applied at once; otherwise to the next option that starts with what has
    /// been typed (characters typed within half a second of each other are taken together).
    ///
    /// @param text The text typed, e.g. a character.
    public void typeAhead(final String text) {
        if (model.displayFilter()) {
            quickFilter.type(text);
            return;
        }
        final List<I> items = cellTable.getVisibleItems();
        final int row = typeAhead.next(text,
                cellTable.getKeyboardSelectedRow(),
                items.size(),
                i -> NullSafe.string(items.get(i).getLabel()));
        if (row >= 0) {
            cellTable.setKeyboardSelectedRow(row, true);
        }
    }

    /// Gives the focus to the first or last option, e.g. as Home or End opens a selection box's list.
    /// With no options yet, the list takes the focus as [#focus()] gives it.
    ///
    /// @param last Whether to go to the last option rather than the first.
    public void focusFirstOrLast(final boolean last) {
        final int count = cellTable.getVisibleItemCount();
        if (count == 0) {
            focus();
        } else {
            cellTable.setKeyboardSelectedRow(last
                    ? count - 1
                    : 0, true);
        }
    }

    // Whether the text is one character, perhaps typed more than once (e.g. "d" or "ddd")
    // Chooses the option with the focus, as Enter does, which closes the list
    private void chooseKeyboardSelected() {
        final int row = cellTable.getKeyboardSelectedRow();
        final List<I> items = cellTable.getVisibleItems();
        if (row >= 0 && row < items.size()) {
            selectionEventManager.doSelect(items.get(row),
                    new SelectionType(true, false, false, false, false),
                    true);
        } else {
            close();
        }
    }


    public void setKeyboardSelectionPolicy(final KeyboardSelectionPolicy policy) {
        cellTable.setKeyboardSelectionPolicy(policy);
    }

    public void focus() {
        if (model.displayFilter()) {
            quickFilter.forceFocus();
        } else {
            final int selectionIndex = getSelectionIndex();
            if (selectionIndex >= 0) {
                cellTable.setKeyboardSelectedRow(selectionIndex, true);
            } else if (cellTable.getKeyboardSelectedRow() >= 0) {
                cellTable.setKeyboardSelectedRow(cellTable.getKeyboardSelectedRow(), true);
            } else {
                cellTable.setKeyboardSelectedRow(0, true);
            }
        }
    }

    private int getSelectionIndex() {
        final List<I> items = cellTable.getVisibleItems();
        if (items != null) {
            for (int i = 0; i < items.size(); i++) {
                if (cellTable.getSelectionModel().isSelected(items.get(i))) {
                    return i;
                }
            }
        }
        return -1;
    }

    @Override
    protected void onLoad() {
        eventBinder.bind();
    }

    @Override
    protected void onUnload() {
        eventBinder.unbind();
    }

    private I getCurrentParent() {
        final NavigationState<I> currentState = getCurrentState();
        if (currentState == null) {
            return null;
        }
        return currentState.getSelected();
    }

    private NavigationState<I> getCurrentState() {
        if (navigationStates.isEmpty()) {
            return null;
        }
        return navigationStates.get(navigationStates.size() - 1);
    }

    public void refresh(final boolean filterChange, final boolean stealFocus) {
        refresh(getCurrentParent(), null, filterChange, cellTable.getVisibleRange(), stealFocus);
    }

    public void refresh(final I parent,
                        final I selection,
                        final boolean filterChange,
                        final Range range,
                        final boolean stealFocus) {
        if (model != null) {
            final PageRequest pageRequest = new PageRequest(range.getStart(), range.getLength());
            model.onRangeChange(parent, lastFilter, filterChange, pageRequest, pageResponse -> {
                // A model that filters server-side can come back with a reason the filter was not
                // applied. Models that filter client-side never set one, so this is a no-op for
                // them. See ResultPage.filterError.
                quickFilter.setFilterError(
                        NullSafe.get(pageResponse.getFilterError(), TokenError::getText));

                // A list of several choices keeps them, e.g. when it is paged
                if (!multiSelect) {
                    selectionModel.clear(false);
                }

                cellTable.setRowData(pageResponse.getPageStart(), pageResponse.getValues());
                cellTable.setRowCount(pageResponse.getPageSize(), pageResponse.isExact());
                quickFilter.setVisible(model.displayFilter());
                links.setVisible(model.displayPath());
                pagerView.setPagerVisible(model.displayPager());
                // Only update the path when we get data
                updatePath();

                if (selection != null) {
                    selectionModel.setSelected(selection, false);
                    setKeyboardSelection(selection, stealFocus);
                }

                // Navigate into some items if needed.
                if (filterChange && pageResponse.getPageStart() == 0) {
                    if (pageResponse.getPageSize() == 1) {
                        final I selectionItem = pageResponse.getValues().get(0);
                        if (selectionItem.isHasChildren()) {
                            navigate(selectionItem, filterChange, false);
                        } else if (getCurrentState() != null) {
                            if (model.isEmptyItem(selectionItem)) {
                                navigateBack(filterChange, false);
                            }
                        }
                    } else if (pageResponse.getPageSize() == 0 && getCurrentState() != null) {
                        navigateBack(filterChange, false);
                    }
                }
            });
        }
    }

    /// Gives the list its options, once, and shows them.
    ///
    /// @param model The options, and whether the list has a filter, a breadcrumb and a pager.
    /// @throws IllegalStateException If the list already has a model.
    public void setModel(final SelectionListModel<T, I> model) {
        if (this.model != null) {
            throw new IllegalStateException("Already has a model.");
        }

        this.model = model;
        quickFilter.setVisible(model.displayFilter());
        links.setVisible(model.displayPath());
        pagerView.setPagerVisible(model.displayPager());

        refresh(false, false);
    }

    public void refresh() {
        quickFilter.clear();
        refresh(true, true);
    }

    public void destroy() {
        if (model != null) {
            lastFilter = null;
            navigationStates.clear();
            model.reset();
        }
    }

    public MultiSelectionModel<I> getSelectionModel() {
        return selectionModel;
    }

    /// Sets what closes the list, e.g. its popup, when the user leaves it: Escape, or choosing with
    /// nothing to choose.
    ///
    /// @param onClose What closes it, or null for nothing.
    public void setOnClose(final Runnable onClose) {
        this.onClose = onClose;
    }

    private void close() {
        if (onClose != null) {
            onClose.run();
        }
    }

    private void setKeyboardSelection(final I value, final boolean stealFocus) {
        final int row = cellTable.getVisibleItems().indexOf(value);
        if (row >= 0) {
            cellTable.setKeyboardSelectedRow(row, stealFocus);
        } else {
            cellTable.setKeyboardSelectedRow(cellTable.getKeyboardSelectedRow(), stealFocus);
        }
    }

    private void updatePath() {
        links.clear();
        if (navigationStates.isEmpty()) {
            links.add(createCurrentLevel(model.getPathRoot()));
        } else {
            links.add(createLevelButton(model.getPathRoot(), null));
        }
        for (int i = 0; i < navigationStates.size(); i++) {
            links.add(createSeparator());
            final NavigationState<I> navigationState = navigationStates.get(i);
            final I selectedItem = navigationState.getSelected();
            if (i < navigationStates.size() - 1) {
                links.add(createLevelButton(selectedItem.getLabel(), selectedItem));
            } else {
                links.add(createCurrentLevel(selectedItem.getLabel()));
            }
        }
    }

    // A level above the current one. It goes back within the list rather than to another page, so
    // it is a button that looks like a link
    private LinkButton createLevelButton(final String label, final I row) {
        final LinkButton button = new LinkButton(label);
        button.addStyleName(PATH_LINK);
        // The button is replaced as the path changes, so the list takes the focus
        button.addClickHandler(event -> navigateBack(row, false, true));
        return button;
    }

    // The level the list shows, which screen readers announce as the current one
    private static Label createCurrentLevel(final String label) {
        final Label current = new Label(label);
        current.setStyleName(PATH_CURRENT);
        current.getElement().setAttribute("aria-current", "page");
        return current;
    }

    // Screen readers needn't read the separators out
    private static Label createSeparator() {
        final Label separator = new Label("/");
        separator.setStyleName(PATH_SEPARATOR);
        separator.getElement().setAttribute("aria-hidden", "true");
        return separator;
    }

    private void navigate(final I selectionItem,
                          final boolean filterChange,
                          final boolean stealFocus) {
        final NavigationState<I> navigationState = new NavigationState<>(
                selectionItem,
                cellTable.getVisibleRange());
        navigationStates.add(navigationState);
        refresh(selectionItem, null, filterChange, new Range(0, cellTable.getPageSize()), stealFocus);
    }

    private void navigateBack(final boolean filterChange, final boolean stealFocus) {
        final NavigationState<I> navigationState;
        if (!navigationStates.isEmpty()) {
            navigationState = navigationStates.remove(navigationStates.size() - 1);
        } else {
            navigationState = null;
        }
        navigateBack(navigationState, filterChange, stealFocus);
    }

    private void navigateBack(final I selectionItem, final boolean filterChange, final boolean stealFocus) {
        NavigationState<I> navigationState = null;
        while (!navigationStates.isEmpty() && !navigationStates
                .get(navigationStates.size() - 1)
                .getSelected()
                .equals(selectionItem)) {
            navigationState = navigationStates.remove(navigationStates.size() - 1);
        }
        navigateBack(navigationState, filterChange, stealFocus);
    }

    private void navigateBack(final NavigationState<I> navigationState,
                              final boolean filterChange,
                              final boolean stealFocus) {
        final I selection;
        final Range range;
        if (navigationState != null) {
            selection = navigationState.getSelected();
            range = navigationState.getRange();
        } else {
            selection = null;
            range = new Range(0, cellTable.getPageSize());
        }
        refresh(getCurrentParent(), selection, filterChange, range, stealFocus);
    }


    // --------------------------------------------------------------------------------


    /// The list's choosing, on the grid's keyboard and the mouse: moving only moves the focus (as a
    /// combobox's list does); Enter, Alt+Up or a click chooses; Right goes into an option with
    /// options of its own and Left back out; Escape closes the list. In a list of several choices,
    /// Space adds the option with the focus or takes it away, Shift+Down and Shift+Up move and do the
    /// same there, Shift+Space chooses the run from the last one chosen, Ctrl+A chooses all, and
    /// Enter or a double click acts on those chosen.
    private static class SelectionEventManager<T, I extends SelectionItem>
            extends DataGridSelectionEventManager<I> {

        private final DataGrid<I> cellTable;
        private final MultiSelectionModelImpl<I> selectionModel;
        private final SelectionList<T, I> selectionList;
        private final DoubleSelectTester doubleClickTest = new DoubleSelectTester();
        // In a list of several choices, the option chosen last, where Shift chooses a run from
        private I multiSelectStart;

        public SelectionEventManager(final DataGrid<I> cellTable,
                                     final MultiSelectionModelImpl<I> selectionModel,
                                     final SelectionList<T, I> selectionList) {
            super(cellTable, selectionModel, false);
            this.cellTable = cellTable;
            this.selectionModel = selectionModel;
            this.selectionList = selectionList;
        }

        // Moving only moves the focus, but Shift+Down and Shift+Up in a list of several choices
        // choose the option moved to, or take it away
        @Override
        public void onFocusRow(final I item, final boolean shift, final boolean ctrl) {
            if (item != null && selectionList.multiSelect && shift && !ctrl) {
                multiSelect(item, true, false, true);
            }
        }

        @Override
        public void onSpace(final I item, final boolean shift, final boolean ctrl) {
            if (item == null) {
                return;
            }
            if (selectionList.multiSelect) {
                // Space adds the option to those chosen or takes it from them, as Ctrl+click does;
                // Shift+Space chooses the run from the last one chosen
                multiSelect(item, true, shift, true);
            } else if (selectionList.model.displayFilter()) {
                // Typed into the quick filter
                selectionList.typeAhead(" ");
            } else {
                doSelect(item, new SelectionType(false, false, true, ctrl, shift), true);
            }
        }

        @Override
        public void onEnter(final I item) {
            if (selectionList.multiSelect) {
                actOnChosen(item);
            } else if (item != null) {
                doSelect(item, new SelectionType(true, false, false, false, false), false);
            }
        }

        @Override
        public void onSelectAll() {
            if (selectionList.multiSelect) {
                final Selection<I> selection = selectionModel.getSelection();
                cellTable.getVisibleItems().forEach(item -> selection.setSelected(item, true));
                selectionModel.setSelection(selection, new SelectionType(false, false, true, true, false));
            }
        }

        // Up on the first option goes to the quick filter, if there is one
        @Override
        public boolean onUpFromFirst() {
            if (selectionList.model.displayFilter()) {
                selectionList.quickFilter.focus();
                return true;
            }
            return super.onUpFromFirst();
        }

        // Right goes into an option with options of its own
        @Override
        public void onRight() {
            final I value = keyboardItem();
            if (value != null && value.isHasChildren()) {
                selectionList.navigate(value, false, true);
            }
        }

        // Left goes back out to the level above
        @Override
        public void onLeft() {
            // A list of several choices keeps them at its top level, where there is nowhere to go
            // back to (going back would show its first page again)
            if (!selectionList.multiSelect || !selectionList.navigationStates.isEmpty()) {
                selectionList.navigateBack(false, true);
            }
        }

        private I keyboardItem() {
            final int row = cellTable.getKeyboardSelectedRow();
            return row >= 0 && row < cellTable.getVisibleItemCount()
                    ? cellTable.getVisibleItem(row)
                    : null;
        }

        @Override
        public void onCellPreview(final CellPreviewEvent<I> e) {
            final NativeEvent nativeEvent = e.getNativeEvent();
            final String type = nativeEvent.getType();
            if (BrowserEvents.MOUSEDOWN.equals(type)) {
                if (MouseUtil.isPrimary(nativeEvent)) {
                    final int row = cellTable.getVisibleItems().indexOf(e.getValue());
                    if (row >= 0) {
                        cellTable.setKeyboardSelectedRow(row, true);
                    }
                    select(e, true);
                }
            } else if (BrowserEvents.KEYDOWN.equals(type) && !selectionList.multiSelect
                       && nativeEvent.getAltKey() && nativeEvent.getKeyCode() == KeyCodes.KEY_UP) {
                // Alt+Up chooses the option with the focus and closes the list (WAI-ARIA combobox)
                nativeEvent.preventDefault();
                selectionList.chooseKeyboardSelected();
            } else if (BrowserEvents.KEYDOWN.equals(type) && nativeEvent.getKeyCode() == KeyCodes.KEY_ESCAPE) {
                selectionList.close();
            } else {
                super.onCellPreview(e);
            }
        }

        private void select(final CellPreviewEvent<I> e,
                            final boolean stealFocus) {
            final NativeEvent nativeEvent = e.getNativeEvent();
            final I selectedItem = e.getValue();
            if (selectedItem != null && MouseUtil.isPrimary(nativeEvent)) {
                final boolean doubleClick = doubleClickTest.test(selectedItem);
                if (selectionList.multiSelect) {
                    if (doubleClick) {
                        actOnChosen(selectedItem);
                    } else {
                        multiSelect(selectedItem,
                                nativeEvent.getCtrlKey(),
                                nativeEvent.getShiftKey(),
                                stealFocus);
                    }
                } else {
                    doSelect(selectedItem,
                            new SelectionType(doubleClick,
                                    false,
                                    false,
                                    nativeEvent.getCtrlKey(),
                                    nativeEvent.getShiftKey()), stealFocus);
                }
            }
        }

        // Chooses in a list of several choices: with Shift, the options from the last one chosen to
        // this one (keeping the others with Ctrl too); with Ctrl alone, this one is added or taken
        // away; otherwise only this one is chosen
        private void multiSelect(final I row,
                                 final boolean control,
                                 final boolean shift,
                                 final boolean stealFocus) {
            if (row == null) {
                return;
            }
            final Selection<I> selection = selectionModel.getSelection();
            final List<I> rows = cellTable.getVisibleItems();
            final int start = rows.indexOf(multiSelectStart);
            final int end = rows.indexOf(row);
            if (shift && start != -1 && end != -1) {
                if (!control) {
                    selection.clear();
                }
                for (int i = Math.min(start, end); i <= Math.max(start, end); i++) {
                    selection.setSelected(rows.get(i), true);
                }
            } else if (control) {
                multiSelectStart = row;
                selection.setSelected(row, !selection.isSelected(row));
            } else {
                multiSelectStart = row;
                selection.setSelected(row);
            }
            selectionModel.setSelection(selection, new SelectionType(false, false, true, control, shift));
            selectionList.setKeyboardSelection(row, stealFocus);
        }

        // Acts on the options chosen in a list of several choices (Enter, or a double click), first
        // choosing only the option with the focus if it isn't one of them, so that Enter acts on what
        // it is on. The selection model announces it as a double select, as a selection box's list
        // does a choice
        private void actOnChosen(final I row) {
            final Selection<I> selection = selectionModel.getSelection();
            if (row != null && !selection.isSelected(row)) {
                multiSelectStart = row;
                selection.setSelected(row);
            }
            selectionModel.setSelection(selection, new SelectionType(true, false, true, false, false));
        }

        // Choosing the item that is already the only one selected doesn't change the selection, so
        // the model would fire no event and whatever opened the list (e.g. a selection box) wouldn't
        // know it had been chosen. Choosing it is then treated as a double select, which the model
        // does fire an event for
        private SelectionType asChoice(final I row, final SelectionType selectionType) {
            if (row != null
                && !row.isHasChildren()
                && !selectionType.isDoubleSelect()
                && selectionModel.isSelected(row)
                && selectionModel.getSelectedCount() == 1) {
                return new SelectionType(true,
                        selectionType.isRightClick(),
                        selectionType.isAllowMultiSelect(),
                        selectionType.isControlPressed(),
                        selectionType.isShiftPressed());
            }
            return selectionType;
        }

        void doSelect(final I row, final SelectionType selectionType, final boolean stealFocus) {
            if (selectionModel != null) {
                selectionModel.setSelected(row, asChoice(row, selectionType));
                if (row != null && row.isHasChildren()) {
                    selectionList.navigate(row, false, stealFocus);
                } else {
                    selectionList.setKeyboardSelection(row, stealFocus);
                }
            }
        }
    }


    // --------------------------------------------------------------------------------


    public static class NavigationState<I extends SelectionItem> {

        private final I selected;
        private final Range range;

        public NavigationState(final I selected,
                               final Range range) {
            this.selected = selected;
            this.range = range;
        }

        public I getSelected() {
            return selected;
        }

        public Range getRange() {
            return range;
        }
    }


    // --------------------------------------------------------------------------------


    // The owner is raw: a binder can't name a type variable
    @SuppressWarnings("rawtypes")
    interface Binder extends UiBinder<Widget, SelectionList> {

    }
}
