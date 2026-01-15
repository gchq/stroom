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

package stroom.widget.tickbox.client.view;

import stroom.widget.form.client.FormLabel;

import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.InputElement;
import com.google.gwt.dom.client.SpanElement;
import com.google.gwt.dom.client.Style.WhiteSpace;
import com.google.gwt.editor.client.IsEditor;
import com.google.gwt.editor.client.LeafValueEditor;
import com.google.gwt.editor.client.adapters.TakesValueEditor;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.i18n.client.HasDirection.Direction;
import com.google.gwt.i18n.shared.DirectionEstimator;
import com.google.gwt.i18n.shared.HasDirectionEstimator;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.annotations.IsSafeHtml;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.ButtonBase;
import com.google.gwt.user.client.ui.DirectionalTextHelper;
import com.google.gwt.user.client.ui.Focus;
import com.google.gwt.user.client.ui.FormPanel;
import com.google.gwt.user.client.ui.HasDirectionalSafeHtml;
import com.google.gwt.user.client.ui.HasName;
import com.google.gwt.user.client.ui.HasValue;
import com.google.gwt.user.client.ui.HasWordWrap;
import com.google.gwt.user.client.ui.Widget;

/**
 * A standard check box widget.
 * <p>
 * This class also serves as a base class for
 * {@link com.google.gwt.user.client.ui.RadioButton}.
 *
 * <p>
 * <img class='gallery' src='doc-files/CheckBox.png'/>
 * </p>
 *
 * <p>
 * <h3>Built-in Bidi Text Support</h3>
 * This widget is capable of automatically adjusting its direction according to
 * its content. This feature is controlled by {@link #setDirectionEstimator} or
 * passing a DirectionEstimator parameter to the constructor, and is off by
 * default.
 * </p>
 *
 * <h3>CSS Style Rules</h3>
 * <dl>
 * <dt>.gwt-CheckBox</dt>
 * <dd>the outer element</dd>
 * <dt>.gwt-CheckBox-disabled</dt>
 * <dd>applied when Checkbox is disabled</dd>
 * </dl>
 *
 * <p>
 * <h3>Example</h3>
 * {@example com.google.gwt.examples.CheckBoxExample}
 * </p>
 */
