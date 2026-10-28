/*
 * Copyright 2016 Crown Copyright
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

import stroom.svg.shared.SvgImage;
import stroom.widget.form.client.AccessibleName;
import stroom.widget.form.client.FieldValidity;
import stroom.widget.form.client.FormGroup;
import stroom.widget.form.client.TextBox;
import stroom.widget.text.client.Label;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.DivElement;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.Focus;
import com.google.gwt.user.client.ui.HasValue;
import com.google.gwt.user.client.ui.Widget;

import java.util.function.Consumer;

/// A field for a whole number, with arrows inside the end of its border that step the value down and
/// up while pressed (held, they keep stepping, faster and faster). It is a WAI-ARIA spin button: from
/// the keyboard, Up and Down in the field step it, Page Up and Page Down take ten steps, and Home and
/// End go to its bounds, so the arrows are no tab stop. What is typed is committed on Enter or when
/// the field loses the focus (or before a step). A typed value that isn't a whole number within the
/// bounds is rejected: the value before it is put back, the field is marked invalid, and why is shown
/// (in the form group's feedback, if the spinner is in a [FormGroup]) and announced, until a value is
/// accepted.
///
/// Its markup is `ValueSpinner.ui.xml`, styled by value-spinner.css: `value-spinner`,
/// `value-spinner__field`, `value-spinner__input`, `value-spinner__steps`, `value-spinner__step`,
/// `value-spinner__icon`, `value-spinner__feedback` and `value-spinner__status`.
/// (`ValueSpinnerCell`, the data grid's spinner, renders the same markup with `value-spinner--cell`.)
public class ValueSpinner
        extends Composite
        implements Focus, HasValue<Long> {

    private static final Binder BINDER = GWT.create(Binder.class);
    private static final String BLOCK = "value-spinner";
    // The steps Page Up and Page Down take
    private static final int PAGE_STEPS = 10;

    private final Spinner spinner;
    @UiField
    TextBox valueBox;
    @UiField
    DivElement decrease;
    @UiField
    DivElement increase;
    // Why a typed value was rejected, shown under the field when it isn't in a form group (one
    // in a form group shows it in the group's feedback, as the group's other controls do)
    @UiField
    Label feedback;
    // Says why a value was rejected, as it happens: the focus has usually left the field (the
    // field's description says it again when it is focused)
    @UiField
    DivElement status;
    private Consumer<Long> onChange;
    // The form group showing why a value was rejected, or null if none is
    private FormGroup rejectingFormGroup;
    // Whether the field's own feedback is showing why a value was rejected
    private boolean showingFeedback;
    // Whether a value has been shown in the field (until then it is empty, and that isn't an error)
    private boolean hasShownValue;
    private boolean enabled = true;
    private boolean readOnly;

    public ValueSpinner() {
        initWidget(BINDER.createAndBindUi(this));
        spinner = new Spinner();
        spinner.addValueChangeHandler(event -> {
            showValue(event.getValue());
            // A value accepted, so any rejection no longer applies
            clearRejection();
            if (onChange != null) {
                onChange.accept(event.getValue());
            }
        });

        valueBox.addBlurHandler(event -> updateSpinner());
        valueBox.addKeyDownHandler(event -> {
            if (readOnly) {
                return;
            }
            final int keyCode = event.getNativeKeyCode();
            if (keyCode == KeyCodes.KEY_ENTER) {
                updateSpinner();
                event.preventDefault();
                event.stopPropagation();
            } else if (isStepKey(keyCode)) {
                // Step from what has been typed, not from the value before it
                updateSpinner();
                step(keyCode);
                event.preventDefault();
                event.stopPropagation();
            }
        });
        updateBounds();

        // The arrows, inside the end of the field's border, as a drop-down's arrow is
        stepArrow(decrease, SvgImage.ARROW_DOWN, false);
        stepArrow(increase, SvgImage.ARROW_UP, true);
    }

    /// @param onChange Told the new value when the user changes it (or `setValue(value, true)`
    ///                 does), or null for nothing.
    public void setOnChange(final Consumer<Long> onChange) {
        this.onChange = onChange;
    }

    /// Names the field for screen readers where no label names it. Bound to the `ariaLabel`
    /// attribute in ui.xml.
    ///
    /// @param ariaLabel The name, or null or blank for none.
    public void setAriaLabel(final String ariaLabel) {
        valueBox.setAriaLabel(ariaLabel);
    }

    // An arrow that steps the value while it is pressed (with the mouse, a finger or a pen): it steps
    // at once, then keeps stepping, faster and faster, while it is held. It isn't a button: the keys
    // in the field do what it does, so (as the WAI-ARIA spin button pattern allows) it is no tab stop
    // and is hidden from screen readers (ValueSpinner.ui.xml), its title naming it for the mouse
    private void stepArrow(final Element arrow, final SvgImage icon, final boolean increment) {
        arrow.setInnerHTML(icon.getSvg());
        // An SVG element's className isn't a string, so set the class attribute itself
        arrow.getFirstChildElement().setAttribute("class", BLOCK + "__icon");
        listenForPresses(arrow, increment);
    }

    // Pointer events, so that a press with a finger or a pen steps and repeats as the mouse's does
    private native void listenForPresses(Element arrow, boolean increment) /*-{
        var self = this;
        // A press neither takes the focus from the field nor starts selecting text
        arrow.addEventListener('mousedown', function (event) {
            event.preventDefault();
        });
        arrow.addEventListener('pointerdown', function (event) {
            if (event.button === 0) {
                self.@stroom.widget.valuespinner.client.ValueSpinner::onArrowPressed(ZZ)(
                    increment, event.pointerType === 'mouse');
            }
        });
        var release = function () {
            self.@stroom.widget.valuespinner.client.ValueSpinner::onArrowReleased()();
        };
        arrow.addEventListener('pointerup', release);
        arrow.addEventListener('pointercancel', release);
        arrow.addEventListener('pointerleave', release);
        // A long press doesn't open the browser's menu, which would end the press
        arrow.addEventListener('contextmenu', function (event) {
            event.preventDefault();
        });
    }-*/;

    private void onArrowPressed(final boolean increment, final boolean withMouse) {
        if (enabled && !readOnly) {
            // The mouse's press puts the focus in the field, so typing can carry on; a finger's
            // doesn't, so as not to bring up an on-screen keyboard
            if (withMouse) {
                valueBox.setFocus(true);
            }
            // Step from what has been typed, not from the value before it
            updateSpinner();
            spinner.startRepeating(increment);
        }
    }

    private void onArrowReleased() {
        spinner.stopRepeating();
    }

    // The keys of the WAI-ARIA spin button: Up and Down step, Page Up and Page Down take ten steps,
    // and Home and End go to the bounds
    private static boolean isStepKey(final int keyCode) {
        return keyCode == KeyCodes.KEY_UP
               || keyCode == KeyCodes.KEY_DOWN
               || keyCode == KeyCodes.KEY_PAGEUP
               || keyCode == KeyCodes.KEY_PAGEDOWN
               || keyCode == KeyCodes.KEY_HOME
               || keyCode == KeyCodes.KEY_END;
    }

    private void step(final int keyCode) {
        switch (keyCode) {
            case KeyCodes.KEY_UP:
                spinner.increase();
                break;
            case KeyCodes.KEY_DOWN:
                spinner.decrease();
                break;
            case KeyCodes.KEY_PAGEUP:
                spinner.stepBy(PAGE_STEPS);
                break;
            case KeyCodes.KEY_PAGEDOWN:
                spinner.stepBy(-PAGE_STEPS);
                break;
            case KeyCodes.KEY_HOME:
                spinner.moveTo(spinner.getMin());
                break;
            case KeyCodes.KEY_END:
                spinner.moveTo(spinner.getMax());
                break;
            default:
                break;
        }
    }

    // Shows the value in the field and tells screen readers it, as text too if it is shown other
    // than as the number (e.g. by a subclass's formatValue)
    private void showValue(final long value) {
        final String text = formatValue(value);
        valueBox.setText(text);
        hasShownValue = true;
        final Element input = valueBox.getElement();
        input.setAttribute("aria-valuenow", String.valueOf(value));
        if (text.equals(String.valueOf(value))) {
            input.removeAttribute("aria-valuetext");
        } else {
            input.setAttribute("aria-valuetext", text);
        }
    }

    private void updateBounds() {
        final Element input = valueBox.getElement();
        input.setAttribute("aria-valuemin", String.valueOf(spinner.getMin()));
        input.setAttribute("aria-valuemax", String.valueOf(spinner.getMax()));
    }

    @Override
    public void focus() {
        valueBox.setFocus(true);
    }

    // Commits what has been typed. A value that isn't a whole number within the bounds is rejected:
    // the value before it is put back, and the user is told why
    private void updateSpinner() {
        final String newText = valueBox.getText();
        final long value = spinner.getValue();
        if (newText.equals(formatValue(value)) || (!hasShownValue && newText.trim().isEmpty())) {
            // Nothing typed since the value was shown (or put back), or still nothing to show
            return;
        }
        try {
            final long newValue = parseValue(newText.trim());
            if (spinner.isConstrained() && (newValue > spinner.getMax() || newValue < spinner.getMin())) {
                reject(newText);
            } else if (newValue != value) {
                spinner.setValue(newValue, true);
            } else {
                // The same value, typed another way (e.g. "010")
                showValue(value);
                clearRejection();
            }
        } catch (final NumberFormatException e) {
            reject(newText);
        }
    }

    // Puts the value back and says why the text was rejected: in the form group's feedback if the
    // spinner is in one, otherwise under the field; either way announced, and marking the field
    // invalid and described by the message (WCAG 3.3.1)
    private void reject(final String rejectedText) {
        showValue(spinner.getValue());
        final String message = rejectionMessage(rejectedText);
        clearRejection();
        rejectingFormGroup = findFormGroup();
        if (rejectingFormGroup != null) {
            rejectingFormGroup.setFeedback(message);
        } else {
            FieldValidity.setInvalid(valueBox, feedback, message);
            showingFeedback = true;
        }
        status.setInnerText(message);
    }

    // Clears what reject said, if anything
    private void clearRejection() {
        if (rejectingFormGroup != null) {
            rejectingFormGroup.clearFeedback();
            rejectingFormGroup = null;
        }
        if (showingFeedback) {
            FieldValidity.setValid(valueBox, feedback);
            showingFeedback = false;
        }
        status.setInnerText("");
    }

    private String rejectionMessage(final String rejectedText) {
        final String expected = "Enter " + expectedValue() + ".";
        if (rejectedText.trim().isEmpty()) {
            return "A value is needed. " + expected;
        }
        return "\"" + rejectedText.trim() + "\" isn't allowed. " + expected;
    }

    // What the spinner accepts, e.g. "a whole number from 0 to 100" (a bound that is the largest or
    // smallest possible number isn't worth saying)
    private String expectedValue() {
        final long min = spinner.getMin();
        final long max = spinner.getMax();
        final boolean hasMin = min != Long.MIN_VALUE;
        final boolean hasMax = max != Long.MAX_VALUE;
        if (hasMin && hasMax) {
            return "a whole number from " + formatValue(min) + " to " + formatValue(max);
        } else if (hasMin) {
            return "a whole number of at least " + formatValue(min);
        } else if (hasMax) {
            return "a whole number of at most " + formatValue(max);
        }
        return "a whole number";
    }

    // The form group the spinner is in, if any
    private FormGroup findFormGroup() {
        Widget parent = getParent();
        while (parent != null && !(parent instanceof FormGroup)) {
            parent = parent.getParent();
        }
        return (FormGroup) parent;
    }

    /**
     * @return whether this widget is enabled.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets whether this widget is enabled.
     *
     * @param enabled true to enable the widget, false to disable it
     */
    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
        valueBox.setEnabled(enabled);
        updateArrows();
    }

    /// Makes the value read only: it can be read, selected and copied, and stays in the tab
    /// order, but can't be changed. It looks like the normal field with its value greyed.
    ///
    /// @param readOnly Whether the value is read only.
    public void setReadOnly(final boolean readOnly) {
        this.readOnly = readOnly;
        valueBox.setReadOnly(readOnly);
        updateArrows();
    }

    // The arrows look disabled from the field's disabled and read only states (value-spinner.css)
    private void updateArrows() {
        spinner.setEnabled(enabled && !readOnly);
    }

    /// @return Whether the value is read only.
    public boolean isReadOnly() {
        return readOnly;
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

    /// @param max The largest value it can have.
    public void setMax(final long max) {
        spinner.setMax(max);
        updateBounds();
    }

    /// The largest value it can have, from a template (`maxValue="59"`): UiBinder can't parse the
    /// `long` [#setMax(long)] takes.
    ///
    /// @param max The largest value it can have.
    public void setMaxValue(final int max) {
        setMax(max);
    }

    /// The smallest value it can have, from a template (`minValue="0"`): UiBinder can't parse the
    /// `long` [#setMin(long)] takes.
    ///
    /// @param min The smallest value it can have.
    public void setMinValue(final int min) {
        setMin(min);
    }

    /// Names its text box for assistive technology, where no label is for it (see [AccessibleName]).
    ///
    /// @param name The name, e.g. `Character count`.
    public void setAccessibleName(final String name) {
        AccessibleName.set(this, name);
    }

    public void setMaxStep(final int maxStep) {
        spinner.setMaxStep(maxStep);
    }

    /// @param min The smallest value it can have.
    public void setMin(final long min) {
        spinner.setMin(min);
        updateBounds();
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
            showValue(value);
            clearRejection();
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


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, ValueSpinner> {

    }
}
