/*
 * Copyright 2021 Crown Copyright
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

/*
 * Copyright 2009 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package stroom.widget.tickbox.client;

import stroom.util.shared.NullSafe;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.EventTarget;
import com.google.gwt.dom.client.InputElement;
import com.google.gwt.dom.client.LabelElement;
import com.google.gwt.dom.client.SpanElement;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.safehtml.shared.annotations.IsSafeHtml;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiTemplate;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.ButtonBase;
import com.google.gwt.user.client.ui.Focus;
import com.google.gwt.user.client.ui.HasName;
import com.google.gwt.user.client.ui.HasValue;
import com.google.gwt.user.client.ui.Widget;

import java.util.function.Consumer;

/**
 * A tick box: a check box with Stroom's drawn box and its label beside it.
 * <p>
 * It is the BEM block {@code tick-box} ({@code tick-box.css}, markup {@code TickBox.ui.xml}): a
 * {@code label} element holding a native check box ({@code tick-box__input}, unseen, over the box, so
 * it has the focus, the clicks and the state), the box drawn for it ({@code tick-box__mark}) and its
 * text ({@code tick-box__label}). Clicking anywhere on it ticks it, as the label wraps the check box.
 * Its states are the check box's: checked, disabled and {@code aria-readonly}.
 */
public class TickBox extends ButtonBase implements HasName, HasValue<Boolean>, Focus {

    private static final String CHECK = "<svg aria-hidden=\"true\" focusable=\"false\" data-prefix=\"fas\" data-icon=\"check\" role=\"img\" xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 512 512\"><path fill=\"currentColor\" d=\"M173.898 439.404l-166.4-166.4c-9.997-9.997-9.997-26.206 0-36.204l36.203-36.204c9.997-9.998 26.207-9.998 36.204 0L192 312.69 432.095 72.596c9.997-9.997 26.207-9.997 36.204 0l36.203 36.204c9.997 9.997 9.997 26.206 0 36.204l-294.4 294.401c-9.998 9.997-26.207 9.997-36.204-.001z\"></path></svg>";

    /// The tick drawn in the box, white on blue (also drawn by the grid's `TickBoxCell`).
    public static final String TICK_SVG = CHECK;

    private static final Binder BINDER = GWT.create(Binder.class);

    final InputElement inputElem;
    final SpanElement checkmarkElem;
    private final SpanElement labelElem;
    private boolean valueChangeHandlerInitialized;
    private boolean readOnly;
    private Consumer<Boolean> onChange;

    public TickBox() {
        this(new Parts());
    }

    private TickBox(final Parts parts) {
        super(BINDER.createAndBindUi(parts));
        inputElem = parts.input;
        checkmarkElem = parts.mark;
        labelElem = parts.label;
        checkmarkElem.setInnerHTML(CHECK);

        // An id of its own, until setIdentity gives it one (e.g. for a form group's label)
        inputElem.setId(DOM.createUniqueId());

        // Accessibility: setting tab index to be 0 by default, ensuring element
        // appears in tab sequence. FocusWidget's setElement method already
        // calls setTabIndex, which is overridden below. However, at the time
        // that this call is made, inputElem has not been created. So, we have
        // to call setTabIndex again, once inputElem has been created.
        setTabIndex(0);

        // So that a read-only tick box can undo a click before the browser shows it
        sinkEvents(Event.ONCLICK);
    }

    /// Makes the tick read only: the tick box can be focused and read, but clicking it, its
    /// label or pressing Space doesn't change it. It looks like the normal tick box with a grey
    /// tick and label.
    ///
    /// @param readOnly Whether the tick is read only.
    public void setReadOnly(final boolean readOnly) {
        this.readOnly = readOnly;
        // The look follows aria-readonly (tick-box.css)
        if (readOnly) {
            inputElem.setAttribute("aria-readonly", "true");
        } else {
            inputElem.removeAttribute("aria-readonly");
        }
    }

    /// @return Whether the tick is read only.
    public boolean isReadOnly() {
        return readOnly;
    }

    /// @param onChange Told the new value when the user ticks or unticks it (or `setValue(value, true)`
    ///                 changes it), or null for nothing.
    public void setOnChange(final Consumer<Boolean> onChange) {
        this.onChange = onChange;
        ensureChangeHandling();
    }

    @Override
    public void onBrowserEvent(final Event event) {
        // A click (or Space) on a read-only tick box mustn't change it: cancelling the click undoes
        // the tick, and its handlers aren't told
        if (readOnly && DOM.eventGetType(event) == Event.ONCLICK) {
            event.preventDefault();
            return;
        }
        super.onBrowserEvent(event);
    }

    @Override
    public void focus() {
        inputElem.focus();
    }

    @Override
    public HandlerRegistration addValueChangeHandler(
            final ValueChangeHandler<Boolean> handler) {
        ensureChangeHandling();
        return addHandler(handler, ValueChangeEvent.getType());
    }

    // The first change handler or callback adds the click handler that tells them
    private void ensureChangeHandling() {
        if (!valueChangeHandlerInitialized) {
            ensureDomEventHandlers();
            valueChangeHandlerInitialized = true;
        }
    }

    private void fireChange(final Boolean value) {
        ValueChangeEvent.fire(this, value);
        if (onChange != null) {
            onChange.accept(value);
        }
    }

    /// @param label The text beside the tick box, which names it, or null or blank for none.
    public void setLabel(final String label) {
        labelElem.setInnerText(NullSafe.string(label));
    }

    /// @return The label beside the tick box, or null or blank if it has none.
    public String getLabel() {
        return labelElem.getInnerText();
    }

