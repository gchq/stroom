/*
 * Copyright 2016-2025 Crown Copyright
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

import stroom.data.grid.client.PagerViewImpl;
import stroom.data.table.client.MyCellTable;
import stroom.util.shared.PageRequest;
import stroom.widget.dropdowntree.client.view.QuickFilter;
import stroom.widget.tab.client.event.CloseEvent;
import stroom.widget.tab.client.event.CloseEvent.CloseHandler;
import stroom.widget.util.client.AbstractSelectionEventManager;
import stroom.widget.util.client.DoubleSelectTester;
import stroom.widget.util.client.MouseUtil;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.MultiSelectionModelImpl;
import stroom.widget.util.client.SelectionType;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.cellview.client.AbstractHasData;
import com.google.gwt.user.cellview.client.CellTable;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.HasKeyboardSelectionPolicy.KeyboardSelectionPolicy;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Hyperlink;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.view.client.AsyncDataProvider;
import com.google.gwt.view.client.CellPreviewEvent;
import com.google.gwt.view.client.HasData;
import com.google.gwt.view.client.Range;
import com.google.web.bindery.event.shared.HandlerRegistration;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public class SelectionList<T, I extends SelectionItem> extends Composite {

    private final QuickFilter quickFilter;
    private final FlowPanel links;
    private final PagerViewImpl pagerView;
    private final CellTable<I> cellTable;
    private SelectionListModel<T, I> model;
    private final MultiSelectionModelImpl<I> selectionModel;
    private final List<NavigationState<I>> navigationStates = new ArrayList<>();
    private String lastFilter;

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

    public SelectionList() {
        cellTable = new MyCellTable<I>(100) {
            @Override
            public void setRowData(final int start, final List<? extends I> values) {
                super.setRowData(start, values);

                quickFilter.setVisible(model.displayFilter());
                links.setVisible(model.displayPath());
                pagerView.setPagerVisible(model.displayPager());

                // Only update the path when we get data.
                updatePath();
            }

            @Override
            protected void onBrowserEvent2(final Event event) {
                if (event.getTypeInt() == Event.ONKEYDOWN) {
                    if (event.getKeyCode() == KeyCodes.KEY_UP) {
                        if (model.displayFilter()) {
                            if (cellTable.getKeyboardSelectedRow() == 0) {
                                quickFilter.focus();
                            }
                        }
                    } else if (event.getKeyCode() == KeyCodes.KEY_ESCAPE) {
                        CloseEvent.fire(SelectionList.this);
                    }
                }
                super.onBrowserEvent2(event);
            }
        };

        selectionModel = new MultiSelectionModelImpl<>();
        final SelectionEventManager<T, I> selectionEventManager = new SelectionEventManager<>(cellTable,
                selectionModel,
                this);
        cellTable.setSelectionModel(selectionModel, selectionEventManager);
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
        cellTable.addColumn(expanderColumn);

        pagerView = new PagerViewImpl(GWT.create(PagerViewImpl.Binder.class));
        pagerView.setDataWidget(cellTable);

        quickFilter = new QuickFilter() {
            @Override
            protected void onKeyDown(final KeyDownEvent event) {
                super.onKeyDown(event);
                if (event.isDownArrow()) {
                    cellTable.setKeyboardSelectedRow(0, true);
                } else if (event.getNativeKeyCode() == KeyCodes.KEY_ESCAPE) {
                    CloseEvent.fire(SelectionList.this);
                }
            }
        };

        quickFilter.addStyleName("dock-min selectionList-quickFilter");

        links = new FlowPanel();
        links.setStyleName("dock-min selectionList-links");

        final SimplePanel elementChooser = new SimplePanel();
        elementChooser.setStyleName("dock-max selectionList-elementChooser");
        elementChooser.add(pagerView.asWidget());

        final FlowPanel layout = new FlowPanel();
        layout.setStyleName("selectionList dock-container-vertical");
        layout.add(quickFilter);
        layout.add(links);
        layout.add(elementChooser);

        initWidget(layout);
    }

    public void registerPopupTextProvider(final Supplier<SafeHtml> popupTextSupplier) {
        quickFilter.registerPopupTextProvider(popupTextSupplier);
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
                selectionModel.clear(false);

                cellTable.setRowData(pageResponse.getPageStart(), pageResponse.getValues());
                cellTable.setRowCount(pageResponse.getPageSize(), pageResponse.isExact());

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

    public void init(final SelectionListModel<T, I> model) {
        if (this.model != null) {
            throw new RuntimeException("Already initialised.");
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

    public HandlerRegistration addCloseHandler(final CloseHandler handler) {
        return addHandler(handler, CloseEvent.getType());
    }

    private static class SelectionEventManager<T, I extends SelectionItem>
            extends AbstractSelectionEventManager<I> {

        private final MultiSelectionModelImpl<I> selectionModel;
        private final SelectionList<T, I> selectionList;
        private final DoubleSelectTester doubleClickTest = new DoubleSelectTester();
        private final boolean allowMultiSelect = false;

        public SelectionEventManager(final AbstractHasData<I> cellTable,
                                     final MultiSelectionModelImpl<I> selectionModel,
                                     final SelectionList<T, I> selectionList) {
            super(cellTable);
            this.selectionModel = selectionModel;
            this.selectionList = selectionList;
        }

        @Override
        protected void onMoveRight(final CellPreviewEvent<I> e) {
            final I value = e.getValue();
            if (value != null && value.isHasChildren()) {
                selectionList.navigate(value, false, true);
            }
        }

        @Override
        protected void onMoveLeft(final CellPreviewEvent<I> e) {
            selectionList.navigateBack(false, true);
        }

        @Override
        protected void onExecute(final CellPreviewEvent<I> e) {
            final NativeEvent nativeEvent = e.getNativeEvent();
            final I item = e.getValue();
            doSelect(item,
                    new SelectionType(true,
                            false,
                            allowMultiSelect,
                            nativeEvent.getCtrlKey(),
                            nativeEvent.getShiftKey()), false);
        }

        @Override
        protected void onSelect(final CellPreviewEvent<I> e) {
            final NativeEvent nativeEvent = e.getNativeEvent();
            // Change the selection.
            doSelect(e.getValue(),
                    new SelectionType(false,
                            false,
                            true,
                            nativeEvent.getCtrlKey(),
                            nativeEvent.getShiftKey()), true);
        }

        @Override
        protected void onMenu(final CellPreviewEvent<I> e) {
//            showMenu(e);
        }

        @Override
        protected void onSelectAll(final CellPreviewEvent<I> e) {
//            selectAll();
        }

        @Override
        protected void onMouseDown(final CellPreviewEvent<I> e) {
            final NativeEvent nativeEvent = e.getNativeEvent();
            if (MouseUtil.isPrimary(nativeEvent)) {
                select(e, true);
            }
        }

        private void select(final CellPreviewEvent<I> e,
                            final boolean stealFocus) {
            final NativeEvent nativeEvent = e.getNativeEvent();
            final I selectedItem = e.getValue();
            if (selectedItem != null && MouseUtil.isPrimary(nativeEvent)) {
                final boolean doubleClick = doubleClickTest.test(selectedItem);
                doSelect(selectedItem,
                        new SelectionType(doubleClick,
                                false,
                                allowMultiSelect,
                                nativeEvent.getCtrlKey(),
                                nativeEvent.getShiftKey()), stealFocus);
            }
        }

        void doSelect(final I row, final SelectionType selectionType, final boolean stealFocus) {
            if (selectionModel != null) {
                selectionModel.setSelected(row, selectionType);
                if (row != null && row.isHasChildren()) {
                    selectionList.navigate(row, false, stealFocus);
                } else {
                    selectionList.setKeyboardSelection(row, stealFocus);
                }
            }
        }
    }


    // --------------------------------------------------------------------------------


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
            links.add(new Label(model.getPathRoot()));
        } else {
            links.add(createLink(model.getPathRoot(), null));
        }
        for (int i = 0; i < navigationStates.size(); i++) {
            links.add(new Label("/"));
            final NavigationState<I> navigationState = navigationStates.get(i);
            final I selectedItem = navigationState.getSelected();
            if (i < navigationStates.size() - 1) {
                links.add(createLink(selectedItem.getLabel(), selectedItem));
            } else {
                links.add(new Label(selectedItem.getLabel()));
            }
        }
    }

    private Hyperlink createLink(final String label, final I row) {
        final Hyperlink link = new Hyperlink();
        link.setText(label);
        link.addHandler(event -> {
            if (MouseUtil.isPrimary(event)) {
                navigateBack(row, false, false);
            }
        }, ClickEvent.getType());
        return link;
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
}
