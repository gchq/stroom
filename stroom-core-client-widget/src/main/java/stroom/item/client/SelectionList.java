package stroom.item.client;

import stroom.data.grid.client.PagerViewImpl;
import stroom.data.table.client.MyCellTable;
import stroom.util.shared.PageRequest;
import stroom.util.shared.PageResponse;
import stroom.util.shared.ResultPage;
import stroom.widget.dropdowntree.client.view.QuickFilter;
import stroom.widget.util.client.AbstractSelectionEventManager;
import stroom.widget.util.client.DoubleSelectTester;
import stroom.widget.util.client.MouseUtil;
import stroom.widget.util.client.MultiSelectEvent;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.MultiSelectionModelImpl;
import stroom.widget.util.client.SelectionType;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class SelectionList<T, I extends SelectionItem> extends Composite {

    private final QuickFilter quickFilter;
    private final FlowPanel links;
    private final PagerViewImpl pagerView;
    private final CellTable<I> cellTable;
    private SelectionListModel<T, I> model;
    private final MultiSelectionModelImpl<I> selectionModel;
    private List<NavigationState<I>> navigationStates = new ArrayList<>();
    private ResultPage<I> currentResult;
    private String lastFilter;

    private final EventBinder eventBinder = new EventBinder() {
        @Override
        protected void onBind() {
            registerHandler(quickFilter.addValueChangeHandler(e -> {
                if (!Objects.equals(e.getValue(), lastFilter)) {
                    lastFilter = e.getValue();
                    refresh();
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
                super.onBrowserEvent2(event);
                if (event.getTypeInt() == Event.ONKEYDOWN && event.getKeyCode() == KeyCodes.KEY_UP) {
                    if (cellTable.getKeyboardSelectedRow() == 0) {
                        quickFilter.focus();
                    }
                }
            }
        };

        selectionModel = new MultiSelectionModelImpl<>(cellTable);
        SelectionEventManager<T, I> selectionEventManager = new SelectionEventManager<>(cellTable,
                selectionModel,
                this);
        cellTable.setSelectionModel(selectionModel, selectionEventManager);
        final AsyncDataProvider<I> dataProvider = new AsyncDataProvider<I>() {
            @Override
            protected void onRangeChanged(final HasData<I> display) {
                refresh();
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

    public void setKeyboardSelectionPolicy(final KeyboardSelectionPolicy policy) {
        cellTable.setKeyboardSelectionPolicy(policy);
    }

    public void focus() {
        quickFilter.focus();
    }

    @Override
    protected void onLoad() {
        eventBinder.bind();
    }

    @Override
    protected void onUnload() {
        eventBinder.unbind();
    }

    public void refresh() {
        refresh(new ArrayList<>(navigationStates));
    }

    public void refresh(final List<NavigationState<I>> navigationStates) {
        if (model != null) {
            final Range range = cellTable.getVisibleRange();
            final PageRequest pageRequest = new PageRequest(range.getStart(), range.getLength());

            final I parentItem;
            if (!navigationStates.isEmpty()) {
                parentItem = navigationStates.get(navigationStates.size() - 1).getSelectedItem();
            } else {
                parentItem = null;
            }

            model.onRangeChange(parentItem, lastFilter, pageRequest, pageResponse -> {
                this.navigationStates = navigationStates;
                currentResult = pageResponse;
                cellTable.setRowData(pageResponse.getPageStart(), pageResponse.getValues());
                cellTable.setRowCount(pageResponse.getPageSize(), pageResponse.isExact());
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

        refresh();
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
                selectionList.navigate(value);
            }
        }

        @Override
        protected void onMoveLeft(final CellPreviewEvent<I> e) {
            selectionList.navigateBack();
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
                            nativeEvent.getShiftKey()));
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
                            nativeEvent.getShiftKey()));
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
                select(e);
            }
        }

        private void select(final CellPreviewEvent<I> e) {
            final NativeEvent nativeEvent = e.getNativeEvent();
            final I selectedItem = e.getValue();
            if (selectedItem != null && MouseUtil.isPrimary(nativeEvent)) {
                final boolean doubleClick = doubleClickTest.test(selectedItem);
                doSelect(selectedItem,
                        new SelectionType(doubleClick,
                                false,
                                allowMultiSelect,
                                nativeEvent.getCtrlKey(),
                                nativeEvent.getShiftKey()));
            }
        }

        void doSelect(final I row, final SelectionType selectionType) {
            if (selectionModel != null) {
                if (!selectionModel.isSelected(row)) {
                    selectionModel.setSelected(row);
                } else {
                    MultiSelectEvent.fire(cellTable, selectionType);
                }

                if (row != null && row.isHasChildren()) {
                    selectionList.navigate(row);
                } else {
                    selectionList.setKeyboardSelection(row);
                }
            }
        }
    }

    private void setKeyboardSelection(final I value) {
        final int row = cellTable.getVisibleItems().indexOf(value);
        if (row >= 0) {
            cellTable.setKeyboardSelectedRow(row, true);
        } else {
            cellTable.setKeyboardSelectedRow(cellTable.getKeyboardSelectedRow(), true);
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
            final I selectedItem = navigationState.getSelectedItem();
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
                navigateBack(row);
            }
        }, ClickEvent.getType());
        return link;
    }

    public void navigate(I selectionItem) {
        final List<I> list = new ArrayList<>(currentResult.getValues());
        final PageResponse pageResponse = currentResult.getPageResponse();
        final PageResponse pageResponseCopy = pageResponse.copy().build();
        final ResultPage<I> resultPage = new ResultPage<>(list, pageResponseCopy);
        final NavigationState<I> navigationState = new NavigationState<>(selectionItem, resultPage);
        final List<NavigationState<I>> navigationStates = new ArrayList<>(this.navigationStates);
        navigationStates.add(navigationState);
        refresh(navigationStates);
    }

    private void navigateBack() {
        if (!navigationStates.isEmpty()) {
            final NavigationState<I> navigationState = navigationStates.remove(navigationStates.size() - 1);
            setState(navigationState);
        }
    }

    private void navigateBack(I selectionItem) {
        NavigationState<I> navigationState = null;
        while (!navigationStates.isEmpty() && !navigationStates.get(navigationStates.size() - 1).getSelectedItem().equals(selectionItem)) {
            navigationState = navigationStates.remove(navigationStates.size() - 1);
        }
        if (navigationState == null && !navigationStates.isEmpty()) {
            navigationState = navigationStates.get(navigationStates.size() - 1);
        }
        if (navigationState != null) {
            setState(navigationState);
        }
    }

    private void setState(final NavigationState<I> navigationState) {
        if (navigationState != null) {
            currentResult = navigationState.getResultPage();
            cellTable.setRowData(currentResult.getPageStart(), currentResult.getValues());
            cellTable.setRowCount(currentResult.getPageSize(), currentResult.isExact());
            selectionModel.setSelected(navigationState.getSelectedItem());
            setKeyboardSelection(navigationState.getSelectedItem());
        }
    }

    public static class NavigationState<I extends SelectionItem> {

        private final I selectedItem;
        private final ResultPage<I> resultPage;

        public NavigationState(final I selectedItem,
                               final ResultPage<I> resultPage) {
            this.selectedItem = selectedItem;
            this.resultPage = resultPage;
        }

        public I getSelectedItem() {
            return selectedItem;
        }

        public ResultPage<I> getResultPage() {
            return resultPage;
        }
    }
}
