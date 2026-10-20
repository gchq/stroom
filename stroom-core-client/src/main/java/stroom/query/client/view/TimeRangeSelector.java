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

package stroom.query.client.view;

import stroom.event.client.StaticEventBus;
import stroom.query.api.ParamUtil;
import stroom.query.api.ParamValues;
import stroom.query.api.TimeRange;
import stroom.util.shared.NullSafe;
import stroom.widget.button.client.LinkButton;

import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.Focus;
import com.google.gwt.user.client.ui.HasValue;

/// The time range a query or dashboard applies to all its queries, shown as a button drawn as a link
/// (its text the range, e.g. "Today"), which opens the Time Range dialog ([TimeRangeDialog]) to
/// change it. The button is named "Time range: " and its text, and says it opens a dialog.
///
/// A disabled or read-only selector stays focusable but doesn't open the dialog; both are
/// `aria-disabled`, as a button can't be read only. Styled by `time-range-selector.css`.
public class TimeRangeSelector extends Composite implements HasValue<TimeRange>, Focus {

    private static final String BLOCK = "time-range-selector";
    private static final String READ_ONLY = BLOCK + "--readonly";
    private static final String ARIA_DISABLED = "aria-disabled";

    private final LinkButton button;
    private TimeRangeDialog dialog;
    private TimeRange value = stroom.query.api.TimeRanges.ALL_TIME;
    private ParamValues paramValues;
    private boolean enabled = true;
    private boolean readOnly;

    /// Makes a selector of all time.
    public TimeRangeSelector() {
        button = new LinkButton(getText());
        button.addStyleName(BLOCK);
        button.setOpensDialog(true);
        button.setTitle("Time range to apply to all queries in this dashboard.\n"
                        + "The selected time range applies to the time field applicable to each "
                        + "query's data source.");
        button.addClickHandler(event -> showDialog());
        updateName();

        initWidget(button);
    }

    private void showDialog() {
        if (enabled && !readOnly) {
            if (dialog == null) {
                dialog = new TimeRangeDialog(StaticEventBus.getEventBus(), new TimeRangePopup());
            }
            dialog.show(value, button.getElement(), timeRange -> setValue(timeRange, true));
        }
    }

    /// Enables or disables choosing the time range. A disabled one is greyed, announced as
    /// disabled and doesn't open its dialog, but stays focusable.
    ///
    /// @param enabled Whether the time range can be chosen.
    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
        updateState();
    }

    /// Makes the time range read only: it can be read and stays in the tab order, but its dialog
    /// doesn't open. Its value is greyed, as a read-only field's is (a button can't be read only, so
    /// it is `aria-disabled`).
    ///
    /// @param readOnly Whether the time range is read only.
    public void setReadOnly(final boolean readOnly) {
        this.readOnly = readOnly;
        updateState();
    }

    private void updateState() {
        if (!enabled || readOnly) {
            button.getElement().setAttribute(ARIA_DISABLED, "true");
        } else {
            button.getElement().removeAttribute(ARIA_DISABLED);
        }
        // Read only (but enabled) has a look of its own, as a read-only field's value does
        button.setStyleName(READ_ONLY, readOnly && enabled);
    }

    @Override
    public void focus() {
        button.setFocus(true);
    }

    @Override
    public TimeRange getValue() {
        return value;
    }

    @Override
    public void setValue(final TimeRange value) {
        setValue(value, false);
    }

    @Override
    public void setValue(final TimeRange value, final boolean fireEvents) {
        this.value = value != null
                ? value
                : stroom.query.api.TimeRanges.ALL_TIME;
        button.setText(getText());
        updateName();
        if (fireEvents) {
            ValueChangeEvent.fire(this, this.value);
        }
    }

    // Named for what it is as well as its value, which alone ("Today") wouldn't say
    private void updateName() {
        button.setAriaLabel("Time range: " + getText());
    }

    private String getText() {
        if (paramValues == null) {
            return value.toString();
        } else if (value.getName() != null) {
            return value.getName();
        } else {
            final String from = ParamUtil.replaceParameters(value.getFrom(), paramValues);
            final String to = ParamUtil.replaceParameters(value.getTo(), paramValues);
            if (NullSafe.isBlankString(from) && NullSafe.isBlankString(to)) {
                return stroom.query.api.TimeRanges.ALL_TIME.toString();
            } else if (NullSafe.isNonBlankString(from)) {
                return "After " + from;
            } else {
                return "Before " + to;
            }
        }
    }

    @Override
    public HandlerRegistration addValueChangeHandler(final ValueChangeHandler<TimeRange> handler) {
        return addHandler(handler, ValueChangeEvent.getType());
    }

    /// @param paramValues The parameters a range's text is shown with, or null.
    public void setParamValues(final ParamValues paramValues) {
        this.paramValues = paramValues;
        button.setText(getText());
        updateName();
    }
}
