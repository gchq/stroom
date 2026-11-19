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

package stroom.query.client.component;

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.FireAlertEventFunction;
import stroom.query.api.ParamValues;
import stroom.query.api.TimeRange;
import stroom.query.api.TimeRanges;
import stroom.query.client.ErrorMessageTemplates;
import stroom.query.client.SearchErrorListener;
import stroom.query.client.SearchStateListener;
import stroom.util.shared.ErrorMessage;
import stroom.util.shared.ErrorMessages;
import stroom.util.shared.Severity;
import stroom.widget.component.client.Controller;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.List;
import java.util.stream.Collectors;

/// A query's toolbar ([QueryToolbar]): keeps the time range chosen, follows the query's state and
/// errors, and shows the errors in an alert when asked.
public class QueryToolbarController
        extends Controller
        implements SearchStateListener, SearchErrorListener {

    private static final ErrorMessageTemplates ERROR_MESSAGE_TEMPLATES = GWT.create(ErrorMessageTemplates.class);

    private final QueryToolbar view;

    private ErrorMessages currentErrors;
    private TimeRange currentTimeRange = TimeRanges.ALL_TIME;

    private Runnable onStart = () -> {
    };
    private Runnable onTimeRangeChange = () -> {
    };

    /// Made by GIN, with its toolbar.
    ///
    /// @param eventBus                    The application's event bus.
    /// @param view                        The toolbar.
    /// @param timeRangeSelectorController Opens the time range's picker.
    @Inject
    public QueryToolbarController(final EventBus eventBus,
                                  final QueryToolbar view,
                                  final TimeRangeSelectorController timeRangeSelectorController) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        own(timeRangeSelectorController).attach(view.getTimeRangeSelector());
        view.setOnTimeRangeChange(this::onTimeRange);
        view.setOnShowErrors(this::showErrors);
        view.getQueryButtons().setOnStart(() -> onStart.run());
    }

    @Override
    public void onError(final List<ErrorMessage> errors) {
        currentErrors = new ErrorMessages(errors);
        view.setErrorsVisible(!currentErrors.isEmpty());
        if (!currentErrors.isEmpty()) {
            view.setErrorSeverity(currentErrors.getHighestSeverity());
        }
    }

    @Override
    public void onSearching(final boolean searching) {
        view.getQueryButtons().setSearching(searching);
    }

    /// @param enabled Whether the query can be started.
    public void setEnabled(final boolean enabled) {
        view.getQueryButtons().setEnabled(enabled);
    }

    /// @return The time range chosen.
    public TimeRange getTimeRange() {
        return currentTimeRange;
    }

    /// @param timeRange The time range to show, or null for all time.
    public void setTimeRange(final TimeRange timeRange) {
        if (timeRange == null) {
            currentTimeRange = TimeRanges.ALL_TIME;
        } else {
            currentTimeRange = timeRange;
        }
        view.setTimeRange(currentTimeRange);
    }

    /// @param paramValues The values of the parameters the time range may use.
    public void setParamValues(final ParamValues paramValues) {
        view.setParamValues(paramValues);
    }

    /// @param onStart What to run when the user starts (or stops) the query.
    public void setOnStart(final Runnable onStart) {
        this.onStart = onStart;
    }

    /// @param onTimeRangeChange What to run when the user changes the time range ([#getTimeRange()]).
    public void setOnTimeRangeChange(final Runnable onTimeRangeChange) {
        this.onTimeRangeChange = onTimeRangeChange;
    }

    private void onTimeRange(final TimeRange timeRange) {
        if (!currentTimeRange.equals(timeRange)) {
            currentTimeRange = timeRange;
            onTimeRangeChange.run();
        }
    }

    private void showErrors() {
        if (currentErrors != null && !currentErrors.isEmpty()) {
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
                : ERROR_MESSAGE_TEMPLATES.errorMessagesCreatedPlural();
    }
}
