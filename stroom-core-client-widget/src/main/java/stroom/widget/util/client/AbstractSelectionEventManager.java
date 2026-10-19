/*
 * Copyright 2021 Crown Copyright
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

package stroom.widget.util.client;

import stroom.widget.util.client.KeyBinding.Action;

import com.google.gwt.dom.client.BrowserEvents;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.user.cellview.client.AbstractHasData;
import com.google.gwt.view.client.CellPreviewEvent;

import java.util.List;
import java.util.function.Consumer;

public abstract class AbstractSelectionEventManager<T>
        implements CellPreviewEvent.Handler<T> {

    private static final int PAGE_SIZE = 10;

    protected final AbstractHasData<T> cellTable;

    // Optional behaviour for a list with a filter above it, e.g. a picker's dialog
    private Runnable moveUpFromFirst;
    private Consumer<String> typedHandler;
    private boolean selectionFollowsFocus;

    public AbstractSelectionEventManager(final AbstractHasData<T> cellTable) {
        this.cellTable = cellTable;
    }

    /// Sets what Up on the first row does, e.g. moves the focus back to a filter above the list.
    ///
    /// @param moveUpFromFirst What to do, or null for nothing (the first row keeps the focus).
    public void setMoveUpFromFirst(final Runnable moveUpFromFirst) {
        this.moveUpFromFirst = moveUpFromFirst;
    }

    /// Sends the characters typed in the list somewhere else, e.g. to a filter above it. Space
    /// still chooses the row.
    ///
    /// @param typedHandler Given each character typed, or null to leave typing to the list.
    public void setTypedHandler(final Consumer<String> typedHandler) {
        this.typedHandler = typedHandler;
    }

    /// Sets whether moving to a row with the keyboard also chooses it, as in a list that chooses
    /// one item (a picker's), rather than only moving a highlight that Space chooses.
    ///
    /// @param selectionFollowsFocus Whether the selection follows the focus.
    public void setSelectionFollowsFocus(final boolean selectionFollowsFocus) {
        this.selectionFollowsFocus = selectionFollowsFocus;
    }

    @Override
    public void onCellPreview(final CellPreviewEvent<T> event) {
        final NativeEvent nativeEvent = event.getNativeEvent();
        final String type = nativeEvent.getType();

        if (isKeyEvent(type) && RowActions.isRowActionEvent(nativeEvent)) {
            // A button or link in a cell has the focus: its keys are its own, not the table's
            if (BrowserEvents.KEYDOWN.equals(type)) {
                onRowActionKeyDown(event);
            }
            return;
        }

        if (typedHandler != null && BrowserEvents.KEYDOWN.equals(type)) {
            final String key = TypedKey.keyOf(nativeEvent);
            if (TypedKey.isTypedCharacter(nativeEvent, key) && !" ".equals(key)) {
                handledEvent(event);
                typedHandler.accept(key);
                return;
            }
        }

//        GWT.log("AbstractSelectionEventManager calling cellPreview test");
        final Action action = KeyBinding.test(nativeEvent);
        if (action != null) {
            final List<T> items = cellTable.getVisibleItems();
            if (!items.isEmpty()) {
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

    void handledEvent(final CellPreviewEvent<
            ?> event) {
//        event.setCanceled(true);
//        GWT.log("Stopping propagation");
        event.getNativeEvent().stopPropagation();
        event.getNativeEvent().preventDefault();
    }

    protected void onMoveUp(final CellPreviewEvent<T> e) {
        if (moveUpFromFirst != null && cellTable.getKeyboardSelectedRow() <= 0) {
            moveUpFromFirst.run();
        } else {
            move(e, cellTable.getKeyboardSelectedRow() - 1, -1, 1);
        }
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
            if (selectionFollowsFocus) {
                onSelectionFollowsFocus(items.get(row));
            }
        }
    }

    /// Chooses the row the keyboard has moved to, when the selection follows the focus (see
    /// [#setSelectionFollowsFocus(boolean)]). Does nothing unless overridden.
    ///
    /// @param item The row's item.
    protected void onSelectionFollowsFocus(final T item) {
    }


    private static boolean isKeyEvent(final String type) {
        return BrowserEvents.KEYDOWN.equals(type)
               || BrowserEvents.KEYPRESS.equals(type)
               || BrowserEvents.KEYUP.equals(type);
    }

    /// Called for a key pressed on a row action (see [RowActions]), e.g. Enter on a link in a
    /// cell, which the table otherwise leaves alone. A cell's buttons act on their own keys in
    /// their cell; this is for row actions that no cell handles.
    ///
    /// @param e The key down event.
    protected void onRowActionKeyDown(final CellPreviewEvent<T> e) {
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
