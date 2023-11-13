package stroom.item.client;

import stroom.data.grid.client.PagerViewImpl;
import stroom.data.table.client.MyCellTable;
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
import com.google.gwt.user.cellview.client.AbstractHasData;
import com.google.gwt.user.cellview.client.CellTable;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Hyperlink;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.view.client.CellPreviewEvent;

import java.util.List;

public class SelectionList extends Composite {


    private final QuickFilter quickFilter;
    private final FlowPanel links;
    private final CellTable<SelectionItem> cellTable;
    private SelectionListModel model;


    private final ExplorerTreeSelectionEventManager selectionEventManager;
    private final MultiSelectionModelImpl<SelectionItem> selectionModel;


    //    private final MySingleSelectionModel<SelectionItem> selectionModel;
//    private final SelectionEventManager selectionEventManager;
    private final EventBinder eventBinder = new EventBinder() {
        @Override
        protected void onBind() {
            registerHandler(quickFilter.addValueChangeHandler(e -> {
                if (model != null) {
                    model.setFilter(e.getValue());
                }
            }));
        }
    };

    public SelectionList() {
//        uiConfigCache.get()
//                .onSuccess(uiConfig ->
//                        quickFilter.registerPopupTextProvider(() ->
//                                QuickFilterTooltipUtil.createTooltip(
//                                        "Query Item Quick Filter",
//                                        ExplorerTreeFilter.FIELD_DEFINITIONS,
//                                        uiConfig.getHelpUrl())));

        cellTable = new MyCellTable<SelectionItem>(100) {
            @Override
            public void setRowData(final int start, final List<? extends SelectionItem> values) {
                super.setRowData(start, values);

                // Only update the path when we get data.
                if (model != null &&
                        model.getNavigationModel() != null) {
                    setPath(model.getNavigationModel().getPath());
                }
            }
        };

        selectionModel = new MultiSelectionModelImpl<>(cellTable);
        selectionEventManager = new ExplorerTreeSelectionEventManager(cellTable, selectionModel);

//        selectionModel = new MySingleSelectionModel<>();
//        selectionEventManager = new SelectionEventManager(cellTable, selectionModel);
        cellTable.setSelectionModel(selectionModel, selectionEventManager);

        final Column<SelectionItem, SelectionItem> expanderColumn =
                new Column<SelectionItem, SelectionItem>(new SelectionItemCell()) {
                    @Override
                    public SelectionItem getValue(final SelectionItem object) {
                        return object;
                    }
                };
        cellTable.addColumn(expanderColumn);

        final PagerViewImpl pagerView = new PagerViewImpl(GWT.create(PagerViewImpl.Binder.class));
        pagerView.setDataWidget(cellTable);

        quickFilter = new QuickFilter();
        quickFilter.addStyleName("dock-min");

        links = new FlowPanel();
        links.setStyleName("dock-min selectionList-links");

        final SimplePanel elementChooser = new SimplePanel();
        elementChooser.setStyleName("dock-max selectionList");
        elementChooser.add(pagerView.asWidget());

        final FlowPanel inner = new FlowPanel();
        inner.setStyleName("max dock-container-vertical");
        inner.add(quickFilter);
        inner.add(links);
        inner.add(elementChooser);

        final SimplePanel outer = new SimplePanel();
        outer.setStyleName("QueryHelpViewImpl max dashboard-panel");
        outer.add(inner);

        initWidget(outer);
    }

    public void focus() {
        quickFilter.focus();
    }

    public void reset() {
        quickFilter.clear();
    }

    @Override
    protected void onLoad() {
        eventBinder.bind();
    }

    @Override
    protected void onUnload() {
        eventBinder.unbind();
    }

    public void setModel(final SelectionListModel model) {
        this.model = model;
        selectionEventManager.setModel(model);
        model.getDataProvider().addDataDisplay(cellTable);
    }

    public MultiSelectionModel<SelectionItem> getSelectionModel() {
        return selectionModel;
    }

    private static class ExplorerTreeSelectionEventManager extends AbstractSelectionEventManager<SelectionItem> {

