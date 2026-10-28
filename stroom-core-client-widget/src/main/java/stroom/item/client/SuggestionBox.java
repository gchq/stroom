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

package stroom.item.client;

import stroom.widget.component.client.Component;
import stroom.widget.form.client.AccessibleName;
import stroom.widget.form.client.TextBox;
import stroom.widget.util.client.CssProperties;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.EventTarget;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyDownHandler;
import com.google.gwt.event.dom.client.MouseDownEvent;
import com.google.gwt.event.logical.shared.HasValueChangeHandlers;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.Event.NativePreviewEvent;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Focus;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// A text field that suggests what to type, e.g. the values an expression term's field holds: a
/// WAI-ARIA editable combobox with list autocomplete. Its value is the field's text; the suggestions
/// are only hints. Typing (or a click, or Down) shows the suggestions for what has been typed, from
/// its [SuggestionSource], in a list below the field. The focus stays in the field: Down and Up move
/// through the suggestions (`aria-activedescendant`), Enter or a click puts one in the field, and
/// Escape, Tab or leaving the field closes the list, keeping what was typed.
///
/// Keys the list uses (Down, Up, and Enter and Escape while it is open) don't reach the handlers
/// added with [#addKeyDownHandler(KeyDownHandler)], so e.g. Enter that chooses a suggestion doesn't
/// also run a query.
///
/// Unlike a [SelectionBox], whose value is one of its list's, any text may be typed. Its markup is
/// `SuggestionBox.ui.xml`, styled by `suggestion-box.css`: `suggestion-box`, `__input`, `__popup`,
/// `__list`, `__option` and `__option--active`.
public class SuggestionBox extends Component implements Focus, HasValueChangeHandlers<String> {

    private static final Binder BINDER = GWT.create(Binder.class);
    private static final String OPTION = "suggestion-box__option";
    private static final String ACTIVE = OPTION + "--active";
    private static final String INDEX = "data-index";

    @UiField
    TextBox input;

    private final PopupPanel popup = new PopupPanel(true, false) {
        @Override
        protected void onPreviewNativeEvent(final NativePreviewEvent event) {
            super.onPreviewNativeEvent(event);
            // Escape closes the list before anything else sees it (e.g. a dialog, which would
            // close), as a selection box's list does
            if (event.getTypeInt() == Event.ONKEYDOWN
                && event.getNativeEvent().getKeyCode() == KeyCodes.KEY_ESCAPE) {
                event.cancel();
                hideList();
            }
        }
    };
    private final FlowPanel list = new FlowPanel();
    private final String listId = DOM.createUniqueId();
    private final List<String> suggestions = new ArrayList<>();
    // The suggestion Down and Up have moved to, or -1 for none
    private int active = -1;
    private SuggestionSource source;
    // Counts the fetches, so that only the latest's answer is shown
    private int fetches;
    // The text last typed or set, to tell when typing changes it
    private String lastText = "";
    // Whether the key going down now was the list's, which the handlers added from outside don't get
    private boolean keyUsed;

    /// Makes an empty box, with no suggestions until it is given a source.
    public SuggestionBox() {
        initWidget(BINDER.createAndBindUi(this));
        final Element field = input.getElement();
        field.setAttribute("aria-autocomplete", "list");
        field.setAttribute("aria-expanded", "false");
        field.setAttribute("aria-controls", listId);

        list.setStyleName("suggestion-box__list");
        list.getElement().setId(listId);
        list.getElement().setAttribute("role", "listbox");
        popup.setStyleName("popup suggestion-box__popup");
        popup.add(list);
        popup.addAutoHidePartner(field);
        popup.addCloseHandler(event -> onListClosed());
        // A press on a suggestion chooses it, and keeps the focus in the field
        list.addDomHandler(this::onListMouseDown, MouseDownEvent.getType());

        input.addKeyDownHandler(this::onKeyDown);
        input.addKeyUpHandler(event -> onTyped());
        input.addClickHandler(event -> showSuggestions());
        input.addBlurHandler(event -> hideList());
        input.addValueChangeHandler(event -> ValueChangeEvent.fire(this, getText()));
    }

    /// @param source Where the suggestions come from, or null for none.
    public void setSource(final SuggestionSource source) {
        this.source = source;
    }

    /// @return The field's text, which is the box's value.
    public String getText() {
        return input.getText();
    }

    /// Sets the field's text, telling no one.
    ///
    /// @param text The text, or null for none.
    public void setText(final String text) {
        input.setText(text);
        lastText = getText();
    }

    /// @return The field's text, which is the box's value.
    public String getValue() {
        return getText();
    }

    /// Sets the field's text, telling no one.
    ///
    /// @param value The text, or null for none.
    public void setValue(final String value) {
        setText(value);
    }

    /// Shows the suggestions for the field's text, as a click on the field does.
    public void showSuggestions() {
        if (!isReadOnly() && input.isEnabled()) {
            fetch(getText());
        }
    }

    /// @return Whether the list of suggestions is open.
    public boolean isListShowing() {
        return popup.isShowing();
    }

    /// Closes the list of suggestions, keeping what was typed.
    public void hideList() {
        // Any answer still to come is for a list no longer wanted
        fetches++;
        if (popup.isShowing()) {
            popup.hide();
        }
    }

    /// Stops a fetch of suggestions still waiting, and closes the list, e.g. when the box goes.
    public void cancel() {
        hideList();
        if (source != null) {
            source.cancel();
        }
    }

    /// Adds a handler of the field's key presses, which isn't told of the keys the list uses (see the
    /// class's description).
    ///
    /// @param handler The handler.
    /// @return Its registration.
    public HandlerRegistration addKeyDownHandler(final KeyDownHandler handler) {
        return input.addKeyDownHandler(event -> {
            if (!keyUsed) {
                handler.onKeyDown(event);
            }
        });
    }

