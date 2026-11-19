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

import stroom.query.api.ParamUtil;
import stroom.query.api.ParamValues;
import stroom.query.api.TimeRange;
import stroom.query.api.TimeRanges;
import stroom.util.shared.NullSafe;
import stroom.widget.button.client.LinkButton;
import stroom.widget.component.client.Component;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Focus;
import com.google.gwt.user.client.ui.HasValue;

/// The time range a query or dashboard applies to all its queries, shown as a button drawn as a link
/// (its text the range, e.g. "Today"), which asks to open the Time Range dialog to change it
/// ([#setOnOpenPicker(Runnable)]); a [TimeRangeSelectorController] attached to it opens it. The
/// button is named "Time range: " and its text, and says it opens a dialog.
///
/// A disabled or read-only selector stays focusable but doesn't open the dialog; both are
/// `aria-disabled`, as a button can't be read only. Styled by `time-range-selector.css`; its markup
/// is `TimeRangeSelector.ui.xml`.
public class TimeRangeSelector extends Component implements HasValue<TimeRange>, Focus {

    private static final Binder BINDER = GWT.create(Binder.class);
    private static final String READ_ONLY = "time-range-selector--read-only";
    private static final String ARIA_DISABLED = "aria-disabled";

    @UiField
    LinkButton button;

    private TimeRange value = TimeRanges.ALL_TIME;
    private ParamValues paramValues;
    private boolean enabled = true;
    private Runnable onOpenPicker;
    private Runnable onChange;

    /// Makes a selector of all time.
    public TimeRangeSelector() {
        initWidget(BINDER.createAndBindUi(this));
        button.setOnClick(() -> {
            if (enabled && !isReadOnly() && onOpenPicker != null) {
                onOpenPicker.run();
            }
        });
        showValue();
    }

    /// @param onOpenPicker Run when the user asks to change the time range in the dialog, unless
    ///                     the selector is disabled or read only.
    public void setOnOpenPicker(final Runnable onOpenPicker) {
        this.onOpenPicker = onOpenPicker;
    }

    /// @param onChange Run when the user changes the time range with the dialog.
    public void setOnChange(final Runnable onChange) {
        this.onChange = onChange;
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
    @Override
    protected void onReadOnlyChange(final boolean readOnly) {
        updateState();
    }

    private void updateState() {
        if (!enabled || isReadOnly()) {
            button.getElement().setAttribute(ARIA_DISABLED, "true");
        } else {
            button.getElement().removeAttribute(ARIA_DISABLED);
        }
        // Read only (but enabled) has a look of its own, as a read-only field's value does
        button.setStyleName(READ_ONLY, isReadOnly() && enabled);
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
                : TimeRanges.ALL_TIME;
        showValue();
        if (fireEvents) {
            ValueChangeEvent.fire(this, this.value);
            if (onChange != null) {
                onChange.run();
            }
        }
    }

    /// @param paramValues The parameters a range's text is shown with, or null.
    public void setParamValues(final ParamValues paramValues) {
        this.paramValues = paramValues;
        showValue();
    }

    /// Kept beside [#setOnChange(Runnable)] until its callers have moved.
    ///
    /// @param handler Told when the user changes the time range with the dialog.
    /// @return The handler's registration.
    @Override
    public HandlerRegistration addValueChangeHandler(final ValueChangeHandler<TimeRange> handler) {
        return addHandler(handler, ValueChangeEvent.getType());
    }

    // Named for what it is as well as its value, which alone ("Today") wouldn't say
    private void showValue() {
        final String text = getText();
        button.setText(text);
        button.setAriaLabel("Time range: " + text);
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
                return TimeRanges.ALL_TIME.toString();
            } else if (NullSafe.isNonBlankString(from)) {
                return "After " + from;
            } else {
                return "Before " + to;
            }
        }
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<LinkButton, TimeRangeSelector> {

    }
}
