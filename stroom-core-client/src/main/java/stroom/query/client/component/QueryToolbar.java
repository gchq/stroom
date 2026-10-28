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
 */

package stroom.query.client.component;

import stroom.query.api.ParamValues;
import stroom.query.api.TimeRange;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.Severity;
import stroom.widget.button.client.Toolbar;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.component.client.Component;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

import java.util.function.Consumer;

/// A query's toolbar: its time range, a button that shows the query's errors when it has any, and
/// the buttons that start and stop it.
public class QueryToolbar extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    // Hides the errors button but keeps its place, so the toolbar doesn't shift; visibility (not
    // opacity) also takes it out of the tab order and away from screen readers
    private static final String ERRORS_HIDDEN = "query-toolbar__errors--hidden";

    @UiField
    TimeRangeSelector timeRangeSelector;
    @UiField
    Toolbar errorsToolbar;
    @UiField
    ToolbarButton errors;
    @UiField
    QueryButtons queryButtons;

    private Consumer<TimeRange> onTimeRangeChange;

    /// Makes a toolbar with no errors shown.
    public QueryToolbar() {
        initWidget(BINDER.createAndBindUi(this));
        timeRangeSelector.addValueChangeHandler(e -> {
            if (onTimeRangeChange != null) {
                onTimeRangeChange.accept(e.getValue());
            }
        });
    }

    /// @return The time range selector, for its controller to attach to.
    public TimeRangeSelector getTimeRangeSelector() {
        return timeRangeSelector;
    }

    /// @return The buttons that start and stop the query.
    public QueryButtons getQueryButtons() {
        return queryButtons;
    }

    /// @param timeRange The time range to show.
    public void setTimeRange(final TimeRange timeRange) {
        timeRangeSelector.setValue(timeRange);
    }

    /// @param paramValues The values of the parameters the time range may use.
    public void setParamValues(final ParamValues paramValues) {
        timeRangeSelector.setParamValues(paramValues);
    }

    /// Shows or hides the errors button, keeping its place either way.
    ///
    /// @param show True to show it.
    public void setErrorsVisible(final boolean show) {
        errorsToolbar.setStyleName(ERRORS_HIDDEN, !show);
    }

    /// Shows the errors button's icon and title for the most severe of the errors.
    ///
    /// @param severity The most severe error's severity.
    public void setErrorSeverity(final Severity severity) {
        if (Severity.FATAL_ERROR.equals(severity) || Severity.ERROR.equals(severity)) {
            errors.setSvg(SvgImage.ERROR);
            errors.setTitle("Show Errors");
        } else if (Severity.WARNING.equals(severity)) {
            errors.setSvg(SvgImage.ALERT);
            errors.setTitle("Show Warnings");
        } else if (Severity.INFO.equals(severity)) {
            errors.setSvg(SvgImage.INFO);
            errors.setTitle("Show Messages");
        }
    }

    /// @param onShowErrors What to run when the user presses the errors button.
    public void setOnShowErrors(final Runnable onShowErrors) {
        errors.setOnClick(onShowErrors);
    }

    /// @param onTimeRangeChange What to run, with the time range chosen, when the user changes it.
    public void setOnTimeRangeChange(final Consumer<TimeRange> onTimeRangeChange) {
        this.onTimeRangeChange = onTimeRangeChange;
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, QueryToolbar> {

    }
}
