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

package stroom.dashboard.client.component;

import stroom.widget.component.client.Component;
import stroom.widget.text.client.Label;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.Widget;

/// One column of a Table Filter: its name above its content, e.g. its conditional formatting rules.
public class TableFilterColumn extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    FlowPanel panel;
    @UiField
    Label label;

    /// Makes a column with no name or content.
    public TableFilterColumn() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// @param name The column's name.
    public void setName(final String name) {
        label.setText(name);
    }

    /// @param content What is shown below the name.
    public void setContent(final IsWidget content) {
        panel.add(content);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, TableFilterColumn> {

    }
}
