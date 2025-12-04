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

/*
 * Copyright 2008 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package stroom.widget.datepicker.client;

import stroom.widget.util.client.ElementUtil;

import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.impl.ElementMapperImpl;
import com.google.gwt.user.client.ui.Grid;

import java.util.ArrayList;

/**
 * Highlighting, selectable cell grid. Used to help construct the default
 * calendar view.
 */
public class DateGrid extends Grid {

    private final ElementMapperImpl<AbstractCell> elementToCell = new ElementMapperImpl<>();
    private final ArrayList<DateCell> cellList = new ArrayList<>();
    private DateCell selectedCell;
    private DateCell keyboardSelectedCell;
    private int keyboardSelectionIndex = -1;

    protected DateGrid() {
        sinkEvents(Event.ONCLICK | Event.ONKEYDOWN);
        setCellPadding(0);
        setCellSpacing(0);
        setBorderWidth(0);
        resize(DateTimeModel.WEEKS_IN_MONTH + 1, DateTimeModel.DAYS_IN_WEEK);
        getElement().setTabIndex(0);
    }

    public void addElement(final AbstractCell abstractCell) {
        elementToCell.put(abstractCell);
    }

    public void addCell(final DateCell cell) {
        cellList.add(cell);
    }

    public DateCell getCell(final int i) {
        return cellList.get(i);
    }

    public int getNumCells() {
        return cellList.size();
    }

    @Override
    public void onBrowserEvent(final Event event) {
        if (event.getTypeInt() == Event.ONKEYDOWN) {
            navigate(event.getKeyCode());

        } else if (event.getTypeInt() == Event.ONCLICK) {
            final Element element = event.getEventTarget().cast();
            if (element != null) {
                final Element outer = ElementUtil.findParent(element, "cellOuter", 5);
                if (outer != null) {
                    final AbstractCell cell = elementToCell.get(outer);
                    if (cell instanceof DateCell) {
                        final DateCell dateCell = (DateCell) cell;
                        if (isActive(dateCell)) {
                            setSelected(dateCell);
                        }
                    }
                }
            }
        }
    }

    public void setKeyboardSelectedCell(final int index) {
        if (index != keyboardSelectionIndex) {
            if (keyboardSelectedCell != null) {
                keyboardSelectedCell.setKeyboardSelected(false);
            }
            if (index >= 0 && index < cellList.size()) {
                final DateCell cell = getCell(index);
                if (cell != null) {
                    cell.setKeyboardSelected(true);
                }
                keyboardSelectedCell = cell;
            }
            keyboardSelectionIndex = index;
        }
    }

    public void setKeyboardSelectedCell(final DateCell cell) {
        setKeyboardSelectedCell(cellList.indexOf(cell));
    }

    public final void setSelected(final DateCell cell) {
        setKeyboardSelectedCell(cell);

        final DateCell last = selectedCell;
        selectedCell = cell;

        if (last != null) {
            last.onSelected(false);
        }
        if (selectedCell != null) {
            selectedCell.onSelected(true);
        }
    }

    boolean isActive(final DateCell cell) {
        return cell != null && cell.isEnabled();
    }

    public void navigate(final int keyCode) {
        switch (keyCode) {
            case KeyCodes.KEY_UP: {
                if (keyboardSelectionIndex >= 7) {
                    setKeyboardSelectedCell(keyboardSelectionIndex - 7);
                }
                break;
            }
            case KeyCodes.KEY_DOWN: {
                if (keyboardSelectionIndex < cellList.size() - 7) {
                    setKeyboardSelectedCell(keyboardSelectionIndex + 7);
                }
                break;
            }
            case KeyCodes.KEY_LEFT: {
                int index = keyboardSelectionIndex;
                index = Math.max(0, index - 1);
                setKeyboardSelectedCell(index);
                break;
            }
            case KeyCodes.KEY_RIGHT: {
                int index = keyboardSelectionIndex;
                index = Math.min(cellList.size() - 1, index + 1);
                setKeyboardSelectedCell(index);
                break;
            }
            case KeyCodes.KEY_ENTER: {
                setSelected(keyboardSelectedCell);
                break;
            }
        }
    }
}
