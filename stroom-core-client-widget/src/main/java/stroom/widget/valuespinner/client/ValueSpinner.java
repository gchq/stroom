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

package stroom.widget.valuespinner.client;

import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Focus;
import com.google.gwt.user.client.ui.HasValue;
import com.google.gwt.user.client.ui.TextBox;

public class ValueSpinner
        extends Composite
        implements Focus, HasValue<Long> {

    private final Spinner spinner;
    private final TextBox valueBox = new TextBox();

    public ValueSpinner() {
        spinner = new Spinner();
        spinner.addValueChangeHandler(event -> valueBox.setText(formatValue(event.getValue())));

        valueBox.addStyleName("allow-focus");
        valueBox.addBlurHandler(event -> updateSpinner());
        valueBox.addKeyDownHandler(event -> {
            if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
                updateSpinner();
                event.preventDefault();
                event.stopPropagation();
            } else if (event.getNativeKeyCode() == KeyCodes.KEY_UP) {
                spinner.increase();
                event.preventDefault();
                event.stopPropagation();
            } else if (event.getNativeKeyCode() == KeyCodes.KEY_DOWN) {
                spinner.decrease();
                event.preventDefault();
                event.stopPropagation();
            }
        });

        final FlowPanel arrowsPanel = new FlowPanel();
        arrowsPanel.setStylePrimaryName("arrows");
        arrowsPanel.add(spinner.getIncrementArrow());
        arrowsPanel.add(spinner.getDecrementArrow());

        final FlowPanel layout = new FlowPanel();
        layout.setStylePrimaryName("valueSpinner");
        layout.add(valueBox);
        layout.add(arrowsPanel);

        initWidget(layout);
    }

    @Override
    public void focus() {
        valueBox.setFocus(true);
    }

    private void updateSpinner() {
        final String newText = valueBox.getText();
        final long value = spinner.getValue();
        try {
            final long newValue = parseValue(newText);
            if (newValue != value) {
                if (spinner.isConstrained() && (newValue > spinner.getMax() || newValue < spinner.getMin())) {
                    valueBox.setText(formatValue(value));
                } else {
                    spinner.setValue(newValue, true);
                }
            }
        } catch (final NumberFormatException e) {
            valueBox.setText(formatValue(value));
        }
    }



    /**
     * @return whether this widget is enabled.
     */
    public boolean isEnabled() {
        return spinner.isEnabled();
    }

    /**
     * Sets whether this widget is enabled.
     *
     * @param enabled true to enable the widget, false to disable it
     */
    public void setEnabled(final boolean enabled) {
        spinner.setEnabled(enabled);
        valueBox.setEnabled(enabled);
    }

    /**
     * @param value the value to format
     * @return the formatted value
     */
    protected String formatValue(final long value) {
        return String.valueOf(value);
    }

    /**
     * @param value the value to parse
     * @return the parsed value
     */
    protected long parseValue(final String value) {
        return Long.valueOf(value);
    }

    public void setMax(final long max) {
        spinner.setMax(max);
    }

    public void setMaxStep(final int maxStep) {
        spinner.setMaxStep(maxStep);
    }

    public void setMin(final long min) {
        spinner.setMin(min);
    }

    public long getMin() {
        return spinner.getMin();
    }

    public void setMinStep(final int minStep) {
        spinner.setMinStep(minStep);
    }

    public long getDelta() {
        return spinner.getDelta();
    }

    public void setDelta(final int delta) {
        spinner.setDelta(delta);
    }

    public void setWrapValues(final boolean wrapValues) {
        spinner.setWrapValues(wrapValues);
    }

    public int getIntValue() {
        return (int) spinner.getValue();
    }

    public void setValue(final Integer value) {
        if (value != null) {
            setValue((long) value);
        }
    }

    @Override
    public Long getValue() {
        return spinner.getValue();
    }

    @Override
    public void setValue(final Long value) {
        setValue(value, false);
    }

    @Override
    public void setValue(final Long value, final boolean fireEvents) {
        if (value != null) {
            spinner.setValue(value, fireEvents);
            valueBox.setText(formatValue(value));
        }
    }

    @Override
    public HandlerRegistration addValueChangeHandler(final ValueChangeHandler<Long> handler) {
        return spinner.addValueChangeHandler(handler);
    }

    @Override
    public void fireEvent(final GwtEvent<?> event) {
        spinner.fireEvent(event);
    }
}
