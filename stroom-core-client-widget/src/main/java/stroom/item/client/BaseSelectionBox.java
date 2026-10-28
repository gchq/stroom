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

package stroom.item.client;

import stroom.svg.client.IconBox;
import stroom.widget.component.client.AriaPanel;
import stroom.widget.form.client.AccessibleName;
import stroom.widget.form.client.TextBox;
import stroom.widget.util.client.SelectionType;
import stroom.widget.util.client.TypedKey;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.logical.shared.HasValueChangeHandlers;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.Focus;
import com.google.gwt.user.client.ui.Widget;
import com.google.web.bindery.event.shared.HandlerRegistration;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/// A box for choosing a value from a list, a WAI-ARIA combobox: its text box (`role="combobox"`) opens
/// the list below it (a [SelectionPopup]) on a click, Enter, Down, Up or Alt+Down, and in a box that
/// can't be typed in, on Space or a typed character, which the list goes to, or Home or End, which
/// open it at its first or last option. The list takes the focus,
/// and Enter or Alt+Up there chooses an option, and Escape closes it. Its drop-down arrow is an
/// [IconBox]'s.
///
/// Its markup is `BaseSelectionBox.ui.xml`, styled by selection-box.css: `selection-box`,
/// `selection-box__control`, `selection-box__value` and `selection-box__input`, and the modifiers
/// `selection-box--select-only`, `selection-box--disabled` and `selection-box--read-only`.
public class BaseSelectionBox<T, I extends SelectionItem>
        extends Composite
        implements Focus, HasValueChangeHandlers<T> {

    private static final Binder BINDER = GWT.create(Binder.class);
    // The block's classes (selection-box.css)
    private static final String BLOCK = "selection-box";
    private static final String DISABLED = BLOCK + "--disabled";
    private static final String READ_ONLY = BLOCK + "--read-only";
    private static final String SELECT_ONLY = BLOCK + "--select-only";

    @UiField
    AriaPanel renderBox;
    @UiField
    TextBox textBox;
    @UiField
    IconBox iconBox;
    private SelectionListModel<T, I> model;
    private T value;
    private SelectionPopup<T, I> popup;
    private boolean allowTextEntry;
    private boolean isEnabled = true;
    private boolean readOnly;
    private Supplier<SafeHtml> popupTextSupplier;
    private Consumer<T> onChange;

    public BaseSelectionBox() {
        initWidget(BINDER.createAndBindUi(this));
        // Its own children's handlers, which go with them
        iconBox.addClickHandler(event -> togglePopup());
        textBox.addClickHandler(event -> onTextBoxClick());
        textBox.addKeyDownHandler(event -> onKeyDown(event.getNativeEvent()));
        textBox.addValueChangeHandler(event -> fireChange());
    }

    /// Lets the value be typed, rather than only chosen from the list. Only the text box shows in a
    /// box that can be typed in, and only the picture of the value in one that can't
    /// (`selection-box--select-only`, which opens its list when clicked).
    ///
    /// @param allowTextEntry Whether the value can be typed.
    public void setAllowTextEntry(final boolean allowTextEntry) {
        this.allowTextEntry = allowTextEntry;
        textBox.setReadOnly(readOnly || !allowTextEntry);
        iconBox.setStyleName(SELECT_ONLY, !allowTextEntry);
    }

    /// @param onChange Told the value chosen when the user chooses one (or `setValue(value, true)`
    ///                 changes it), or null for nothing.
    public void setOnChange(final Consumer<T> onChange) {
        this.onChange = onChange;
    }

    /// Names the box for screen readers where no label names it, e.g. a box beside another field.
    /// Bound to the `ariaLabel` attribute in ui.xml.
    ///
    /// @param ariaLabel The name, or null or blank for none.
    public void setAriaLabel(final String ariaLabel) {
        textBox.setAriaLabel(ariaLabel);
    }

    private void fireChange() {
        ValueChangeEvent.fire(this, value);
        if (onChange != null) {
            onChange.accept(value);
        }
    }

    public void registerPopupTextProvider(final Supplier<SafeHtml> popupTextSupplier) {
        this.popupTextSupplier = popupTextSupplier;
    }

    private void onTextBoxClick() {
        if (!allowTextEntry) {
            togglePopup();
        }
    }

    // The keys of the WAI-ARIA combobox, while the list is closed (once open, the focus is in the
    // list, which has its own): Enter, Down, Up and Alt+Down open it; in a box that can't be typed in,
    // Space does too, Home and End open it at its first or last option, and typing a character opens
    // it and goes to what was typed (into the list's quick filter, or to the first option that starts
    // with it)
    private void onKeyDown(final NativeEvent event) {
        final int keyCode = event.getKeyCode();
        if (keyCode == KeyCodes.KEY_ENTER) {
            togglePopup();
        } else if (keyCode == KeyCodes.KEY_DOWN || keyCode == KeyCodes.KEY_UP) {
            event.preventDefault();
            openPopup(null);
        } else if (!allowTextEntry) {
            final String key = TypedKey.keyOf(event);
            if (keyCode == KeyCodes.KEY_SPACE) {
                event.preventDefault();
                openPopup(null);
            } else if (keyCode == KeyCodes.KEY_HOME || keyCode == KeyCodes.KEY_END) {
                event.preventDefault();
                openPopup(null, keyCode == KeyCodes.KEY_END);
            } else if (TypedKey.isTypedCharacter(event, key)) {
                event.preventDefault();
                if (popup != null) {
                    // Typed before the list took the focus
                    popup.type(key);
                } else {
                    openPopup(key);
                }
            }
        }
    }

    private boolean isEnabled() {
        return this.isEnabled;
    }

    /// @param model The values to choose from, and how each is shown.
    public void setModel(final SelectionListModel<T, I> model) {
        this.model = model;
    }

    // Opens the list, or closes it if it is open (e.g. a second click on the box)
    private void togglePopup() {
        if (popup != null) {
            hidePopup();
        } else {
            openPopup(null);
        }
    }

    // Opens the list, unless it is open or the value can't be changed, going to what was typed to
    // open it, if anything
    private void openPopup(final String typed) {
        openPopup(typed, null);
    }

    // Opens the list as openPopup(String) does, or with the focus on its last option (true) or its
    // first (false), as End and Home open it
    private void openPopup(final String typed, final Boolean atLast) {
        if (readOnly || !isEnabled() || popup != null) {
            return;
        }
        popup = new SelectionPopup<>();
        popup.setModel(model);
        popup.addAutoHidePartner(textBox.getElement());
        popup.addAutoHidePartner(iconBox.getElement());
        popup.setPopupTextProvider(popupTextSupplier);
        // The list is named after the box, and the box says that it is open and controls it
        popup.setAccessibleName(getAccessibleName(textBox.getElement()));
        textBox.getElement().setAttribute("aria-expanded", "true");
        textBox.getElement().setAttribute("aria-controls", popup.getListBoxId());

        final I selectionItem = model.wrap(value);
        if (selectionItem != null) {
            popup.getSelectionModel().setSelected(selectionItem, true, new SelectionType(), false);
        }
        final List<HandlerRegistration> handlerRegistrations = new ArrayList<>();
        popup.setOnClose(() -> {
            textBox.getElement().setAttribute("aria-expanded", "false");
            textBox.getElement().removeAttribute("aria-controls");
            focus();
            for (final HandlerRegistration handlerRegistration : handlerRegistrations) {
                handlerRegistration.removeHandler();
            }
            handlerRegistrations.clear();
            popup = null;
        });
        handlerRegistrations.add(popup.getSelectionModel().addSelectionHandler(e -> {
            final I selected = popup.getSelectionModel().getSelected();
            if (selected == null) {
                setValue(null, true);
            } else {
                setValue(model.unwrap(selected), true);
            }
            hidePopup();
        }));

        if (atLast == null) {
            popup.show(textBox.getElement(), typed);
        } else {
            popup.showAtFirstOrLast(textBox.getElement(), atLast);
        }
    }

    // The box's name, as a screen reader works it out: the elements it is labelled by
    // (aria-labelledby, as a form group names it), else its aria-label, else its label
    private static native String getAccessibleName(Element element) /*-{
        var ids = (element.getAttribute("aria-labelledby") || "").trim();
        if (ids) {
            var doc = element.ownerDocument;
            var text = ids.split(/\s+/).map(function (id) {
                var label = doc.getElementById(id);
                return label ? label.textContent.trim() : "";
            }).join(" ").trim();
            if (text) {
                return text;
            }
        }
        var ariaLabel = (element.getAttribute("aria-label") || "").trim();
        if (ariaLabel) {
            return ariaLabel;
        }
        if (element.labels && element.labels.length > 0) {
            return element.labels[0].textContent.trim();
        }
        return "";
    }-*/;

    private void hidePopup() {
        popup.hide();
        popup = null;
    }

    @Override
    public void focus() {
        textBox.setFocus(true);
    }

    public void setName(final String name) {
        textBox.setName(name);
    }

    /// Names its text box for assistive technology, where no label is for it (see [AccessibleName]).
    ///
    /// @param name The name, e.g. `Type`.
    public void setAccessibleName(final String name) {
        AccessibleName.set(this, name);
    }

    public void setEnabled(final boolean enabled) {
        this.isEnabled = enabled;
        // An open list would still let the value be changed
        if (!enabled && popup != null) {
            hidePopup();
        }
        textBox.setEnabled(enabled);
        iconBox.setReadOnly(!enabled || readOnly);
        // The disabled look (selection-box.css), rather than fading the value
        iconBox.setStyleName(DISABLED, !enabled);
    }

    /// Makes the value read only: it can be read and stays in the tab order, but the list
    /// doesn't open and typing doesn't change it. It looks like the normal field with its value
    /// greyed.
    ///
    /// @param readOnly Whether the value is read only.
    public void setReadOnly(final boolean readOnly) {
        this.readOnly = readOnly;
        if (readOnly && popup != null) {
            hidePopup();
        }
        textBox.setReadOnly(readOnly || !allowTextEntry);
        if (readOnly) {
            textBox.getElement().setAttribute("aria-readonly", "true");
        } else {
            textBox.getElement().removeAttribute("aria-readonly");
        }
        iconBox.setReadOnly(!isEnabled() || readOnly);
        iconBox.setStyleName(READ_ONLY, readOnly);
    }

    /// @return Whether the value is read only.
    public boolean isReadOnly() {
        return readOnly;
    }

    public T getValue() {
        return value;
    }

    public void setValue(final T value) {
        setValue(value, false);
    }

    public void setValue(final T value, final boolean fireEvents) {
        final boolean changed = !Objects.equals(this.value, value);
        this.value = value;

        String newText = "";
        String newHTML = "";
        final SelectionItem selectionItem = value != null
                ? model.wrap(value)
                : getNoValueItem();
        if (selectionItem != null) {
            newText = selectionItem.getLabel();
            newHTML = selectionItem.getRenderedLabel().asString();
        }

        textBox.setValue(newText);
        renderBox.getElement().setInnerHTML(newHTML);

        if (fireEvents && changed) {
            fireChange();
        }
    }

    /// @return The item that stands for no value, e.g. `Never`, shown when the value is null, or
    /// null for none (the box is then empty).
    protected SelectionItem getNoValueItem() {
        return null;
    }

    public String getText() {
        return textBox.getText();
    }

    @Override
    public com.google.gwt.event.shared.HandlerRegistration addValueChangeHandler(final ValueChangeHandler<T> handler) {
        return addHandler(handler, ValueChangeEvent.getType());
    }


    // --------------------------------------------------------------------------------


    // Generic in its owner, as the box is
    @SuppressWarnings("rawtypes")
    interface Binder extends UiBinder<Widget, BaseSelectionBox> {

    }
}
