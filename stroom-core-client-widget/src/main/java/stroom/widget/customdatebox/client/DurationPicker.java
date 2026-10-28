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

package stroom.widget.customdatebox.client;

import stroom.item.client.SelectionBox;
import stroom.util.shared.time.SimpleDuration;
import stroom.util.shared.time.TimeUnit;
import stroom.widget.valuespinner.client.ValueSpinner;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.Focus;
import com.google.gwt.user.client.ui.HasValue;
import com.google.gwt.user.client.ui.Widget;

import java.util.function.Consumer;

/// A duration: an amount (a value spinner, "Amount") and its unit (a selection box, "Unit"), side by
/// side. In a form group, which all of Stroom's are in, the two are a `group` named by its label.
///
/// Its markup is `DurationPicker.ui.xml`, styled by `duration-picker.css`: `duration-picker`,
/// `__amount`, `__unit`.
public class DurationPicker extends Composite implements HasValue<SimpleDuration>, Focus {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    ValueSpinner time;
    @UiField
    SelectionBox<TimeUnit> timeUnit;
    private Consumer<SimpleDuration> onChange;
    // Whether the duration may be none, chosen as the unit's first item (setNoneText)
    private boolean noneAllowed;
    private boolean enabled = true;

    public DurationPicker() {
        initWidget(BINDER.createAndBindUi(this));
        // Its own children's handlers, which go with them
        time.addValueChangeHandler(event -> fireChange());
        timeUnit.addValueChangeHandler(event -> {
            updateAmountEnabled();
            fireChange();
        });

        // Its bounds are longs, which a template can't give
        time.setMin(1);
        time.setMax(1000000);
        timeUnit.addItem(TimeUnit.SECONDS);
        timeUnit.addItem(TimeUnit.MINUTES);
        timeUnit.addItem(TimeUnit.HOURS);
        timeUnit.addItem(TimeUnit.DAYS);
        timeUnit.addItem(TimeUnit.WEEKS);
        timeUnit.addItem(TimeUnit.MONTHS);
        timeUnit.addItem(TimeUnit.YEARS);
    }

    /// @param onChange Told the duration when the user changes its amount or its unit (or
    ///                 `setValue(value, true)` sets it), or null for nothing.
    public void setOnChange(final Consumer<SimpleDuration> onChange) {
        this.onChange = onChange;
    }

    private void fireChange() {
        ValueChangeEvent.fire(this, getValue());
        if (onChange != null) {
            onChange.accept(getValue());
        }
    }

    /// Allows an amount of 0, for a duration where 0 means something (e.g. "wait indefinitely"); the
    /// least is otherwise 1. Bound to the `zeroAllowed` attribute in ui.xml.
    ///
    /// @param zeroAllowed Whether the amount may be 0.
    public void setZeroAllowed(final boolean zeroAllowed) {
        time.setMin(zeroAllowed
                ? 0
                : 1);
    }

    /// Allows no duration, for a setting where none means something (e.g. "never"): it is chosen as
    /// the unit's first item, named by `noneText`, which leaves the amount aside. Bound to the
    /// `noneText` attribute in ui.xml.
    ///
    /// @param noneText What no duration is called, e.g. `Never` or `Keep all`.
    public void setNoneText(final String noneText) {
        noneAllowed = true;
        timeUnit.setNonSelectString(noneText);
    }

    public void smallTimeMode() {
        time.setMin(0);
        time.setMax(1000000);
        timeUnit.clear();
        timeUnit.addItem(TimeUnit.NANOSECONDS);
        timeUnit.addItem(TimeUnit.MILLISECONDS);
        timeUnit.addItem(TimeUnit.SECONDS);
        timeUnit.addItem(TimeUnit.MINUTES);
        timeUnit.addItem(TimeUnit.HOURS);
    }

    @Override
    public void focus() {
        time.focus();
    }

    /// @return The duration, or null for none where that is allowed ([#setNoneText(String)]).
    @Override
    public SimpleDuration getValue() {
        if (noneAllowed && timeUnit.getValue() == null) {
            return null;
        }
        return SimpleDuration.builder().time(time.getValue()).timeUnit(timeUnit.getValue()).build();
    }

    /// @param value The duration, or null for none where that is allowed ([#setNoneText(String)]),
    ///              otherwise for 1 day.
    @Override
    public void setValue(final SimpleDuration value) {
        if (value != null) {
            time.setValue(value.getTime());
            timeUnit.setValue(value.getTimeUnit());
        } else if (noneAllowed) {
            timeUnit.setValue(null);
        } else {
            time.setValue(1);
            timeUnit.setValue(TimeUnit.DAYS);
        }
        updateAmountEnabled();
    }

    // No duration has no amount to change
    private void updateAmountEnabled() {
        time.setEnabled(enabled && !(noneAllowed && timeUnit.getValue() == null));
    }

    @Override
    public void setValue(final SimpleDuration value, final boolean fireEvents) {
        // As setValue(value), which this once didn't call: it threw on null, and never fired
        setValue(value);
        if (fireEvents) {
            fireChange();
        }
    }

    @Override
    public com.google.gwt.event.shared.HandlerRegistration addValueChangeHandler(
            final ValueChangeHandler<SimpleDuration> handler) {
        return addHandler(handler, ValueChangeEvent.getType());
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
        timeUnit.setEnabled(enabled);
        updateAmountEnabled();
    }

    /// Makes the duration read only: it can be read and stays in the tab order, but neither the
    /// amount nor the unit can be changed. It looks like the normal fields with their values greyed.
    ///
    /// @param readOnly Whether the duration is read only.
    public void setReadOnly(final boolean readOnly) {
        time.setReadOnly(readOnly);
        timeUnit.setReadOnly(readOnly);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, DurationPicker> {

    }
}
