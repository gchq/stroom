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
import com.google.gwt.view.client.CellPreviewEvent;

import java.util.List;

public class SelectionList<T, I extends SelectionItem> extends Composite {

    private final QuickFilter quickFilter;
    private final FlowPanel links;
    private final PagerViewImpl pagerView;
    private final CellTable<I> cellTable;
    private SelectionListModel<T, I> model;

    private final SelectionEventManager<T, I> selectionEventManager;
    private final MultiSelectionModelImpl<I> selectionModel;

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
        cellTable = new MyCellTable<I>(100) {
            @Override
            public void setRowData(final int start, final List<? extends I> values) {
                super.setRowData(start, values);

                links.setVisible(model.displayPath());
                pagerView.setPagerVisible(model.displayPager());

                // Only update the path when we get data.
                if (model != null && model.getNavigationModel() != null) {
                    setPath(model.getNavigationModel().getPath());
                }
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
        selectionEventManager = new SelectionEventManager<>(cellTable, selectionModel);
        cellTable.setSelectionModel(selectionModel, selectionEventManager);

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

    public void init(final SelectionListModel<T, I> model) {
        if (this.model != null) {
            throw new RuntimeException("Already initialised.");
        }
        if (model.getDataProvider().getDataDisplays().size() > 0) {
            throw new RuntimeException("Display already attached.");
        }

        this.model = model;
        selectionEventManager.setModel(model);
        links.setVisible(model.displayPath());
        pagerView.setPagerVisible(model.displayPager());

        model.getDataProvider().addDataDisplay(cellTable);
        model.refresh();
    }

    public void destroy() {
        if (model != null) {
            model.getDataProvider().removeDataDisplay(cellTable);
            model.reset();
        }
    }

    public MultiSelectionModel<I> getSelectionModel() {
        return selectionModel;
    }

    private static class SelectionEventManager<T, I extends SelectionItem>
            extends AbstractSelectionEventManager<I> {

        private final MultiSelectionModelImpl<I> selectionModel;
        private final DoubleSelectTester doubleClickTest = new DoubleSelectTester();
        private final boolean allowMultiSelect = false;
        private SelectionListModel<T, I> model;

        public SelectionEventManager(final AbstractHasData<I> cellTable,
                                     final MultiSelectionModelImpl<I> selectionModel) {
            super(cellTable);
            this.selectionModel = selectionModel;
        }

        public void setModel(final SelectionListModel<T, I> model) {
            this.model = model;
        }

        @Override
        protected void onMoveRight(final CellPreviewEvent<I> e) {
            final I value = e.getValue();
            if (value != null && value.isHasChildren()) {
                if (model != null &&
                        model.getNavigationModel() != null &&
                        model.getNavigationModel().navigate(value)) {
                    model.refresh();
                }
            }
        }

        @Override
        protected void onMoveLeft(final CellPreviewEvent<I> e) {
            if (model != null &&
                    model.getNavigationModel() != null &&
                    model.getNavigationModel().navigateBack()) {
                model.refresh();
            }
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
                    // Open item.
                    if (model != null &&
                            model.getNavigationModel() != null &&
                            model.getNavigationModel().navigate(row)) {
                        model.refresh();
                    }
                } else {
                    setKeyboardSelection(row);
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
    }

    private void setPath(final List<I> path) {
        links.clear();
        if (path.size() == 0) {
            links.add(new Label(model.getPathRoot()));
        } else {
            links.add(createLink(model.getPathRoot(), null));
        }
        for (int i = 0; i < path.size(); i++) {
            links.add(new Label("/"));
            final I row = path.get(i);
            if (i < path.size() - 1) {
                links.add(createLink(row.getLabel(), row));
            } else {
                links.add(new Label(row.getLabel()));
            }
        }
    }

    private Hyperlink createLink(final String label, final I row) {
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