    @Override
    public HandlerRegistration addValueChangeHandler(final ValueChangeHandler<String> handler) {
        return addHandler(handler, ValueChangeEvent.getType());
    }

    /// Names the field for screen readers, where no label names it.
    ///
    /// @param name The name, e.g. `Value`.
    public void setAccessibleName(final String name) {
        AccessibleName.set(input, name);
    }

    /// @param enabled Whether the field can be used.
    public void setEnabled(final boolean enabled) {
        input.setEnabled(enabled);
        if (!enabled) {
            hideList();
        }
    }

    @Override
    public void focus() {
        input.setFocus(true);
    }

    @Override
    protected void onReadOnlyChange(final boolean readOnly) {
        input.setReadOnly(readOnly);
        if (readOnly) {
            hideList();
        }
    }

    // The keys of an editable combobox; the field keeps the rest (e.g. Home and End move the caret)
    private void onKeyDown(final KeyDownEvent event) {
        keyUsed = false;
        final int keyCode = event.getNativeKeyCode();
        final boolean open = popup.isShowing();
        if (keyCode == KeyCodes.KEY_DOWN) {
            keyUsed = true;
            event.preventDefault();
            if (open && !event.isAltKeyDown()) {
                moveTo(Math.min(active + 1, suggestions.size() - 1));
            } else if (!open) {
                showSuggestions();
            }
        } else if (keyCode == KeyCodes.KEY_UP && open) {
            keyUsed = true;
            event.preventDefault();
            if (event.isAltKeyDown()) {
                hideList();
            } else {
                moveTo(Math.max(active - 1, 0));
            }
        } else if (keyCode == KeyCodes.KEY_ENTER && open && active >= 0) {
            keyUsed = true;
            event.preventDefault();
            choose(active);
        } else if (keyCode == KeyCodes.KEY_ESCAPE && open) {
            keyUsed = true;
            event.preventDefault();
            hideList();
        } else if (keyCode == KeyCodes.KEY_TAB) {
            hideList();
        }
    }

    // Typing changes the text: show the suggestions for it
    private void onTyped() {
        final String text = getText();
        if (!Objects.equals(text, lastText)) {
            lastText = text;
            showSuggestions();
        }
    }

    private void fetch(final String text) {
        if (source == null) {
            return;
        }
        final int fetch = ++fetches;
        final String typed = text == null
                ? ""
                : text;
        source.fetch(typed, found -> {
            // Only the answer to the latest text, while the field is still on the page
            if (fetch == fetches && isAttached()) {
                showList(found);
            }
        });
    }

    private void showList(final List<String> found) {
        suggestions.clear();
        if (found != null) {
            suggestions.addAll(found);
        }
        active = -1;
        input.getElement().removeAttribute("aria-activedescendant");
        list.clear();
        if (suggestions.isEmpty()) {
            hideList();
            return;
        }
        for (int i = 0; i < suggestions.size(); i++) {
            list.getElement().appendChild(makeOption(i, suggestions.get(i)));
        }
        // At least as wide as the field, below it
        CssProperties.set(popup.getElement(), "--suggestion-box__width", input.getOffsetWidth() + "px");
        popup.setPopupPositionAndShow((width, height) -> popup.setPopupPosition(
                input.getAbsoluteLeft(),
                input.getAbsoluteTop() + input.getOffsetHeight()));
        input.getElement().setAttribute("aria-expanded", "true");
    }

    private Element makeOption(final int index, final String text) {
        final Element option = Document.get().createDivElement();
        option.setId(listId + "-" + index);
        option.setClassName(OPTION);
        option.setAttribute("role", "option");
        option.setAttribute("aria-selected", "false");
        option.setAttribute(INDEX, String.valueOf(index));
        option.setInnerText(text);
        return option;
    }

    // Marks a suggestion as the one Enter would choose, scrolling it into the list's view
    private void moveTo(final int index) {
        if (index < 0 || index >= suggestions.size()) {
            return;
        }
        if (active >= 0) {
            setActive(optionAt(active), false);
        }
        active = index;
        final Element option = optionAt(index);
        setActive(option, true);
        input.getElement().setAttribute("aria-activedescendant", option.getId());
        scrollIntoView(option);
    }

    private Element optionAt(final int index) {
        return list.getElement().getChild(index).cast();
    }

    private static void setActive(final Element option, final boolean active) {
        if (active) {
            option.addClassName(ACTIVE);
        } else {
            option.removeClassName(ACTIVE);
        }
        option.setAttribute("aria-selected", String.valueOf(active));
    }

    // Puts a suggestion in the field, which keeps the focus, and says the value changed
    private void choose(final int index) {
        final String text = suggestions.get(index);
        hideList();
        setText(text);
        ValueChangeEvent.fire(this, text);
    }

    private void onListMouseDown(final MouseDownEvent event) {
        event.preventDefault();
        final EventTarget target = event.getNativeEvent().getEventTarget();
        if (Element.is(target)) {
            final Element option = optionOf(Element.as(target));
            if (option != null) {
                choose(Integer.parseInt(option.getAttribute(INDEX)));
            }
        }
    }

    // The option an element is in, or null
    private Element optionOf(final Element element) {
        for (Element e = element; e != null && e != list.getElement(); e = e.getParentElement()) {
            if (e.hasAttribute(INDEX)) {
                return e;
            }
        }
        return null;
    }

    private void onListClosed() {
        active = -1;
        input.getElement().setAttribute("aria-expanded", "false");
        input.getElement().removeAttribute("aria-activedescendant");
    }

    private static native void scrollIntoView(Element element) /*-{
        element.scrollIntoView({block: "nearest"});
    }-*/;


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, SuggestionBox> {

    }
}
