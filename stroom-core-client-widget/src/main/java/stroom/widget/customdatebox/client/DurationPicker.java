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

    public DurationPicker() {
        initWidget(BINDER.createAndBindUi(this));
        // Its own children's handlers, which go with them
        time.addValueChangeHandler(event -> fireChange());
        timeUnit.addValueChangeHandler(event -> fireChange());

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

    @Override
    public SimpleDuration getValue() {
        return SimpleDuration.builder().time(time.getValue()).timeUnit(timeUnit.getValue()).build();
    }

    @Override
    public void setValue(final SimpleDuration value) {
        if (value != null) {
            time.setValue(value.getTime());
            timeUnit.setValue(value.getTimeUnit());
        } else {
            time.setValue(1);
            timeUnit.setValue(TimeUnit.DAYS);
        }
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
        time.setEnabled(enabled);
        timeUnit.setEnabled(enabled);
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
