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

package stroom.annotation.client;

import stroom.svg.shared.SvgImage;
import stroom.widget.util.client.KeyBinding;
import stroom.widget.util.client.KeyBinding.Action;

import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.HasClickHandlers;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HasWidgets;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.Collections;
import java.util.Iterator;

public class SettingBlock extends Composite implements HasWidgets, HasClickHandlers {

    private final FlowPanel formGroupPanel = new FlowPanel();

    private final SimplePanel icon;
    private Widget childWidget = null;

    public SettingBlock() {
        formGroupPanel.addStyleName("setting-block icon-colour__grey");

        icon = new SimplePanel();
        icon.getElement().setInnerHTML(SvgImage.ELLIPSES_VERTICAL.getSvg());
        icon.getElement().setClassName("setting-block-icon icon-colour__grey svgIcon");

        formGroupPanel.add(icon);
        initWidget(formGroupPanel);

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

    @Override
    public void add(final Widget widget) {
        // Not a HelpHTML so must be the childWidget
        if (childWidget != null) {
            throw new IllegalStateException("FormGroup can only contain one child widget.");
        }
        this.childWidget = widget;
        widget.addStyleName("allow-focus");
        updateFormGroupPanel();
    }

    @Override
    public void clear() {
        childWidget = null;
        updateFormGroupPanel();
    }

    private void updateFormGroupPanel() {
        formGroupPanel.clear();
        formGroupPanel.add(icon);
        if (childWidget != null) {
            formGroupPanel.add(childWidget);
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
}
