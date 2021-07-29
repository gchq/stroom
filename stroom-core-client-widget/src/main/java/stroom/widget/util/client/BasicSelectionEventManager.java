package stroom.widget.util.client;

import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.user.cellview.client.AbstractHasData;
import com.google.gwt.view.client.CellPreviewEvent;

import java.util.List;

public class BasicSelectionEventManager<T> implements CellPreviewEvent.Handler<T> {

    private final AbstractHasData<T> cellTable;

    public BasicSelectionEventManager(final AbstractHasData<T> cellTable) {
        this.cellTable = cellTable;
    }

    @Override
    public void onCellPreview(final CellPreviewEvent<T> e) {
        final NativeEvent nativeEvent = e.getNativeEvent();
        final String type = nativeEvent.getType();
        if ("keydown".equals(type)) {
            // Stop space affecting the scroll position.
            nativeEvent.preventDefault();

            final List<T> items = cellTable.getVisibleItems();
            if (items.size() > 0) {
                final int keyCode = e.getNativeEvent().getKeyCode();
                switch (keyCode) {
                    case KeyCodes.KEY_UP:
                        onUp(e);
                        break;
                    case KeyCodes.KEY_DOWN:
                        onDown(e);
                        break;

                    case KeyCodes.KEY_ENTER:
                    case KeyCodes.KEY_SPACE:
                        onEnter(e);
                        break;
                }
            }

        } else if ("mousedown".equals(type)) {
            // We set focus here so that we can use the keyboard to navigate once we have focus.
            cellTable.setFocus(true);
            final int row = cellTable.getVisibleItems().indexOf(e.getValue());
            if (row >= 0) {
                cellTable.setKeyboardSelectedRow(row);
            }

            if (MouseUtil.isPrimary(nativeEvent)) {
                onEnter(e);
            }
        }
    }

    private void onUp(final CellPreviewEvent<T> e) {
        final int originalRow = cellTable.getKeyboardSelectedRow();
        int row = originalRow - 1;
        row = Math.max(0, row);
        if (row != originalRow) {
            cellTable.setKeyboardSelectedRow(row, true);
        }
    }

    private void onDown(final CellPreviewEvent<T> e) {
        final int originalRow = cellTable.getKeyboardSelectedRow();
        int row = originalRow + 1;
        row = Math.min(cellTable.getVisibleItemCount() - 1, row);
        if (row != originalRow) {
            cellTable.setKeyboardSelectedRow(row, true);
        }
    }

    private void onEnter(final CellPreviewEvent<T> e) {
        final T item = e.getValue();
        cellTable.getSelectionModel().setSelected(item, true);
    }
}
