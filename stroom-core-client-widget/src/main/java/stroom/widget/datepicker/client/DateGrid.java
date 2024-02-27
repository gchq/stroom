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
import com.google.gwt.user.client.DOM;
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

    protected DateGrid() {
        sinkEvents(Event.ONCLICK);
        setCellPadding(0);
        setCellSpacing(0);
        setBorderWidth(0);
        resize(CalendarModel.WEEKS_IN_MONTH + 1, CalendarModel.DAYS_IN_WEEK);
    }

    public void addElement(final AbstractCell abstractCell) {
        elementToCell.put(abstractCell);
    }

    public void addCell(final DateCell cell) {
        cellList.add(cell);
    }

    public DateCell getCell(int i) {
        return cellList.get(i);
    }

    public int getNumCells() {
        return cellList.size();
    }

    @Override
    public void onBrowserEvent(Event event) {
        final Element element = event.getEventTarget().cast();
        if (element != null) {
            final Element outer = ElementUtil.findMatching(element, "cellOuter", 0, 5);
            if (outer != null) {
                final AbstractCell cell = elementToCell.get(outer);
                if (cell instanceof DateCell) {
                    final DateCell dateCell = (DateCell) cell;
                    if (DOM.eventGetType(event) == Event.ONCLICK) {
                        if (isActive(dateCell)) {
                            setSelected(dateCell);
                        }
                    }
                }
            }
        }
    }

    public final void setSelected(DateCell cell) {
        DateCell last = selectedCell;
        selectedCell = cell;

        if (last != null) {
            last.onSelected(false);
        }
        if (selectedCell != null) {
            selectedCell.onSelected(true);
        }
    }

    boolean isActive(DateCell cell) {
        return cell != null && cell.isEnabled();
    }


//    public void verticalNavigation(int keyCode) {
//        switch (keyCode) {
//            case KeyCodes.KEY_UP:
//                setHighlighted(previousItem());
//                break;
//            case KeyCodes.KEY_DOWN:
//                setHighlighted(nextItem());
//                break;
//            case KeyCodes.KEY_ENTER:
//                setSelected(this);
//                break;
//        }
//    }
//
//    private DateCell nextItem() {
//        if (index == getLastIndex()) {
//            return getCell(0);
//        } else {
//            return getCell(index + 1);
//        }
//    }
//
//    private DateCell previousItem() {
//        if (index != 0) {
//            return getCell(index - 1);
//        } else {
//            return getCell(getLastIndex());
//        }
//    }
}
