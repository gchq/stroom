package stroom.widget.util.client;

import stroom.widget.util.client.KeyBinding.Action;

import com.google.gwt.dom.client.BrowserEvents;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.user.cellview.client.AbstractHasData;
import com.google.gwt.view.client.CellPreviewEvent;

import java.util.List;

public abstract class AbstractSelectionEventManager<T>
        implements CellPreviewEvent.Handler<T> {

    private static final int PAGE_SIZE = 10;

    protected final AbstractHasData<T> cellTable;

    public AbstractSelectionEventManager(final AbstractHasData<T> cellTable) {
        this.cellTable = cellTable;
    }

    @Override
    public void onCellPreview(final CellPreviewEvent<T> event) {
        final NativeEvent nativeEvent = event.getNativeEvent();
        final String type = nativeEvent.getType();
        if (BrowserEvents.KEYDOWN.equals(type)) {
            final Action action = KeyBinding.getAction(nativeEvent);
            final List<T> items = cellTable.getVisibleItems();
            if (action != null && items.size() > 0) {
                switch (action) {
                    case MOVE_UP:
                        onMoveUp(event);
                        handledEvent(event);
                        break;
                    case MOVE_DOWN:
                        onMoveDown(event);
                        handledEvent(event);
                        break;
                    case MOVE_PAGE_DOWN:
                        onMovePageDown(event);
                        handledEvent(event);
                        break;
                    case MOVE_PAGE_UP:
                        handledEvent(event);
                        break;
                    case MOVE_START:
                        onMoveStart(event);
                        handledEvent(event);
                        break;
                    case MOVE_END:
                        onMoveEnd(event);
                        handledEvent(event);
                        break;
                    case MOVE_RIGHT:
                        onMoveRight(event);
                        handledEvent(event);
                        break;
                    case MOVE_LEFT:
                        onMoveLeft(event);
                        handledEvent(event);
                        break;
                    case CLOSE:
                        onClose(event);
                        handledEvent(event);
                        break;
                    case EXECUTE:
                        onExecute(event);
                        handledEvent(event);
                        break;
                    case SELECT:
                        onSelect(event);
                        handledEvent(event);
                        break;
                    case SELECT_ALL:
                        onSelectAll(event);
                        handledEvent(event);
                        break;
                    case MENU:
                        onMenu(event);
                        handledEvent(event);
                        break;
                }
            }

        } else if (BrowserEvents.MOUSEDOWN.equals(type)) {
            onMouseDown(event);

        } else if (BrowserEvents.MOUSEMOVE.equals(type)) {
            onMouseMove(event);

        } else if (BrowserEvents.BLUR.equals(type)) {
            onBlur(event);
        }
    }

    void handledEvent(CellPreviewEvent<
            ?> event) {
//        event.setCanceled(true);
        event.getNativeEvent().preventDefault();
    }

    protected void onMoveUp(final CellPreviewEvent<T> e) {
        move(e, cellTable.getKeyboardSelectedRow() - 1, -1, 1);
    }

    protected void onMoveDown(final CellPreviewEvent<T> e) {
        move(e, cellTable.getKeyboardSelectedRow() + 1, 1, 1);
    }

    protected void onMovePageUp(final CellPreviewEvent<T> e) {
        move(e,
                cellTable.getKeyboardSelectedRow() - PAGE_SIZE,
                -1,
                PAGE_SIZE);
    }

    protected void onMovePageDown(final CellPreviewEvent<T> e) {
        move(e,
                cellTable.getKeyboardSelectedRow() + PAGE_SIZE,
                1,
                PAGE_SIZE);
    }

    protected void onMoveStart(final CellPreviewEvent<T> e) {
        move(e, 0, 1, 1);
    }

    protected void onMoveEnd(final CellPreviewEvent<T> e) {
        move(e, cellTable.getVisibleItemCount() - 1, -1, 1);
    }

    private void move(final CellPreviewEvent<T> e, final int start, final int delta, final int page) {
        final int originalRow = cellTable.getKeyboardSelectedRow();
        final List<T> items = cellTable.getVisibleItems();
        int row = originalRow;

        // Ensure start is within bounds.
        int pos = start;
        pos = Math.max(pos, 0);
        pos = Math.min(pos, items.size() - 1);

        for (int i = 0; i < page; i++) {
            for (; pos >= 0 && pos < items.size(); pos += delta) {
                final T item = items.get(pos);
                if (isSelectable(item)) {
                    row = pos;
                    break;
                }
            }
        }

        if (row != originalRow) {
            onKeyboardSelectRow(row, true);
        }
    }


    protected void onMoveRight(final CellPreviewEvent<T> e) {
    }

    protected void onMoveLeft(final CellPreviewEvent<T> e) {
    }

    protected void onClose(final CellPreviewEvent<T> e) {
    }

    protected void onExecute(final CellPreviewEvent<T> e) {
    }

    protected void onSelect(final CellPreviewEvent<T> e) {
    }

    protected void onMenu(final CellPreviewEvent<T> e) {
    }

    protected void onSelectAll(final CellPreviewEvent<T> e) {
    }

    protected void onMouseDown(final CellPreviewEvent<T> e) {
        // We set focus here so that we can use the keyboard to navigate once we have focus.
        cellTable.setFocus(true);

        final T item = e.getValue();
        if (isSelectable(item)) {
            final int row = cellTable.getVisibleItems().indexOf(e.getValue());
            if (row >= 0) {
                cellTable.setKeyboardSelectedRow(row);
            }

            if (MouseUtil.isPrimary(e.getNativeEvent())) {
                onExecute(e);
            }
        }
    }

    protected void onMouseMove(final CellPreviewEvent<T> e) {
    }

    protected void onBlur(final CellPreviewEvent<T> e) {
    }

    protected boolean isSelectable(final T item) {
        return item != null;
    }

    protected void onKeyboardSelectRow(final int row, final boolean stealFocus) {
        cellTable.setKeyboardSelectedRow(row, stealFocus);
    }
}
