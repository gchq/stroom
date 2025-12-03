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

package stroom.menubar.client.view;

import stroom.widget.menu.client.presenter.MenuItemPresenter.MenuItemView;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.MouseOutEvent;
import com.google.gwt.event.dom.client.MouseOutHandler;
import com.google.gwt.event.dom.client.MouseOverEvent;
import com.google.gwt.event.dom.client.MouseOverHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewImpl;

public class MenubarItem extends ViewImpl implements MenuItemView {

    private final Widget widget;
    @UiField
    SimplePanel background;
    @UiField
    Label text;
    private boolean enabled;

    @Inject
    public MenubarItem(final Binder binder) {
        widget = binder.createAndBindUi(this);
        widget.sinkEvents(Event.MOUSEEVENTS);
        setEnabled(enabled);
        widget.addDomHandler(event -> background.getElement().getStyle().setOpacity(0.3), MouseOverEvent.getType());
        widget.addDomHandler(event -> background.getElement().getStyle().setOpacity(0), MouseOutEvent.getType());
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setEnabledImage(final String resource) {
        // Not relevant
    }

    @Override
    public void setDisabledImage(final String resource) {
        // Not relevant
    }

    @Override
    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public void setHTML(final String html) {
        this.text.setText(html);
    }

    @Override
    public void setShortcut(final String shortcut) {
        // Not relevant
    }

    @Override
    public HandlerRegistration addMouseOverHandler(final MouseOverHandler handler) {
        return widget.addDomHandler(handler, MouseOverEvent.getType());
    }

    @Override
    public HandlerRegistration addMouseOutHandler(final MouseOutHandler handler) {
        return widget.addDomHandler(handler, MouseOutEvent.getType());
    }

    @Override
    public HandlerRegistration addClickHandler(final ClickHandler handler) {
        return widget.addDomHandler(handler, ClickEvent.getType());
    }

    @Override
    public void fireEvent(final GwtEvent<?> event) {
        widget.fireEvent(event);
    }

    public interface Binder extends UiBinder<Widget, MenubarItem> {

    }
}
