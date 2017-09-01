/*
 * Copyright 2016 Crown Copyright
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

package stroom.widget.contextmenu.client.view;

import com.google.gwt.core.client.GWT;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;

public class ContextMenuItem extends Composite {
    private static final Binder binder = GWT.create(Binder.class);
    private final ContextMenu contextMenu;
    private final Command command;
    @UiField
    Label text;

    @UiField
    Style style;

    private FlowPanel layout;
    public ContextMenuItem(final ContextMenu contextMenu, final String text, final Command command) {
        this.contextMenu = contextMenu;
        this.command = command;
        sinkEvents(Event.MOUSEEVENTS);

        layout = binder.createAndBindUi(this);
        initWidget(layout);

        setText(text);
    }

    public String getText() {
        return text.getText();
    }

    public void setText(final String t) {
        text.setText(t);
    }

    @Override
    public void onBrowserEvent(Event event) {
        switch (DOM.eventGetType(event)) {
            case Event.ONMOUSEOVER:
                layout.addStyleName(style.selected());
                break;
            case Event.ONMOUSEOUT:
                layout.removeStyleName(style.selected());
                break;
            case Event.ONMOUSEDOWN:
                command.execute();
                contextMenu.hide();
                layout.removeStyleName(style.selected());
                break;
        }

        super.onBrowserEvent(event);
    }

    interface Binder extends UiBinder<FlowPanel, ContextMenuItem> {
    }

    interface Style extends CssResource {
        String selected();
    }
}
