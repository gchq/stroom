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

package stroom.gwt.workbench.client.widgets.buttons;

import stroom.widget.button.client.ToolbarButton;
import stroom.widget.button.client.ToolbarToggleButton;
import stroom.widget.component.client.Component;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

import java.util.function.Consumer;

/// Toolbars declared in a UiBinder template (`ToolbarTemplate.ui.xml`), as a component's template
/// declares them: the template lays out the buttons, and their behaviour is set here with
/// `setOnClick` and `setOnToggle`.
final class ToolbarTemplate extends Component {

    @UiField
    ToolbarButton save;
    @UiField
    ToolbarToggleButton filter;
    @UiField
    ToolbarButton first;

    ToolbarTemplate() {
        initWidget(GWT.<Binder>create(Binder.class).createAndBindUi(this));
    }

    /// @param onSave What to run when Save is clicked.
    void setOnSave(final Runnable onSave) {
        save.setOnClick(onSave);
    }

    /// @param onFilter What to run when Show Filters is toggled, with whether it is now on.
    void setOnFilter(final Consumer<Boolean> onFilter) {
        filter.setOnToggle(onFilter);
    }

    /// @param onFirst What to run when First is clicked.
    void setOnFirst(final Runnable onFirst) {
        first.setOnClick(onFirst);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, ToolbarTemplate> {

    }
}