public class CustomCheckBox extends ButtonBase implements HasName, HasValue<Boolean>, Focus,
        HasWordWrap, HasDirectionalSafeHtml, HasDirectionEstimator,
        IsEditor<LeafValueEditor<Boolean>> {

    private static final String CHECK = "<svg aria-hidden=\"true\" focusable=\"false\" data-prefix=\"fas\" data-icon=\"check\" class=\"svg-inline--fa fa-check fa-w-16 fa-lg \" role=\"img\" xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 512 512\"><path fill=\"currentColor\" d=\"M173.898 439.404l-166.4-166.4c-9.997-9.997-9.997-26.206 0-36.204l36.203-36.204c9.997-9.998 26.207-9.998 36.204 0L192 312.69 432.095 72.596c9.997-9.997 26.207-9.997 36.204 0l36.203 36.204c9.997 9.997 9.997 26.206 0 36.204l-294.4 294.401c-9.998 9.997-26.207 9.997-36.204-.001z\"></path></svg>";

    public static final DirectionEstimator DEFAULT_DIRECTION_ESTIMATOR =
            DirectionalTextHelper.DEFAULT_DIRECTION_ESTIMATOR;

    final DirectionalTextHelper directionalTextHelper;
    final InputElement inputElem;
    final SpanElement checkmarkElem;
    final FormLabel label = new FormLabel();
    private LeafValueEditor<Boolean> editor;
    private boolean valueChangeHandlerInitialized;

    public CustomCheckBox() {
        super(DOM.createDiv());
        setStyleName("SimpleTickBox");

        final Element checkboxContainer = DOM.createDiv();
        checkboxContainer.setClassName("checkbox-container");
        inputElem = InputElement.as(DOM.createInputCheck());
        checkmarkElem = Document.get().createSpanElement();
        checkmarkElem.setInnerHTML(CHECK);

        inputElem.setClassName("checkbox");
        checkmarkElem.setClassName("checkmark");

        checkboxContainer.appendChild(inputElem);
        checkboxContainer.appendChild(checkmarkElem);
        getElement().appendChild(checkboxContainer);

        getElement().appendChild(label.getElement());

//        String uid = DOM.createUniqueId();
//        inputElem.setPropertyString("id", uid);
//        labelElem.setHtmlFor(uid);

        directionalTextHelper = new DirectionalTextHelper(checkmarkElem, true);

        // Accessibility: setting tab index to be 0 by default, ensuring element
        // appears in tab sequence. FocusWidget's setElement method already
        // calls setTabIndex, which is overridden below. However, at the time
        // that this call is made, inputElem has not been created. So, we have
        // to call setTabIndex again, once inputElem has been created.
        setTabIndex(0);
    }

    @Override
    public void focus() {
        inputElem.focus();
    }

    @Override
    public HandlerRegistration addValueChangeHandler(
            final ValueChangeHandler<Boolean> handler) {
        // Is this the first value change handler? If so, time to add handlers
        if (!valueChangeHandlerInitialized) {
            ensureDomEventHandlers();
            valueChangeHandlerInitialized = true;
        }
        return addHandler(handler, ValueChangeEvent.getType());
    }

    @Override
    public LeafValueEditor<Boolean> asEditor() {
        if (editor == null) {
            editor = TakesValueEditor.of(this);
        }
        return editor;
    }

    @Override
    public DirectionEstimator getDirectionEstimator() {
        return directionalTextHelper.getDirectionEstimator();
    }

    /**
     * Returns the value property of the input element that backs this widget.
     * This is the value that will be associated with the CheckBox name and
     * submitted to the server if a {@link FormPanel} that holds it is submitted
     * and the box is checked.
     * <p>
     * Don't confuse this with {@link #getValue}, which returns true or false if
     * the widget is checked.
     */
    public String getFormValue() {
        return inputElem.getValue();
    }

    public void setLabel(final String label) {
        this.label.setLabel(label);
    }

    public void setIdentity(final String identity) {
        this.label.setIdentity(identity);
        this.inputElem.setId(identity);
    }

    @Override
    public String getHTML() {
        return directionalTextHelper.getHtml();
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
        return directionalTextHelper.getText();
    }

    @Override
    public Direction getTextDirection() {
        return directionalTextHelper.getTextDirection();
    }

    /**
     * Determines whether this check box is currently checked.
     * <p>
     * Note that this <em>does not</em> return the value property of the checkbox
     * input element wrapped by this widget. For access to that property, see
     * {@link #getFormValue()}
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
    public boolean getWordWrap() {
        return !WhiteSpace.NOWRAP.getCssName().equals(getElement().getStyle().getWhiteSpace());
    }

    /**
     * Determines whether this check box is currently checked.
     *
     * @return <code>true</code> if the check box is checked
     * @deprecated Use {@link #getValue} instead
     */
    @Deprecated
    public boolean isChecked() {
        // Funny comparison b/c getValue could in theory return null
        return getValue() == true;
    }

    @Override
    public boolean isEnabled() {
        return !inputElem.isDisabled();
    }

    @Override
    public void setAccessKey(final char key) {
        inputElem.setAccessKey("" + key);
    }

    /**
     * Checks or unchecks this check box. Does not fire {@link ValueChangeEvent}.
     * (If you want the event to fire, use {@link #setValue(Boolean, boolean)})
     *
     * @param checked <code>true</code> to check the check box.
     * @deprecated Use {@link #setValue(Boolean)} instead
     */
    @Deprecated
    public void setChecked(final boolean checked) {
        setValue(checked);
    }

    /**
     * {@inheritDoc}
     * <p>
     * See note at {@link #setDirectionEstimator(DirectionEstimator)}.
     */
    @Override
    public void setDirectionEstimator(final boolean enabled) {
        directionalTextHelper.setDirectionEstimator(enabled);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Note: DirectionEstimator should be set before the label has any content;
     * it's highly recommended to set it using a constructor. Reason: if the
     * label already has non-empty content, this will update its direction
     * according to the new estimator's result. This may cause flicker, and thus
     * should be avoided.
     */
    @Override
    public void setDirectionEstimator(final DirectionEstimator directionEstimator) {
        directionalTextHelper.setDirectionEstimator(directionEstimator);
    }

    @Override
    public void setEnabled(final boolean enabled) {
        inputElem.setDisabled(!enabled);
        if (enabled) {
            removeStyleDependentName("disabled");
        } else {
            addStyleDependentName("disabled");
        }
    }

    @Override
    public void setFocus(final boolean focused) {
        if (focused) {
            inputElem.focus();
        } else {
            inputElem.blur();
        }
    }

    /**
     * Set the value property on the input element that backs this widget. This is
     * the value that will be associated with the CheckBox's name and submitted to
     * the server if a {@link FormPanel} that holds it is submitted and the box is
     * checked.
     * <p>
     * Don't confuse this with {@link #setValue}, which actually checks and
     * unchecks the box.
     *
     * @param value
     */
    public void setFormValue(final String value) {
        inputElem.setAttribute("value", value);
    }

    @Override
    public void setHTML(final SafeHtml html, final Direction dir) {
        directionalTextHelper.setHtml(html, dir);
    }

    @Override
    public void setHTML(@IsSafeHtml final String html) {
        directionalTextHelper.setHtml(html);
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
        directionalTextHelper.setText(text);
    }

    @Override
    public void setText(final String text, final Direction dir) {
        directionalTextHelper.setText(text, dir);
    }

    /**
     * Checks or unchecks the check box.
     * <p>
     * Note that this <em>does not</em> set the value property of the checkbox
     * input element wrapped by this widget. For access to that property, see
     * {@link #setFormValue(String)}
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
     * <p>
     * Note that this <em>does not</em> set the value property of the checkbox
     * input element wrapped by this widget. For access to that property, see
     * {@link #setFormValue(String)}
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
            ValueChangeEvent.fire(this, value);
        }
    }

    @Override
    public void setWordWrap(final boolean wrap) {
        getElement().getStyle().setWhiteSpace(wrap
                ? WhiteSpace.NORMAL
                : WhiteSpace.NOWRAP);
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
                // Checkboxes always toggle their value, no need to compare
                // with old value. Radio buttons are not so lucky, see
                // overrides in RadioButton
                ValueChangeEvent.fire(CustomCheckBox.this, getValue());
            }
        });
    }

//    /**
//     * <b>Affected Elements:</b>
//     * <ul>
//     * <li>-label = label next to checkbox.</li>
//     * </ul>
//     *
//     * @see UIObject#onEnsureDebugId(String)
//     */
//    @Override
//    protected void onEnsureDebugId(String baseID) {
//        super.onEnsureDebugId(baseID);
//        ensureDebugId(labelElem, baseID, "label");
//        ensureDebugId(inputElem, baseID, "input");
//        labelElem.setHtmlFor(inputElem.getId());
//    }

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
}
