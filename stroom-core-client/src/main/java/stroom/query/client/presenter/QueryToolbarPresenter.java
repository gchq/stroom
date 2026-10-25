/*
 * Copyright 2023 Crown Copyright
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
import stroom.query.api.TimeRanges;
import stroom.query.client.presenter.QueryToolbarPresenter.QueryToolbarView;
import stroom.query.client.view.QueryButtons;
import stroom.query.client.view.TimeRangeSelector;
import stroom.query.client.view.TimeRangeSelectorController;
import stroom.util.shared.ErrorMessage;
import stroom.util.shared.ErrorMessages;
import stroom.util.shared.Severity;
import stroom.widget.presenter.client.HasUiHandlers;
import stroom.widget.presenter.client.MyPresenterWidget;
import stroom.widget.presenter.client.View;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.List;
import java.util.stream.Collectors;

public class QueryToolbarPresenter
        extends MyPresenterWidget<QueryToolbarView>
        implements
        QueryToolbarUiHandlers,
        SearchStateListener,
        SearchErrorListener {

    private ErrorMessages currentErrors;
    private TimeRange currentTimeRange = TimeRanges.ALL_TIME;

    private static final ErrorMessageTemplates ERROR_MESSAGE_TEMPLATES = GWT.create(ErrorMessageTemplates.class);

    private Runnable onStart = () -> {
    };
    private Runnable onTimeRangeChange = () -> {
    };

    @Inject
    public QueryToolbarPresenter(final EventBus eventBus,
                                 final QueryToolbarView view,
                                 final TimeRangeSelectorController timeRangeSelectorController) {
        super(eventBus, view);
        own(timeRangeSelectorController).attach(view.getTimeRangeSelector());
        getView().setErrorsVisible(false);
        view.setUiHandlers(this);
        view.getQueryButtons().setOnStart(this::start);
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
        return numberOfMessages == 1
                ? ERROR_MESSAGE_TEMPLATES.errorMessageCreatedSingular()
                :
                        ERROR_MESSAGE_TEMPLATES.errorMessagesCreatedPlural();
    }

    @Override
    public void onTimeRange(final TimeRange timeRange) {
        if (!currentTimeRange.equals(timeRange)) {
            currentTimeRange = timeRange;
            onTimeRangeChange.run();
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

    private void start() {
        onStart.run();
    }

    @Override
    public void onSearching(final boolean searching) {
        getView().getQueryButtons().setSearching(searching);
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

    /// @param onStart What to run when the user starts (or stops) the query.
    public void setOnStart(final Runnable onStart) {
        this.onStart = onStart;
    }

    /// @param onTimeRangeChange What to run when the user changes the time range ([#getTimeRange()]).
    public void setOnTimeRangeChange(final Runnable onTimeRangeChange) {
        this.onTimeRangeChange = onTimeRangeChange;
    }


    // --------------------------------------------------------------------------------

    public interface QueryToolbarView extends View, HasUiHandlers<QueryToolbarUiHandlers> {

        void setErrorsVisible(boolean show);

        void setErrorSeverity(Severity severity);

        QueryButtons getQueryButtons();

        /// @return The time range selector, for a controller to open its dialog.
        TimeRangeSelector getTimeRangeSelector();

        TimeRange getTimeRange();

        void setTimeRange(TimeRange timeRange);

        void setParamValues(ParamValues paramValues);
    }
}
