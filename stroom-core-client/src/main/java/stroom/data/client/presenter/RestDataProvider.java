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

package stroom.data.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.dispatch.client.RestErrorHandler;
import stroom.entity.client.presenter.TreeRowHandler;
import stroom.util.shared.ResultPage;

import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.gwt.view.client.AsyncDataProvider;
import com.google.gwt.view.client.HasData;
import com.google.gwt.view.client.Range;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;

public abstract class RestDataProvider<R, T extends ResultPage<R>> extends AsyncDataProvider<R> implements HasHandlers {

    private final EventBus eventBus;

    private Range requestedRange;
    private boolean fetching;
    private boolean refetch;
    private TreeRowHandler<R> treeRowHandler;

    public RestDataProvider(final EventBus eventBus) {
        this.eventBus = eventBus;
    }

    @Override
    protected void onRangeChanged(final HasData<R> display) {
        fetch(display.getVisibleRange(), false);
    }

    private void fetch(final Range range, final boolean force) {
        if (range != null) {
            requestedRange = range;

            if (!fetching) {
                fetching = true;
                doFetch(range);
            } else if (force) {
                refetch = true;
            }
        }
    }

    private void doFetch(final Range range) {
        exec(range,
                resultList -> {
                    if (requestedRange.equals(range) && !refetch) {
                        if (resultList != null) {
                            changeData(resultList);
                        }
                        fetching = false;
                    } else {
                        refetch = false;
                        doFetch(requestedRange);
                    }
                }, error -> {
                    fetching = false;
                    refetch = false;

                    AlertEvent.fireErrorFromException(this, error.getException(), null);
                });
    }

    protected abstract void exec(final Range range,
                                 final Consumer<T> dataConsumer,
                                 final RestErrorHandler restErrorHandler);

    @Override
    public void fireEvent(final GwtEvent<?> event) {
        eventBus.fireEvent(event);
    }

    protected void changeData(final T data) {
        if (treeRowHandler != null) {
            treeRowHandler.handle(data.getValues());
        }

        updateRowData(getStart(data), data.getValues());
        updateRowCount(getSize(data), data.getPageResponse().isExact());
    }

    public void refresh() {
        fetch(requestedRange, true);
    }

    public void setTreeRowHandler(final TreeRowHandler<R> treeRowHandler) {
        this.treeRowHandler = treeRowHandler;
    }

    private int getStart(final T data) {
        return (int) data.getPageResponse().getOffset();
    }

    public int getSize(final T data) {
        if (data.getPageResponse().getTotal() == null) {
            return getStart(data) + data.size();
        }
        return data.getPageResponse().getTotal().intValue();
    }
}
