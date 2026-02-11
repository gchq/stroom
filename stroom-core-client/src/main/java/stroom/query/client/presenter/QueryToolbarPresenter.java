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

package stroom.query.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.FireAlertEventFunction;
import stroom.core.client.messages.ErrorMessageTemplates;
import stroom.query.api.ParamValues;
import stroom.query.api.TimeRange;
import stroom.query.client.presenter.QueryToolbarPresenter.QueryToolbarView;
import stroom.query.client.view.QueryButtons;
import stroom.query.client.view.TimeRanges;
import stroom.util.shared.ErrorMessage;
import stroom.util.shared.ErrorMessages;
import stroom.util.shared.Severity;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.List;
import java.util.stream.Collectors;

public class QueryToolbarPresenter
        extends MyPresenterWidget<QueryToolbarView>
        implements
        QueryToolbarUiHandlers,
        QueryUiHandlers,
        SearchStateListener,
        SearchErrorListener {

    private ErrorMessages currentErrors;
    private TimeRange currentTimeRange = TimeRanges.ALL_TIME;

    private static final ErrorMessageTemplates ERROR_MESSAGE_TEMPLATES = GWT.create(ErrorMessageTemplates.class);

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
                fireAlertEvent(AlertEvent::fireError);
            } else if (currentErrors.containsAny(Severity.WARNING)) {
                fireAlertEvent(AlertEvent::fireWarn);
            } else if (currentErrors.containsAny(Severity.INFO)) {
                fireAlertEvent(AlertEvent::fireInfo);
            }
        }
    }

    private void fireAlertEvent(final FireAlertEventFunction fireAlertEventFunction) {
        final List<ErrorMessage> errorMessages = currentErrors.getErrorMessagesOrderedBySeverity();
        final String msg = getAlertMessage(errorMessages.size());
        final List<String> messages = errorMessages.stream()
                .map(this::toDisplayMessage)
                .collect(Collectors.toList());

        fireAlertEventFunction.apply(this, msg, String.join("\n", messages), null);
    }

    private String toDisplayMessage(final ErrorMessage errorMessage) {
        if (errorMessage.getNode() == null) {
            return ERROR_MESSAGE_TEMPLATES.errorMessage(errorMessage.getSeverity().getDisplayValue(),
                    errorMessage.getMessage());
        }
        return ERROR_MESSAGE_TEMPLATES.errorMessageWithNode(errorMessage.getSeverity().getDisplayValue(),
                errorMessage.getMessage(), errorMessage.getNode());
    }

    private String getAlertMessage(final int numberOfMessages) {
        return numberOfMessages == 1 ? ERROR_MESSAGE_TEMPLATES.errorMessageCreatedSingular() :
                ERROR_MESSAGE_TEMPLATES.errorMessagesCreatedPlural();
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
