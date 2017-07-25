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
import com.google.gwt.user.client.ui.DecoratedPopupPanel;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.SimplePanel;

public class ContextMenu extends DecoratedPopupPanel {
    private static final Binder binder = GWT.create(Binder.class);
    @UiField
    Style style;
    private FlowPanel layout;

    public ContextMenu() {
        super(true, false);
        layout = binder.createAndBindUi(this);
        setWidget(layout);
    }

    public void addItem(final ContextMenuItem item) {
        layout.add(item);
    }

    public void addSeparator() {
        final SimplePanel separator = new SimplePanel();
        separator.setStyleName(style.separator());
        layout.add(separator);
    }

    interface Binder extends UiBinder<FlowPanel, ContextMenu> {
    }

    interface Style extends CssResource {
        String separator();
    }
}