    /// Names the tick box for screen readers when it has no label of its own, e.g. a tick box that
    /// chooses whether the field beside it is changed. Bound to the `ariaLabel` attribute in
    /// ui.xml.
    ///
    /// @param ariaLabel The name, or null or blank for none.
    public void setAriaLabel(final String ariaLabel) {
        if (NullSafe.isBlankString(ariaLabel)) {
            inputElem.removeAttribute("aria-label");
        } else {
            inputElem.setAttribute("aria-label", ariaLabel);
        }
    }

    /// @param identity The check box's id, e.g. for a form group's label to point at.
    public void setIdentity(final String identity) {
        this.inputElem.setId(identity);
    }

    // getText, setText, getHTML and setHTML (GWT's text methods) use the label, as setLabel does,
    // rather than replacing the drawn box and its tick

    @Override
    public String getHTML() {
        return labelElem.getInnerHTML();
    }

    @Override
    public String getName() {
        return inputElem.getName();
    }

    @Override
    public int getTabIndex() {
        return inputElem.getTabIndex();
    }

    @Override
    public String getText() {
        return labelElem.getInnerText();
    }

    /**
     * Determines whether this check box is currently checked.
     *
     * @return <code>true</code> if the check box is checked, false otherwise.
     * Will not return null
     */
    @Override
    public Boolean getValue() {
        if (isAttached()) {
            return inputElem.isChecked();
        } else {
            return inputElem.isDefaultChecked();
        }
    }

    @Override
    public boolean isEnabled() {
        return !inputElem.isDisabled();
    }

    @Override
    public void setEnabled(final boolean enabled) {
        // The look follows the check box's disabled state (tick-box.css)
        inputElem.setDisabled(!enabled);
    }

    @Override
    public void setFocus(final boolean focused) {
        if (focused) {
            inputElem.focus();
        } else {
            inputElem.blur();
        }
    }

    @Override
    public void setHTML(@IsSafeHtml final String html) {
        labelElem.setInnerHTML(html);
    }

    @Override
    public void setName(final String name) {
        inputElem.setName(name);
    }

    @Override
    public void setTabIndex(final int index) {
        // Need to guard against call to setTabIndex before inputElem is
        // initialized. This happens because FocusWidget's (a superclass of
        // CheckBox) setElement method calls setTabIndex before inputElem is
        // initialized. See CheckBox's protected constructor for more information.
        if (inputElem != null) {
            inputElem.setTabIndex(index);
        }
    }

    @Override
    public void setText(final String text) {
        labelElem.setInnerText(text);
    }

    /**
     * Checks or unchecks the check box.
     *
     * @param value true to check, false to uncheck; null value implies false
     */
    @Override
    public void setValue(final Boolean value) {
        setValue(value, false);
    }

    /**
     * Checks or unchecks the check box, firing {@link ValueChangeEvent} if
     * appropriate.
     *
     * @param value      true to check, false to uncheck; null value implies false
     * @param fireEvents If true, and value has changed, fire a
     *                   {@link ValueChangeEvent}
     */
    @Override
    public void setValue(Boolean value, final boolean fireEvents) {
        if (value == null) {
            value = Boolean.FALSE;
        }

        final Boolean oldValue = getValue();
        inputElem.setChecked(value);
        inputElem.setDefaultChecked(value);
        if (value.equals(oldValue)) {
            return;
        }
        if (fireEvents) {
            fireChange(value);
        }
    }

    // Unlike other widgets the CheckBox sinks on its inputElement, not
    // its wrapper
    @Override
    public void sinkEvents(final int eventBitsToAdd) {
        if (isOrWasAttached()) {
            Event.sinkEvents(inputElem, eventBitsToAdd
                    | Event.getEventsSunk(inputElem));
        } else {
            super.sinkEvents(eventBitsToAdd);
        }
    }

    protected void ensureDomEventHandlers() {
        addClickHandler(new ClickHandler() {
            @Override
            public void onClick(final ClickEvent event) {
                // Only a click on the input toggles it. A click on the label is followed by the
                // browser's click on the input, and a click elsewhere in the widget changes nothing.
                final EventTarget target = event.getNativeEvent().getEventTarget();
                if (Element.is(target) && inputElem.isOrHasChild(Element.as(target))) {
                    // Checkboxes always toggle their value, no need to compare
                    // with old value. Radio buttons are not so lucky, see
                    // overrides in RadioButton
                    fireChange(getValue());
                }
            }
        });
    }

    /**
     * This method is called when a widget is attached to the browser's document.
     * onAttach needs special handling for the CheckBox case. Must still call
     * {@link Widget#onAttach()} to preserve the <code>onAttach</code> contract.
     */
    @Override
    protected void onLoad() {
        DOM.setEventListener(inputElem, this);
    }

    /**
     * This method is called when a widget is detached from the browser's
     * document. Overridden because of IE bug that throws away checked state and
     * in order to clear the event listener off of the <code>inputElem</code>.
     */
    @Override
    protected void onUnload() {
        // Clear out the inputElem's event listener (breaking the circular
        // reference between it and the widget).
        DOM.setEventListener(inputElem, null);
        setValue(getValue());
    }


    // --------------------------------------------------------------------------------


    // The parts of the template it changes; the label element itself is the template's root
    static final class Parts {

        @UiField
        InputElement input;
        @UiField
        SpanElement mark;
        @UiField
        SpanElement label;
    }


    // --------------------------------------------------------------------------------


    @UiTemplate("TickBox.ui.xml")
    interface Binder extends UiBinder<LabelElement, Parts> {

    }
}
