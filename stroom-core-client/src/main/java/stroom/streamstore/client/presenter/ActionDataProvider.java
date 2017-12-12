/*
 * Copyright 2016 Crown Copyright
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

package stroom.streamstore.client.presenter;

import com.google.gwt.view.client.AsyncDataProvider;
import com.google.gwt.view.client.HasData;
import com.google.gwt.view.client.Range;
import stroom.alert.client.event.AlertEvent;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.dispatch.client.ClientDispatchAsyncImpl;
import stroom.entity.client.presenter.TreeRowHandler;
import stroom.entity.shared.Action;
import stroom.entity.shared.BaseCriteria;
import stroom.entity.shared.HasCriteria;
import stroom.entity.shared.HasIsConstrained;
import stroom.entity.shared.ResultList;
import stroom.util.shared.SharedObject;

import java.util.ArrayList;

public class ActionDataProvider<R extends SharedObject> extends AsyncDataProvider<R> {
    private final ClientDispatchAsync dispatcher;
    private final Action<ResultList<R>> action;
    private Range requestedRange;
    private boolean fetching;
    private int fetchCount;
    private boolean refetch;
    private boolean allowNoConstraint = true;
    private TreeRowHandler<R> treeRowHandler;

    public ActionDataProvider(final ClientDispatchAsync dispatcher, final Action<ResultList<R>> action) {
        this.dispatcher = dispatcher;
        this.action = action;
    }

    public void setAllowNoConstraint(boolean allowNoConstraint) {
        this.allowNoConstraint = allowNoConstraint;
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

    private boolean checkNoConstraint() {
        if (allowNoConstraint) {
            return true;
        }

        boolean noConstraint = false;

        if (action instanceof HasCriteria) {
            final BaseCriteria criteria = ((HasCriteria<?>) action).getCriteria();

            if (criteria == null) {
                noConstraint = true;
            } else {
                if (criteria instanceof HasIsConstrained) {
                    if (!((HasIsConstrained) criteria).isConstrained()) {
                        noConstraint = true;
                    }
                }
            }
        }
        if (action instanceof HasIsConstrained) {
            if (!((HasIsConstrained) action).isConstrained()) {
                noConstraint = true;
            }
        }

        if (noConstraint) {
            updateRowData(0, new ArrayList<>());
            updateRowCount(0, true);
            fetching = false;
            return false;
        }
        return true;
    }

    private void doFetch(final Range range) {
        // No Criteria ... just produce no results.
        if (!checkNoConstraint()) {
            return;
        }

        if (action instanceof HasCriteria) {
            final HasCriteria<?> hasCriteria = (HasCriteria<?>) action;
            final BaseCriteria criteria = hasCriteria.getCriteria();

            criteria.obtainPageRequest().setOffset((long) range.getStart());
            criteria.obtainPageRequest().setLength(range.getLength());
        }

        fetchCount++;
        dispatcher.exec(action).onSuccess(resultList -> {
            if (requestedRange.equals(range) && !refetch) {
                if (resultList != null) {
                    changeData(resultList);
                }
                fetching = false;
            } else {
                refetch = false;
                doFetch(requestedRange);
            }
        }).onFailure(caught -> {
            fetching = false;
            refetch = false;

            AlertEvent.fireErrorFromException((ClientDispatchAsyncImpl) dispatcher, caught, null);
        });
    }

    protected void changeData(final ResultList<R> data) {
        if (treeRowHandler != null) {
            treeRowHandler.handle(data.getValues());
        }

        updateRowData(data.getStart(), data.getValues());
        updateRowCount(data.getSize(), data.isExact());
    }

    public void refresh() {
        fetch(requestedRange, true);
    }

    public int getFetchCount() {
        return fetchCount;
    }

    public void setTreeRowHandler(final TreeRowHandler<R> treeRowHandler) {
        this.treeRowHandler = treeRowHandler;
    }
}
