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

package stroom.view.client.component;

import stroom.widget.component.client.Component;
import stroom.widget.dropdowntree.client.DropDownBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

/// A view's Settings tab: its data source and pipeline pickers (attached to by its controller), and
/// the filter on the streams analytic rules process (shown here by its controller).
public class ViewSettings extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    DropDownBox dataSource;
    @UiField
    DropDownBox pipeline;
    @UiField
    SimplePanel expression;

    /// Makes the tab, with nothing chosen.
    public ViewSettings() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// @return The data source picker.
    public DropDownBox getDataSourceBox() {
        return dataSource;
    }

    /// @return The pipeline picker.
    public DropDownBox getPipelineBox() {
        return pipeline;
    }

    /// @param expression The filter on the streams analytic rules process.
    public void setExpression(final IsWidget expression) {
        this.expression.setWidget(expression);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, ViewSettings> {

    }
}
