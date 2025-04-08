/*
 * Copyright 2022 Crown Copyright
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
 *
 */

package stroom.query.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.query.api.ParamValues;
import stroom.query.api.TimeRange;
import stroom.query.client.presenter.QueryToolbarPresenter.QueryToolbarView;
import stroom.query.client.view.QueryButtons;
import stroom.query.client.view.TimeRanges;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.List;

public class QueryToolbarPresenter
        extends MyPresenterWidget<QueryToolbarView>
        implements
        QueryToolbarUiHandlers,
        QueryUiHandlers,
        SearchStateListener,
        SearchErrorListener {

    private List<String> currentWarnings;
    private TimeRange currentTimeRange = TimeRanges.ALL_TIME;

    @Inject
    public QueryToolbarPresenter(final EventBus eventBus,
                                 final QueryToolbarView view) {
        super(eventBus, view);
        getView().setWarningsVisible(false);
        view.setUiHandlers(this);
        view.getQueryButtons().setUiHandlers(this);
    }

    @Override
    public void onError(final List<String> errors) {
        currentWarnings = errors;
        getView().setWarningsVisible(currentWarnings != null && !currentWarnings.isEmpty());
    }

    @Override
    public void showWarnings() {
        if (currentWarnings != null && !currentWarnings.isEmpty()) {
            final String msg = currentWarnings.size() == 1
                    ? ("The following warning was created while running this search:")
                    : ("The following " + currentWarnings.size()
                       + " warnings have been created while running this search:");
            final String errors = String.join("\n", currentWarnings);
            AlertEvent.fireWarn(this, msg, errors, null);
        }
    }

    @Override
    public void onTimeRange(final TimeRange timeRange) {
        if (!currentTimeRange.equals(timeRange)) {
            currentTimeRange = timeRange;
            TimeRangeChangeEvent.fire(this, timeRange);
        }
    }

    public void setTimeRange(final TimeRange timeRange) {
        if (timeRange == null) {
            currentTimeRange = TimeRanges.ALL_TIME;
        } else {
            currentTimeRange = timeRange;
        }
        getView().setTimeRange(currentTimeRange);
    }

    @Override
    public void start() {
        StartQueryEvent.fire(this);
    }

    @Override
    public void onSearching(final boolean searching) {
        getView().getQueryButtons().onSearching(searching);
    }

    public void setEnabled(final boolean enabled) {
        getView().getQueryButtons().setEnabled(enabled);
    }

    public TimeRange getTimeRange() {
        return currentTimeRange;
    }

    public void setParamValues(final ParamValues paramValues) {
        getView().setParamValues(paramValues);
    }

    public HandlerRegistration addStartQueryHandler(final StartQueryEvent.Handler handler) {
//        return addHandlerToSource(StartQueryEvent.getType(), handler);

        return addHandlerToSource(StartQueryEvent.getType(), handler);
    }

    public HandlerRegistration addTimeRangeChangeHandler(final TimeRangeChangeEvent.Handler handler) {
        return addHandlerToSource(TimeRangeChangeEvent.getType(), handler);
    }


    // --------------------------------------------------------------------------------


    public interface QueryToolbarView extends View, HasUiHandlers<QueryToolbarUiHandlers> {

        void setWarningsVisible(boolean show);

        QueryButtons getQueryButtons();

        TimeRange getTimeRange();

        void setTimeRange(TimeRange timeRange);

        void setParamValues(ParamValues paramValues);
    }
}
