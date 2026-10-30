/*
 * Copyright 2026 Crown Copyright
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

package stroom.widget.form.client;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.DivElement;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.InputElement;
import com.google.gwt.dom.client.LabelElement;
import com.google.gwt.dom.client.SpanElement;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.Focus;
import com.google.gwt.user.client.ui.HasValue;
import com.google.gwt.user.client.ui.Widget;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/// A choice of one of a few options, all shown: a WAI-ARIA radio group (`role="radiogroup"`) of
/// native radio buttons, which share a name, so the browser makes the group one tab stop with the
/// arrow keys moving between its options (and choosing as they move). Each option is labelled by
/// its text. The group is named by its form group's label, or by [#setAccessibleName(String)].
///
/// The BEM block `radio-group` (`radio-group.css`, markup `RadioGroup.ui.xml`): its options
/// (`radio-group__option`, a label), each holding its radio button (`radio-group__input`) and text
/// (`radio-group__label`).
///
/// @param <T> The type of the options' values.
public class RadioGroup<T> extends Widget implements HasValue<T>, Focus {

    private static final Binder BINDER = GWT.create(Binder.class);
    private static final String BLOCK = "radio-group";
    private static final String ARIA_LABEL = "aria-label";

    // The name the radio buttons share, which makes them one group to the browser
    private final String name = DOM.createUniqueId();
    private final List<T> values = new ArrayList<>();
    private final List<InputElement> inputs = new ArrayList<>();
    private T value;
    private Consumer<T> onChange;

    /// Makes an empty group. Add its options with [#addItem(Object, String)].
    public RadioGroup() {
        setElement(BINDER.createAndBindUi(this));
        // A radio button's change bubbles to the group
        sinkEvents(Event.ONCHANGE);
    }

    /// @param onChange Told the option chosen when the user chooses one (or `setValue(value, true)`
    ///                 changes it), or null for nothing.
    public void setOnChange(final Consumer<T> onChange) {
        this.onChange = onChange;
    }

    /// Adds an option.
    ///
    /// @param value The option's value.
    /// @param label The option's text, which names its radio button.
    public void addItem(final T value, final String label) {
        addItem(value, label, null);
    }

    /// Adds an option, with a tooltip saying more about it.
    ///
    /// @param value The option's value.
    /// @param label The option's text, which names its radio button.
    /// @param title What the option does, shown as its tooltip, or null for none.
    public void addItem(final T value, final String label, final String title) {
        final Document document = Document.get();
        final LabelElement option = document.createLabelElement();
        option.setClassName(BLOCK + "__option");
        if (title != null) {
            option.setTitle(title);
        }

        final InputElement input = document.createRadioInputElement(name);
        input.setClassName(BLOCK + "__input");
        input.setChecked(Objects.equals(value, this.value));
        option.appendChild(input);

        final SpanElement text = document.createSpanElement();
        text.setClassName(BLOCK + "__label");
        text.setInnerText(label);
        option.appendChild(text);

        getElement().appendChild(option);
        values.add(value);
        inputs.add(input);
    }

    /// @return The values of the options, in the order they are shown.
    public List<T> getItems() {
        return new ArrayList<>(values);
    }

    /// Names the group for assistive technology, where no form group's label names it.
    ///
    /// @param accessibleName The group's name, e.g. `Graphic`, or null to remove it.
    public void setAccessibleName(final String accessibleName) {
        if (accessibleName == null) {
            getElement().removeAttribute(ARIA_LABEL);
        } else {
            getElement().setAttribute(ARIA_LABEL, accessibleName);
        }
    }

    /// Enables or disables every option.
    ///
    /// @param enabled Whether the options can be chosen.
    public void setEnabled(final boolean enabled) {
        for (final InputElement input : inputs) {
            input.setDisabled(!enabled);
        }
    }

    /// @return Whether the options can be chosen.
    public boolean isEnabled() {
        return inputs.isEmpty() || !inputs.get(0).isDisabled();
    }

    /// Focuses the chosen option, or the first one if none is chosen, as Tab into the group does.
    @Override
    public void focus() {
        final int index = Math.max(0, values.indexOf(value));
        if (index < inputs.size()) {
            inputs.get(index).focus();
        }
    }

    @Override
    public T getValue() {
        return value;
    }

    @Override
    public void setValue(final T value) {
        setValue(value, false);
    }

    @Override
    public void setValue(final T value, final boolean fireEvents) {
        final T oldValue = this.value;
        this.value = value;
        for (int i = 0; i < inputs.size(); i++) {
            inputs.get(i).setChecked(Objects.equals(values.get(i), value));
        }
        if (fireEvents && !Objects.equals(oldValue, value)) {
            ValueChangeEvent.fire(this, value);
            if (onChange != null) {
                onChange.accept(value);
            }
        }
    }

    @Override
    public HandlerRegistration addValueChangeHandler(final ValueChangeHandler<T> handler) {
        return addHandler(handler, ValueChangeEvent.getType());
    }

    @Override
    public void onBrowserEvent(final Event event) {
        super.onBrowserEvent(event);
        if (event.getTypeInt() == Event.ONCHANGE) {
            // Only the radio button that has just been chosen fires a change
            final Element target = Element.as(event.getEventTarget());
            final int index = inputs.indexOf(target);
            if (index >= 0) {
                setValue(values.get(index), true);
            }
        }
    }


    // --------------------------------------------------------------------------------


    // Generic in its owner, as the group is
    @SuppressWarnings("rawtypes")
    interface Binder extends UiBinder<DivElement, RadioGroup> {

    }
}