        private final MultiSelectionModelImpl<SelectionItem> selectionModel;
        private final DoubleSelectTester doubleClickTest = new DoubleSelectTester();
        private final boolean allowMultiSelect = false;
        //        private SelectionItem multiSelectStart;
        private SelectionListModel model;

        public ExplorerTreeSelectionEventManager(final AbstractHasData<SelectionItem> cellTable,
                                                 final MultiSelectionModelImpl<SelectionItem> selectionModel) {
            super(cellTable);
            this.selectionModel = selectionModel;
        }

        public void setModel(final SelectionListModel model) {
            this.model = model;
        }

//    @Override
//        protected void onMoveRight(final CellPreviewEvent<SelectionItem> e) {
////            if (e.getValue().hasNodeFlags(NodeFlag.LEAF)) {
////                showMenu(e);
////            } else {
////                treeModel.setItemOpen(e.getValue(), true);
////            }
//        }
//
//        @Override
//        protected void onMoveLeft(final CellPreviewEvent<SelectionItem> e) {
////            treeModel.setItemOpen(e.getValue(), false);
//        }

        @Override
        protected void onMoveRight(final CellPreviewEvent<SelectionItem> e) {
            final SelectionItem value = e.getValue();
            if (value != null && value.isHasChildren()) {
                if (model != null &&
                        model.getNavigationModel() != null &&
                        model.getNavigationModel().navigate(value)) {
                    model.refresh();
                }
            }
        }

        @Override
        protected void onMoveLeft(final CellPreviewEvent<SelectionItem> e) {
            if (model != null &&
                    model.getNavigationModel() != null &&
                    model.getNavigationModel().navigateBack()) {
                model.refresh();
            }


//                if (!openItems.empty()) {
//                    openItems.pop();
//                    model.refresh();
//                }
        }

        @Override
        protected void onExecute(final CellPreviewEvent<SelectionItem> e) {
            final NativeEvent nativeEvent = e.getNativeEvent();
            final SelectionItem item = e.getValue();
            doSelect(item,
                    new SelectionType(true,
                            false,
                            allowMultiSelect,
                            nativeEvent.getCtrlKey(),
                            nativeEvent.getShiftKey()));
        }

