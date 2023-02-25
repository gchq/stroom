package stroom.widget.util.client;

import com.google.gwt.user.cellview.client.AbstractHasData;
import com.google.gwt.view.client.CellPreviewEvent;

import java.util.List;

public abstract class CheckListSelectionEventManager<T>
        extends AbstractSelectionEventManager<T> {

    private final AbstractHasData<T> cellTable;

    public CheckListSelectionEventManager(final AbstractHasData<T> cellTable) {
        super(cellTable);
        this.cellTable = cellTable;
    }

    protected void onToggle(T item) {
    }

    @Override
    protected void onExecute(final CellPreviewEvent<T> e) {
        onSelect(e);
    }

    protected void onSelect(final CellPreviewEvent<T> e) {
        final List<T> items = cellTable.getVisibleItems();
        final int originalRow = cellTable.getKeyboardSelectedRow();
        if (originalRow >= 0 && originalRow < items.size()) {
            final T item = items.get(originalRow);
            onToggle(item);
        }
    }

    @Override
    protected void onMouseDown(final CellPreviewEvent<T> e) {
        final T item = e.getValue();
        if (isSelectable(item)) {
            final int row = cellTable.getVisibleItems().indexOf(item);
            selectRow(row);
            onToggle(item);
        }
    }

    @Override
    protected void onKeyboardSelectRow(final int row, final boolean stealFocus) {
        selectRow(row);
    }

    public void selectRow(final int row) {
        final List<T> items = cellTable.getVisibleItems();
        if (row >= 0 && row < items.size()) {
            final T item = items.get(row);
//            selectionModel.setSelected(item, true);
            cellTable.setKeyboardSelectedRow(row, true);
        }
    }

    public void selectFirstItem() {
        int row = getFirstSelectableRow();
        if (row >= 0) {
            selectRow(row);
        }
    }

    private int getFirstSelectableRow() {
        return 0;
    }
}
