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
import stroom.alert.client.event.FireAlertEventFunction;
import stroom.query.api.ParamValues;
import stroom.query.api.TimeRange;
import stroom.query.client.presenter.QueryToolbarPresenter.QueryToolbarView;
import stroom.query.client.view.QueryButtons;
import stroom.query.client.view.TimeRanges;
import stroom.util.shared.ErrorMessage;
import stroom.util.shared.ErrorMessages;
import stroom.util.shared.Severity;

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

    private ErrorMessages currentErrors;
    private TimeRange currentTimeRange = TimeRanges.ALL_TIME;

    @Inject
    public QueryToolbarPresenter(final EventBus eventBus,
                                 final QueryToolbarView view) {
        super(eventBus, view);
        getView().setErrorsVisible(false);
        view.setUiHandlers(this);
        view.getQueryButtons().setUiHandlers(this);
    }

    @Override
    public void onError(final List<ErrorMessage> errors) {
        currentErrors = new ErrorMessages(errors);
        getView().setErrorsVisible(!currentErrors.isEmpty());
        if (!currentErrors.isEmpty()) {
            getView().setErrorSeverity(currentErrors.getHighestSeverity());
        }
    }

    @Override
    public void showErrors() {
        if (!currentErrors.isEmpty()) {
            if (currentErrors.containsAny(Severity.FATAL_ERROR, Severity.ERROR)) {
                fireAlertEvent(AlertEvent::fireError, "error", Severity.FATAL_ERROR, Severity.ERROR);
            } else if (currentErrors.containsAny(Severity.WARNING)) {
                fireAlertEvent(AlertEvent::fireWarn, "warning", Severity.WARNING);
            } else if (currentErrors.containsAny(Severity.INFO)) {
                fireAlertEvent(AlertEvent::fireInfo, "message", Severity.INFO);
            }
        }
    }

    private void fireAlertEvent(final FireAlertEventFunction fireAlertEventFunction,
                                final String messageType, final Severity...severity) {
        final List<String> messages = currentErrors.get(severity);
        final String msg = getMessage(messageType, messages.size());
        final String errorMessages = String.join("\n", messages);
        fireAlertEventFunction.apply(this, msg, errorMessages, null);
    }

    private String getMessage(final String messageType, final int numberOfMessages) {
        return numberOfMessages == 1
                ? ("The following " + messageType + " was created while running this search:")
                : ("The following " + numberOfMessages
                   + " " + messageType + "s have been created while running this search:");
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

        void setErrorsVisible(boolean show);

        void setErrorSeverity(Severity severity);

        QueryButtons getQueryButtons();

        TimeRange getTimeRange();

        void setTimeRange(TimeRange timeRange);

        void setParamValues(ParamValues paramValues);
    }
}
