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

package stroom.config.global.client.presenter;

import stroom.entity.client.presenter.TreeRowHandler;

import com.google.gwt.view.client.AsyncDataProvider;
import com.google.gwt.view.client.HasData;
import com.google.gwt.view.client.Range;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ListDataProvider<R> extends AsyncDataProvider<R> {

    private Consumer<Range> listUpdater;
    private List<R> list;
    private Range requestedRange;
    private TreeRowHandler<R> treeRowHandler;
    private int totalSize;
    private boolean isFullList;

    @Override
    protected void onRangeChanged(final HasData<R> display) {
        fetch(display.getVisibleRange(), true);
    }

    public void setCompleteList(final List<R> list) {
        this.list = list;
        this.isFullList = true;
        if (this.requestedRange == null) {
            this.requestedRange = new Range(0, list.size());
        }
        this.totalSize = list.size();
        redraw(requestedRange);
    }

    //    public void setPartialList(final List<R> list, final Range range, final int totalSize) {
//        this.list = list;
//        this.requestedRange = range;
//        this.totalSize = totalSize;
//    }
    public void setPartialList(final List<R> partialList, final int totalSize) {
        this.list = partialList;
        this.isFullList = false;
        if (this.requestedRange == null) {
            this.requestedRange = new Range(0, partialList.size());
        }
        this.totalSize = totalSize;
        redraw(requestedRange);
    }

    /**
     * @param listUpdater This consumer is expected to get new data and
     *                    call setCompleteList or setPartialList with the new data
     */
    public void setListUpdater(final Consumer<Range> listUpdater) {
        this.listUpdater = listUpdater;
    }

    public List<R> getList() {
        return list;
    }

    public Range getRequestedRange() {
        return requestedRange;
    }

    private void fetch(final Range range, final boolean isRedrawRequired) {
        if (range != null) {
            requestedRange = range;

            if (listUpdater != null) {
                listUpdater.accept(range);
            }

            if (isRedrawRequired) {
                redraw(range);
            }
        }
    }

    private void redraw(final Range range) {
        if (list != null) {
            final List<R> subList;
            if (isFullList) {
                // list is ALL the data so make a sub list from the range
                subList = new ArrayList<>();
                for (int i = range.getStart(); i < (range.getStart() + range.getLength()) && i < list.size(); i++) {
                    subList.add(list.get(i));
                }
            } else {
                // list is not all the data so already represents a range, so use it as is
                subList = list;
            }
            if (treeRowHandler != null) {
                treeRowHandler.handle(list);
            }

            updateRowData(range.getStart(), subList);
            updateRowCount(totalSize, true);
        }
    }

    public void redraw() {
        redraw(requestedRange);
    }

    public void refresh(final boolean isRedrawRequired) {
        fetch(requestedRange, isRedrawRequired);
    }

    public TreeRowHandler<R> getTreeRowHandler() {
        return treeRowHandler;
    }

    public void setTreeRowHandler(final TreeRowHandler<R> treeRowHandler) {
        this.treeRowHandler = treeRowHandler;
    }
}