        @Override
        protected void onSelect(final CellPreviewEvent<SelectionItem> e) {
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
        protected void onMenu(final CellPreviewEvent<SelectionItem> e) {
//            showMenu(e);
        }

        @Override
        protected void onSelectAll(final CellPreviewEvent<SelectionItem> e) {
//            selectAll();
        }

        @Override
        protected void onMouseDown(final CellPreviewEvent<SelectionItem> e) {
            final NativeEvent nativeEvent = e.getNativeEvent();
            final int row = cellTable.getVisibleItems().indexOf(e.getValue());
            if (row >= 0) {
                cellTable.setKeyboardSelectedRow(row, true);
            } else {
                cellTable.setKeyboardSelectedRow(cellTable.getKeyboardSelectedRow(), true);
            }

            if (MouseUtil.isSecondary(nativeEvent)) {
//                showMenu(e);

            } else if (MouseUtil.isPrimary(nativeEvent)) {
                select(e);
            }
        }

        private void select(final CellPreviewEvent<SelectionItem> e) {
            final NativeEvent nativeEvent = e.getNativeEvent();
            final SelectionItem selectedItem = e.getValue();
            if (selectedItem != null && MouseUtil.isPrimary(nativeEvent)) {
                if (!selectedItem.isHasChildren()) {
                    final boolean doubleClick = doubleClickTest.test(selectedItem);
                    doSelect(selectedItem,
                            new SelectionType(doubleClick,
                                    false,
                                    allowMultiSelect,
                                    nativeEvent.getCtrlKey(),
                                    nativeEvent.getShiftKey()));
                } else {
//                    final Element element = nativeEvent.getEventTarget().cast();
//
//                    // Expander
//                    if ((ElementUtil.hasClassName(element, expanderClassName, 0, 5))) {
//                        treeModel.toggleOpenState(selectedItem);
//                    } else {
                    final boolean doubleClick = doubleClickTest.test(selectedItem);
                    doSelect(selectedItem,
                            new SelectionType(doubleClick,
                                    false,
                                    allowMultiSelect,
                                    nativeEvent.getCtrlKey(),
                                    nativeEvent.getShiftKey()));
//                    }
                }
            }
        }

        void doSelect(final SelectionItem row, final SelectionType selectionType) {
            if (selectionModel != null) {
//                final Selection<SelectionItem> selection = selectionModel.getSelection();
//
//                if (allowMultiSelect) {
//                    if (row == null) {
//                        multiSelectStart = null;
//                        selection.clear();
//                    } else if (selectionType.isAllowMultiSelect() &&
//                            selectionType.isShiftPressed() &&
//                            multiSelectStart != null) {
//                        // If control isn't pressed as well as shift then we are selecting a new range so clear.
//                        if (!selectionType.isControlPressed()) {
//                            selection.clear();
//                        }
//
//                        final int index1 = rows.indexOf(multiSelectStart);
//                        final int index2 = rows.indexOf(row);
//                        if (index1 != -1 && index2 != -1) {
//                            final int start = Math.min(index1, index2);
//                            final int end = Math.max(index1, index2);
//                            for (int i = start; i <= end; i++) {
//                                selection.setSelected(rows.get(i), true);
//                            }
//                        } else if (selectionType.isControlPressed()) {
//                            multiSelectStart = row;
//                            selection.setSelected(row, !selection.isSelected(row));
//                        } else {
//                            multiSelectStart = row;
//                            selection.setSelected(row);
//                        }
//                    } else if (selectionType.isAllowMultiSelect() && selectionType.isControlPressed()) {
//                        multiSelectStart = row;
//                        selection.setSelected(row, !selection.isSelected(row));
//                    } else {
//                        multiSelectStart = row;
//                        selection.setSelected(row);
//                    }
//
//                    selectionModel.setSelection(selection, selectionType);
//
//                } else if (!selectionModel.isSelected(row)) {
//                    selectionModel.clear();
//                    selectionModel.setSelected(row);
//                }

                if (!selectionModel.isSelected(row)) {
                    selectionModel.clear();
                    selectionModel.setSelected(row);
                }

                if (selectionType.isDoubleSelect()) {
                    if (row != null) {
                        if (row.isHasChildren()) {
                            // Open item.
                            if (model != null &&
                                    model.getNavigationModel() != null &&
                                    model.getNavigationModel().navigate(row)) {
                                model.refresh();
                            }
                        } else {
                            MultiSelectEvent.fire(cellTable, selectionType);
                        }
                    }
                } else {
                    MultiSelectEvent.fire(cellTable, selectionType);
                }
            }
        }
    }

//    private static class SelectionEventManager extends AbstractSelectionEventManager<SelectionItem> {
//
//        private final MySingleSelectionModel<SelectionItem> selectionModel;
//        private final DoubleSelectTester doubleClickTest = new DoubleSelectTester();
//        private SelectionListModel model;
//
//        public SelectionEventManager(final AbstractHasData<SelectionItem> cellTable,
//                                     final MySingleSelectionModel<SelectionItem> selectionModel) {
//            super(cellTable);
//            this.selectionModel = selectionModel;
//        }
//
//        public void setModel(final SelectionListModel model) {
//            this.model = model;
//        }
//
//        @Override
//        protected void onSelect(final CellPreviewEvent<SelectionItem> e) {
//            select(e.getValue());
//        }
//
//        @Override
//        protected void onMouseDown(final CellPreviewEvent<SelectionItem> e) {
//            final NativeEvent nativeEvent = e.getNativeEvent();
//            final SelectionItem value = e.getValue();
//            final int row = cellTable.getVisibleItems().indexOf(value);
//            if (row >= 0) {
//                cellTable.setKeyboardSelectedRow(row, true);
//            } else {
//                cellTable.setKeyboardSelectedRow(cellTable.getKeyboardSelectedRow(), true);
//            }
//
//            if (MouseUtil.isPrimary(nativeEvent)) {
//                select(value);
//                if (doubleClickTest.test(value)) {
//                    if (value != null) {
//                        if (value.isHasChildren()) {
//                            // Open item.
//                            if (model != null &&
//                                    model.getNavigationModel() != null &&
//                                    model.getNavigationModel().navigate(value)) {
//                                model.refresh();
//                            }
//                        } else {
//                            exec(e.getValue());
//                        }
//                    }
//                }
//            }
//
//
////                // We set focus here so that we can use the keyboard to navigate once we have focus.
////                cellTable.setFocus(true);
////
////                final SelectionItem value = e.getValue();
////                select(value);
////
////                // Set current keyboard selection.
////                if (value != null) {
////                    final int row = cellTable.getVisibleItems().indexOf(e.getValue());
////                    if (row >= 0) {
////                        cellTable.setKeyboardSelectedRow(row, true);
////                    }
////                }
////
////                if (doubleSelectTest.test(value)) {
////                    if (value != null) {
////                        if (value.isHasChildren()) {
////                            // Open item.
////                            if (model != null &&
////                                    model.getNavigationModel() != null &&
////                                    model.getNavigationModel().navigate(value)) {
////                                model.refresh();
////                            }
////                        } else {
////                            exec(e.getValue());
////                        }
////                    }
////                }
//        }
//
//        @Override
//        protected void onMoveRight(final CellPreviewEvent<SelectionItem> e) {
//            final SelectionItem value = e.getValue();
//            if (value != null && value.isHasChildren()) {
//                if (model != null &&
//                        model.getNavigationModel() != null &&
//                        model.getNavigationModel().navigate(value)) {
//                    model.refresh();
//                }
//            }
//        }
//
//        @Override
//        protected void onMoveLeft(final CellPreviewEvent<SelectionItem> e) {
//            if (model != null &&
//                    model.getNavigationModel() != null &&
//                    model.getNavigationModel().navigateBack()) {
//                model.refresh();
//            }
//
//
////                if (!openItems.empty()) {
////                    openItems.pop();
////                    model.refresh();
////                }
//        }
//
//        @Override
//        protected void onMoveDown(final CellPreviewEvent<SelectionItem> e) {
//            super.onMoveDown(e);
//            final SelectionItem keyboardSelectedItem = cellTable.getVisibleItem(
//                    cellTable.getKeyboardSelectedRow());
//            select(keyboardSelectedItem);
//        }
//
//        @Override
//        protected void onMoveUp(final CellPreviewEvent<SelectionItem> e) {
//            super.onMoveUp(e);
//            final SelectionItem keyboardSelectedItem = cellTable.getVisibleItem(
//                    cellTable.getKeyboardSelectedRow());
//            select(keyboardSelectedItem);
//        }
//
//        @Override
//        protected void onExecute(final CellPreviewEvent<SelectionItem> e) {
//            exec(e.getValue());
//        }
//
//        private void exec(final SelectionItem row) {
//            if (selectionModel != null) {
//                // Simulate double select.
//                selectionModel.setSelected(row, true);
//                selectionModel.setSelected(row, true);
//            }
//        }
//
//        private void select(final SelectionItem row) {
//            if (selectionModel != null) {
//                if (row == null) {
//                    selectionModel.clear();
//                } else {
//                    selectionModel.setSelected(row, true);
//                }
////        updateDetails(row);
//            }
//        }
//    }


    private void setPath(final List<SelectionItem> path) {
        links.clear();
        if (path.size() == 0) {
            links.add(new Label("Help"));
        } else {
            links.add(createLink("Help", null));
        }
        for (int i = 0; i < path.size(); i++) {
            links.add(new Label("/"));
            final SelectionItem row = path.get(i);
            if (i < path.size() - 1) {
                links.add(createLink(row.getLabel(), row));
            } else {
                links.add(new Label(row.getLabel()));
            }
        }
    }

    private Hyperlink createLink(final String label, final SelectionItem row) {
        final Hyperlink link = new Hyperlink();
        link.setText(label);
        link.addHandler(event -> {
            if (MouseUtil.isPrimary(event)) {
                if (model != null &&
                        model.getNavigationModel() != null &&
                        model.getNavigationModel().navigateBack(row)) {
                    model.refresh();
                }
            }
        }, ClickEvent.getType());
        return link;
    }
}
