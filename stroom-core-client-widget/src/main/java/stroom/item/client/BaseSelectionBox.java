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

import stroom.svg.client.SvgIconBox;
import stroom.widget.util.client.SelectionType;

import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.logical.shared.HasValueChangeHandlers;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Focus;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.TextBox;
import com.google.web.bindery.event.shared.HandlerRegistration;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/// A box for choosing a value from a list, a WAI-ARIA combobox: its text box (`role="combobox"`) opens
/// the list below it (a [SelectionPopup]) on a click, Enter, Down, Up or Alt+Down, and in a box that
/// can't be typed in, on Space or a typed character, which the list goes to. The list takes the focus,
/// and Enter or Alt+Up there chooses an option, and Escape closes it. Its drop-down arrow is an
/// [SvgIconBox]'s.
///
/// Styled by selection-box.css: `selection-box`, `selection-box__control`, `selection-box__value` and
/// `selection-box__input`, and the modifiers `selection-box--select-only`, `selection-box--disabled`
/// and `selection-box--readonly`.
public class BaseSelectionBox<T, I extends SelectionItem>
        extends Composite
        implements SelectionBoxView<T, I>, Focus, HasValueChangeHandlers<T> {

    // The block's classes (selection-box.css)
    private static final String BLOCK = "selection-box";
    private static final String DISABLED = BLOCK + "--disabled";
    private static final String READ_ONLY = BLOCK + "--readonly";
    private static final String SELECT_ONLY = BLOCK + "--select-only";

    private final SimplePanel renderBox;
    private final TextBox textBox;
    private final SvgIconBox svgIconBox;
    private SelectionListModel<T, I> model;
    private T value;
    private SelectionPopup<T, I> popup;
    private boolean allowTextEntry;
    private boolean isEnabled = true;
    private boolean readOnly;
    private Supplier<SafeHtml> popupTextSupplier;

    private final EventBinder eventBinder = new EventBinder() {
        @Override
        protected void onBind() {
            registerHandler(svgIconBox.addClickHandler(event -> togglePopup()));
            registerHandler(textBox.addClickHandler(event -> onTextBoxClick()));
            registerHandler(textBox.addKeyDownHandler(event -> onKeyDown(event.getNativeEvent())));
            registerHandler(textBox.addValueChangeHandler(event ->
                    ValueChangeEvent.fire(BaseSelectionBox.this, value)));
        }
    };

    public BaseSelectionBox() {
        textBox = new TextBox();
        textBox.addStyleName(BLOCK + "__input stroom-control allow-focus");

        renderBox = new SimplePanel();
        renderBox.addStyleName(BLOCK + "__value stroom-control allow-focus");
        // A picture of the value, which the text box (the named control) also holds, so screen
        // readers don't read it twice
        renderBox.getElement().setAttribute("aria-hidden", "true");
        // The text box opens a list of the values to choose from
        textBox.getElement().setAttribute("role", "combobox");
        textBox.getElement().setAttribute("aria-haspopup", "listbox");
        textBox.getElement().setAttribute("aria-expanded", "false");

        final FlowPanel outer = new FlowPanel();
        outer.addStyleName(BLOCK + "__control");
        outer.add(renderBox);
        outer.add(textBox);

        svgIconBox = new SvgIconBox();
        svgIconBox.addStyleName(BLOCK);
        svgIconBox.setDropDownWidget(outer);

        initWidget(svgIconBox);
        setAllowTextEntry(false);
    }

    public void setAllowTextEntry(final boolean allowTextEntry) {
        this.allowTextEntry = allowTextEntry;
        textBox.setReadOnly(readOnly || !allowTextEntry);
        textBox.getElement().getStyle().setOpacity(allowTextEntry
                ? 1
                : 0);
        renderBox.getElement().getStyle().setOpacity(allowTextEntry
                ? 0
                : 1);
        // A box that can't be typed in opens its list when clicked (selection-box.css)
        svgIconBox.setStyleName(SELECT_ONLY, !allowTextEntry);
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
    // Space does too, and typing a character opens it and goes to what was typed (into the list's
    // quick filter, or to the first option that starts with it)
    private void onKeyDown(final NativeEvent event) {
        final int keyCode = event.getKeyCode();
        if (keyCode == KeyCodes.KEY_ENTER) {
            togglePopup();
        } else if (keyCode == KeyCodes.KEY_DOWN || keyCode == KeyCodes.KEY_UP) {
            event.preventDefault();
            openPopup(null);
        } else if (!allowTextEntry) {
            final String key = SelectionList.keyOf(event);
            if (keyCode == KeyCodes.KEY_SPACE) {
                event.preventDefault();
                openPopup(null);
            } else if (SelectionList.isTypedCharacter(event, key)) {
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

    @Override
    protected void onLoad() {
        eventBinder.bind();
    }

    @Override
    protected void onUnload() {
        eventBinder.unbind();
    }

    @Override
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
        if (readOnly || !isEnabled() || popup != null) {
            return;
        }
        popup = new SelectionPopup<>();
        popup.init(model);
        popup.addAutoHidePartner(textBox.getElement());
        popup.addAutoHidePartner(svgIconBox.getElement());
        popup.registerPopupTextProvider(popupTextSupplier);
        // The list is named after the box, and the box says that it is open and controls it
        popup.setAccessibleName(getAccessibleName(textBox.getElement()));
        textBox.getElement().setAttribute("aria-expanded", "true");
        textBox.getElement().setAttribute("aria-controls", popup.getListBoxId());

        final I selectionItem = model.wrap(value);
        if (selectionItem != null) {
            popup.getSelectionModel().setSelected(selectionItem, true, new SelectionType(), false);
        }
        final List<HandlerRegistration> handlerRegistrations = new ArrayList<>();
        handlerRegistrations.add(popup.addCloseHandler(event -> {
            textBox.getElement().setAttribute("aria-expanded", "false");
            textBox.getElement().removeAttribute("aria-controls");
            focus();
            for (final HandlerRegistration handlerRegistration : handlerRegistrations) {
                handlerRegistration.removeHandler();
            }
            handlerRegistrations.clear();
            popup = null;
        }));
        handlerRegistrations.add(popup.getSelectionModel().addSelectionHandler(e -> {
            final I selected = popup.getSelectionModel().getSelected();
            if (selected == null) {
                setValue(null, true);
            } else {
                setValue(model.unwrap(selected), true);
            }
            hidePopup();
        }));

        popup.show(textBox.getElement(), typed);
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

    public void setEnabled(final boolean enabled) {
        this.isEnabled = enabled;
        // An open list would still let the value be changed
        if (!enabled && popup != null) {
            hidePopup();
        }
        textBox.setEnabled(enabled);
        svgIconBox.setReadonly(!enabled || readOnly);
        // The disabled look (selection-box.css), rather than fading the value
        svgIconBox.setStyleName(DISABLED, !enabled);
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
        svgIconBox.setReadonly(!isEnabled() || readOnly);
        svgIconBox.setStyleName(READ_ONLY, readOnly);
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
        if (value != null) {
            final SelectionItem selectionItem = model.wrap(value);
            newText = selectionItem.getLabel();
            newHTML = selectionItem.getRenderedLabel().asString();
        }

        textBox.setValue(newText);
        renderBox.getElement().setInnerHTML(newHTML);

        if (fireEvents && changed) {
            ValueChangeEvent.fire(this, value);
        }
    }

    public String getText() {
        return textBox.getText();
    }

    @Override
    public com.google.gwt.event.shared.HandlerRegistration addValueChangeHandler(final ValueChangeHandler<T> handler) {
        return addHandler(handler, ValueChangeEvent.getType());
    }
}
