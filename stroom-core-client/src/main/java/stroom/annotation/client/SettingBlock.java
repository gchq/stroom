/*
 * Copyright 2025 Crown Copyright
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

package stroom.annotation.client;

import stroom.svg.shared.SvgImage;
import stroom.widget.util.client.KeyBinding;
import stroom.widget.util.client.KeyBinding.Action;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.HasClickHandlers;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HasWidgets;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.Collections;
import java.util.Iterator;

/// A setting of an annotation (e.g. its status), shown as its value with a ⋮ hint, that a click
/// anywhere on changes. When it has a title (e.g. `Change Status`), its ⋮ is a button named by it
/// that the keyboard reaches and presses (Enter or Space), clicking the block, so opening its
/// chooser (a dialog) as a click does. A setting without one (e.g. one holding a text box, which
/// takes the focus itself) keeps the ⋮ as a hint only.
public class SettingBlock extends Composite implements HasWidgets, HasClickHandlers {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    FlowPanel formGroupPanel;
    @UiField
    SimplePanel icon;

    private Widget childWidget = null;

    public SettingBlock() {
        initWidget(BINDER.createAndBindUi(this));
        icon.getElement().setInnerHTML(SvgImage.ELLIPSES_VERTICAL.getSvg());
        // A hint only, until the setting has a title (see setTitle)
        icon.getElement().setAttribute("aria-hidden", "true");
        icon.addDomHandler(event -> {
            final int keyCode = event.getNativeKeyCode();
            if (isButton() && (keyCode == KeyCodes.KEY_ENTER || keyCode == KeyCodes.KEY_SPACE)) {
                event.preventDefault();
                event.stopPropagation();
                icon.getElement().dispatchEvent(Document.get().createClickEvent(
                        1, 0, 0, 0, 0, false, false, false, false));
            }
        }, KeyDownEvent.getType());

        formGroupPanel.addDomHandler(
                event ->
                        handleKeyEvent(event.getNativeEvent()),
                KeyDownEvent.getType());
    }

    private void handleKeyEvent(final NativeEvent nativeEvent) {
        if (Action.HELP == KeyBinding.test(nativeEvent)) {
            nativeEvent.preventDefault();
        }
    }

    /// Sets the setting's tooltip, e.g. `Change Status`, which also makes its ⋮ a button named by it.
    ///
    /// @param title The tooltip, or null or blank for none (the ⋮ is then a hint only).
    @Override
    public void setTitle(final String title) {
        super.setTitle(title);
        final Element element = icon.getElement();
        if (title == null || title.isEmpty()) {
            element.removeAttribute("role");
            element.removeAttribute("tabindex");
            element.removeAttribute("aria-label");
            element.removeAttribute("aria-haspopup");
            element.setAttribute("aria-hidden", "true");
        } else {
            element.setAttribute("role", "button");
            element.setTabIndex(0);
            element.setAttribute("aria-label", title);
            element.setAttribute("aria-haspopup", "dialog");
            element.removeAttribute("aria-hidden");
        }
    }

    private boolean isButton() {
        return "button".equals(icon.getElement().getAttribute("role"));
    }

    /// Makes the setting read only: its value is greyed and its chooser doesn't open (the controller
    /// refuses the change too), and its ellipsis hint is hidden.
    ///
    /// @param readOnly Whether the setting is read only.
    public void setReadOnly(final boolean readOnly) {
        formGroupPanel.setStyleName("setting-block--read-only", readOnly);
    }

    @Override
    public void add(final Widget widget) {
        // Not a HelpHTML so must be the childWidget
        if (childWidget != null) {
            throw new IllegalStateException("SettingBlock can only contain one child widget.");
        }
        this.childWidget = widget;
        widget.addStyleName("allow-focus");
        // After the hint
        formGroupPanel.add(widget);
    }

    @Override
    public void clear() {
        if (childWidget != null) {
            formGroupPanel.remove(childWidget);
            childWidget = null;
        }
    }

    @Override
    public Iterator<Widget> iterator() {
        return Collections.singleton(childWidget).iterator();
    }

    @Override
    public boolean remove(final Widget w) {
        if (childWidget == w) {
            clear();
            return true;
        } else {
            return false;
        }
    }

    @Override
    public HandlerRegistration addClickHandler(final ClickHandler clickHandler) {
        return formGroupPanel.addDomHandler(clickHandler, ClickEvent.getType());
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<FlowPanel, SettingBlock> {

    }
}
