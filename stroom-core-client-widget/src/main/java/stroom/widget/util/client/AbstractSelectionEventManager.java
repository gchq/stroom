package stroom.widget.util.client;

import com.google.gwt.dom.client.BrowserEvents;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.user.cellview.client.AbstractHasData;
import com.google.gwt.view.client.CellPreviewEvent;

import java.util.List;

public abstract class AbstractSelectionEventManager<T>
        implements CellPreviewEvent.Handler<T> {

    private int pageSize = 10;

    private final AbstractHasData<T> cellTable;

    public AbstractSelectionEventManager(final AbstractHasData<T> cellTable) {
        this.cellTable = cellTable;
    }

    @Override
    public void onCellPreview(final CellPreviewEvent<T> event) {
        final NativeEvent nativeEvent = event.getNativeEvent();
        final String type = nativeEvent.getType();
        if (BrowserEvents.KEYDOWN.equals(type)) {
            final List<T> items = cellTable.getVisibleItems();

            if (items.size() > 0) {
                final int keyCode = nativeEvent.getKeyCode();
                switch (keyCode) {
                    case KeyCodes.KEY_UP:
                        onUp(event);
                        handledEvent(event);
                        break;
                    case KeyCodes.KEY_DOWN:
                        onDown(event);
                        handledEvent(event);
                        break;
                    case KeyCodes.KEY_PAGEDOWN:
                        onPageDown(event);
                        handledEvent(event);
                        return;
                    case KeyCodes.KEY_PAGEUP:
                        onPageUp(event);
                        handledEvent(event);
                        return;
                    case KeyCodes.KEY_HOME:
                        onHome(event);
                        handledEvent(event);
                        return;
                    case KeyCodes.KEY_END:
                        onEnd(event);
                        handledEvent(event);
                        return;
                    case KeyCodes.KEY_RIGHT:
                        onRight(event);
                        handledEvent(event);
                        break;
                    case KeyCodes.KEY_LEFT:
                        onLeft(event);
                        handledEvent(event);
                        break;
                    case KeyCodes.KEY_ESCAPE:
                        onEscape(event);
                        handledEvent(event);
                        break;
                    case KeyCodes.KEY_ENTER:
                        onEnter(event);
                        handledEvent(event);
                        break;
                    case KeyCodes.KEY_SPACE:
                        onSpace(event);
                        handledEvent(event);
                        break;
                    case KeyCodes.KEY_ALT:
                        onAlt(event);
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

    void handledEvent(CellPreviewEvent<?> event) {
//        event.setCanceled(true);
        event.getNativeEvent().preventDefault();
    }

    protected void onUp(final CellPreviewEvent<T> e) {
        move(e, cellTable.getKeyboardSelectedRow() - 1, -1, 1);
    }

    protected void onDown(final CellPreviewEvent<T> e) {
        move(e, cellTable.getKeyboardSelectedRow() + 1, 1, 1);
    }

    protected void onPageUp(final CellPreviewEvent<T> e) {
        move(e, cellTable.getKeyboardSelectedRow() - pageSize, -1, pageSize);
    }

    protected void onPageDown(final CellPreviewEvent<T> e) {
        move(e, cellTable.getKeyboardSelectedRow() + pageSize, 1, pageSize);
    }

    protected void onHome(final CellPreviewEvent<T> e) {
        move(e, 0, 1, 1);
    }

    protected void onEnd(final CellPreviewEvent<T> e) {
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


    protected void onRight(final CellPreviewEvent<T> e) {
    }

    protected void onLeft(final CellPreviewEvent<T> e) {
    }

    protected void onEscape(final CellPreviewEvent<T> e) {
    }

    protected void onEnter(final CellPreviewEvent<T> e) {
    }

    protected void onSpace(final CellPreviewEvent<T> e) {
    }

    protected void onAlt(final CellPreviewEvent<T> e) {
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
                onEnter(e);
            }
        }
    }

    protected void onMouseMove(final CellPreviewEvent<T> e) {
    }

    protected void onBlur(final CellPreviewEvent<T> e) {
    }

    protected boolean isSelectable(final T item) {
        return true;
    }

    protected void onKeyboardSelectRow(final int row, final boolean stealFocus) {
        cellTable.setKeyboardSelectedRow(row, stealFocus);
    }
}
