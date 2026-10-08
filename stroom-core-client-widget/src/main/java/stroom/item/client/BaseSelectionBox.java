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
import stroom.svg.shared.SvgImage;
import stroom.widget.util.client.SelectionType;

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

public class BaseSelectionBox<T, I extends SelectionItem>
        extends Composite
        implements SelectionBoxView<T, I>, Focus, HasValueChangeHandlers<T> {

    public static final String POINTER_CLASS_NAME = "pointer";
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
            registerHandler(svgIconBox.addClickHandler(event -> showPopup()));
            registerHandler(textBox.addClickHandler(event -> onTextBoxClick()));
            registerHandler(textBox.addKeyDownHandler(event -> {
                final int keyCode = event.getNativeKeyCode();
                if (KeyCodes.KEY_ENTER == keyCode) {
                    showPopup();
                }
            }));
            registerHandler(textBox.addValueChangeHandler(event ->
                    ValueChangeEvent.fire(BaseSelectionBox.this, value)));
        }
    };

    public BaseSelectionBox() {
        textBox = new TextBox();
        textBox.addStyleName("SelectionBox-textBox stroom-control allow-focus");

        renderBox = new SimplePanel();
        renderBox.addStyleName("SelectionBox-renderBox stroom-control allow-focus");
        // A picture of the value, which the text box (the named control) also holds, so screen
        // readers don't read it twice
        renderBox.getElement().setAttribute("aria-hidden", "true");

        final FlowPanel outer = new FlowPanel();
        outer.addStyleName("SelectionBox-outer");
        outer.add(renderBox);
        outer.add(textBox);

        svgIconBox = new SvgIconBox();
        svgIconBox.addStyleName("SelectionBox");
        svgIconBox.setWidget(outer, SvgImage.DROP_DOWN);

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
        updatePointer();
    }

    public void registerPopupTextProvider(final Supplier<SafeHtml> popupTextSupplier) {
        this.popupTextSupplier = popupTextSupplier;
    }

    private void updatePointer() {
        if (allowTextEntry || !isEnabled() || readOnly) {
            textBox.removeStyleName(POINTER_CLASS_NAME);
        } else {
            textBox.addStyleName(POINTER_CLASS_NAME);
        }

        if (isEnabled() && !readOnly) {
            svgIconBox.addStyleName(POINTER_CLASS_NAME);
        } else {
            svgIconBox.removeStyleName(POINTER_CLASS_NAME);
        }
    }

    private void onTextBoxClick() {
        if (!allowTextEntry) {
            showPopup();
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

    private void showPopup() {
        if (readOnly || !isEnabled()) {
            return;
        }
        if (popup != null) {
//            GWT.log("Hiding popup");
            hidePopup();

        } else {
            popup = new SelectionPopup<>();
            popup.init(model);
            popup.addAutoHidePartner(textBox.getElement());
            popup.addAutoHidePartner(svgIconBox.getElement());
            popup.registerPopupTextProvider(popupTextSupplier);

            final I selectionItem = model.wrap(value);
            if (selectionItem != null) {
                popup.getSelectionModel().setSelected(selectionItem, true, new SelectionType(), false);
            }
            final List<HandlerRegistration> handlerRegistrations = new ArrayList<>();
            handlerRegistrations.add(popup.addCloseHandler(event -> {
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

            popup.show(textBox.getElement());
        }
    }

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
        // The disabled look (SelectionBox.css), rather than fading the value
        svgIconBox.setStyleName("disabled", !enabled);
        updatePointer();
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
        svgIconBox.setStyleName("readonly", readOnly);
        updatePointer();
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
