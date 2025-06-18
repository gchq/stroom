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

package com.google.gwt.user.client.ui;

import com.google.gwt.dom.client.Element;
import com.google.gwt.editor.client.IsEditor;
import com.google.gwt.editor.ui.client.adapters.ValueBoxEditor;
import com.google.gwt.event.dom.client.InputEvent;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.i18n.client.AutoDirectionHandler;
import com.google.gwt.i18n.client.BidiPolicy;
import com.google.gwt.i18n.client.BidiUtils;
import com.google.gwt.i18n.shared.DirectionEstimator;
import com.google.gwt.i18n.shared.HasDirectionEstimator;
import com.google.gwt.text.shared.Parser;
import com.google.gwt.text.shared.Renderer;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Event;

import java.text.ParseException;

/**
 * Abstract base class for all text entry widgets.
 *
 * <h3>Use in UiBinder Templates</h3>
 *
 * @param <T> the value type
 */
public class ValueBoxBase<T> extends FocusWidget implements
        HasName, HasDirectionEstimator,
        HasValue<T>, HasText, AutoDirectionHandler.Target, IsEditor<ValueBoxEditor<T>> {

    /**
     * Alignment values for {@link ValueBoxBase#setAlignment}.
     */
    public enum TextAlignment {
        CENTER {
            @Override
            String getTextAlignString() {
                return "center";
            }
        },
        JUSTIFY {
            @Override
            String getTextAlignString() {
                return "justify";
            }
        },
        LEFT {
            @Override
            String getTextAlignString() {
                return "left";
            }
        },
        RIGHT {
            @Override
            String getTextAlignString() {
                return "right";
            }

        };

        abstract String getTextAlignString();
    }

    private final AutoDirectionHandler autoDirHandler;

    private final Parser<T> parser;
    private final Renderer<T> renderer;
    private ValueBoxEditor<T> editor;
    private Event currentEvent;

    private boolean valueChangeHandlerInitialized;

    /**
     * Creates a value box that wraps the given browser element handle. This is
     * only used by subclasses.
     *
     * @param elem the browser element to wrap
     */
    protected ValueBoxBase(final Element elem, final Renderer<T> renderer, final Parser<T> parser) {
        super(elem);
        autoDirHandler = AutoDirectionHandler.addTo(this,
                BidiPolicy.isBidiEnabled());
        this.renderer = renderer;
        this.parser = parser;
    }

    public HandlerRegistration addValueChangeHandler(final ValueChangeHandler<T> handler) {
        // Initialization code
        if (!valueChangeHandlerInitialized) {
            valueChangeHandlerInitialized = true;
            addDomHandler(e -> ValueChangeEvent.fire(ValueBoxBase.this, getValue()), InputEvent.getType());
        }
        return addHandler(handler, ValueChangeEvent.getType());
    }

    /**
     * Returns an Editor that is backed by the ValueBoxBase. The default
     * implementation returns {@link ValueBoxEditor#of(ValueBoxBase)}. Subclasses
     * may override this method to provide custom error-handling when using the
     * Editor framework.
     */
    public ValueBoxEditor<T> asEditor() {
        if (editor == null) {
            editor = ValueBoxEditor.of(this);
        }
        return editor;
    }

    /**
     * If a keyboard event is currently being handled on this text box, calling
     * this method will suppress it. This allows listeners to easily filter
     * keyboard input.
     */
    public void cancelKey() {
        if (currentEvent != null) {
            currentEvent.preventDefault();
        }
    }

    /**
     * Gets the current position of the cursor (this also serves as the beginning
     * of the text selection).
     *
     * @return the cursor's position
     */
    public int getCursorPos() {
        return getCursorPos(getElement());
    }

    public Direction getDirection() {
        return BidiUtils.getDirectionOnElement(getElement());
    }

    /**
     * Gets the direction estimation model of the auto-dir handler.
     */
    public DirectionEstimator getDirectionEstimator() {
        return autoDirHandler.getDirectionEstimator();
    }

    public String getName() {
        return getElement().getPropertyString("name");
    }

    /**
     * Gets the text currently selected within this text box.
     *
     * @return the selected text, or an empty string if none is selected
     */
    public String getSelectedText() {
        final int start = getCursorPos();
        if (start < 0) {
            return "";
        }
        final int length = getSelectionLength();
        return getText().substring(start, start + length);
    }

    /**
     * Gets the length of the current text selection.
     *
     * @return the text selection length
     */
    public int getSelectionLength() {
        return getSelectionLength(getElement());
    }

    public String getText() {
        return getElement().getPropertyString("value");
    }

    /**
     * Return the parsed value, or null if the field is empty or parsing fails.
     */
    public T getValue() {
        try {
            return getValueOrThrow();
        } catch (final ParseException e) {
            return null;
        }
    }

    /**
     * Return the parsed value, or null if the field is empty.
     *
     * @throws ParseException if the value cannot be parsed
     */
    public T getValueOrThrow() throws ParseException {
        final String text = getText();

        final T parseResult = parser.parse(text);

        if ("".equals(text)) {
            return null;
        }

        return parseResult;
    }

    /**
     * Determines whether or not the widget is read-only.
     *
     * @return <code>true</code> if the widget is currently read-only,
     * <code>false</code> if the widget is currently editable
     */
    public boolean isReadOnly() {
        return getElement().getPropertyBoolean("readOnly");
    }

    @Override
    public void onBrowserEvent(final Event event) {
        final int type = DOM.eventGetType(event);
        if ((type & Event.KEYEVENTS) != 0) {
            // Fire the keyboard event. Hang on to the current event object so that
            // cancelKey() and setKey() can be implemented.
            currentEvent = event;
            // Call the superclass' onBrowserEvent as that includes the key event
            // handlers.
            super.onBrowserEvent(event);
            currentEvent = null;
        } else {
            // Handles Focus and Click events.
            super.onBrowserEvent(event);
        }
    }

    /**
     * Selects all of the text in the box.
     * <p>
     * This will only work when the widget is attached to the document and not
     * hidden.
     */
    public void selectAll() {
        final int length = getText().length();
        if (length > 0) {
            setSelectionRange(0, length);
        }
    }

    public void setAlignment(final TextAlignment align) {
        getElement().getStyle().setProperty("textAlign", align.getTextAlignString());
    }

    /**
     * Sets the cursor position.
     * <p>
     * This will only work when the widget is attached to the document and not
     * hidden.
     *
     * @param pos the new cursor position
     */
    public void setCursorPos(final int pos) {
        setSelectionRange(pos, 0);
    }

    public void setDirection(final Direction direction) {
        BidiUtils.setDirectionOnElement(getElement(), direction);
    }

    /**
     * Toggles on / off direction estimation.
     */
    public void setDirectionEstimator(final boolean enabled) {
        autoDirHandler.setDirectionEstimator(enabled);
    }

    /**
     * Sets the direction estimation model of the auto-dir handler.
     */
    public void setDirectionEstimator(final DirectionEstimator directionEstimator) {
        autoDirHandler.setDirectionEstimator(directionEstimator);
    }

    public void setName(final String name) {
        getElement().setPropertyString("name", name);
    }

    /**
     * Turns read-only mode on or off.
     *
     * @param readOnly if <code>true</code>, the widget becomes read-only; if
     *                 <code>false</code> the widget becomes editable
     */
    public void setReadOnly(final boolean readOnly) {
        getElement().setPropertyBoolean("readOnly", readOnly);
        final String readOnlyStyle = "readonly";
        if (readOnly) {
            addStyleDependentName(readOnlyStyle);
        } else {
            removeStyleDependentName(readOnlyStyle);
        }
    }

    /**
     * Sets the range of text to be selected.
     * <p>
     * This will only work when the widget is attached to the document and not
     * hidden.
     *
     * @param pos    the position of the first character to be selected
     * @param length the number of characters to be selected
     */
    public void setSelectionRange(final int pos, final int length) {
        // Setting the selection range will not work for unattached elements.
        if (!isAttached()) {
            return;
        }

        if (length < 0) {
            throw new IndexOutOfBoundsException(
                    "Length must be a positive integer. Length: " + length);
        }
        if (pos < 0 || length + pos > getText().length()) {
            throw new IndexOutOfBoundsException("From Index: " + pos + "  To Index: "
                    + (pos + length) + "  Text Length: " + getText().length());
        }
        setSelectionRange(getElement(), pos, length);
    }

    /**
     * Sets this object's text. Note that some browsers will manipulate the text
     * before adding it to the widget. For example, most browsers will strip all
     * <code>\r</code> from the text, except IE which will add a <code>\r</code>
     * before each <code>\n</code>. Use {@link #getText()} to get the text
     * directly from the widget.
     *
     * @param text the object's new text
     */
    public void setText(final String text) {
        getElement().setPropertyString("value",
                text != null
                        ? text
                        : "");
        autoDirHandler.refreshDirection();
    }

    public void setValue(final T value) {
        setValue(value, false);
    }

    public void setValue(final T value, final boolean fireEvents) {
        final T oldValue = fireEvents
                ? getValue()
                : null;
        setText(renderer.render(value));
        if (fireEvents) {
            final T newValue = getValue();
            ValueChangeEvent.fireIfNotEqual(this, oldValue, newValue);
        }
    }

    @Override
    protected void onLoad() {
        super.onLoad();
        autoDirHandler.refreshDirection();
    }

    private native int getCursorPos(Element elem) /*-{
        // Guard needed for FireFox.
        try{
            return elem.selectionStart;
        } catch (e) {
            return 0;
        }
    }-*/;

    private native int getSelectionLength(Element elem) /*-{
        // Guard needed for FireFox.
        try{
            return elem.selectionEnd - elem.selectionStart;
        } catch (e) {
            return 0;
        }
    }-*/;

    private native void setSelectionRange(Element elem, int pos, int length) /*-{
        try {
            elem.setSelectionRange(pos, pos + length);
        } catch (e) {
            // Firefox throws exception if TextBox is not visible, even if attached
        }
    }-*/;
}
