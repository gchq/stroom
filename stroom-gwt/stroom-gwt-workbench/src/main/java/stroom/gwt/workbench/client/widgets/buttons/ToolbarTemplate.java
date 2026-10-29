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

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.Widget;

import java.util.function.Consumer;

/// Toolbars declared in a UiBinder template (`ToolbarTemplate.ui.xml`), as a view declares them:
/// the template lays out the buttons, and their behaviour is set here with `setOnClick` and
/// `setOnToggle`.
final class ToolbarTemplate extends Composite {

    @UiField
    ToolbarButton save;
    @UiField
    ToolbarToggleButton filter;
    @UiField
    ToolbarButton first;

    ToolbarTemplate(final Runnable onSave, final Consumer<Boolean> onFilter, final Runnable onFirst) {
        initWidget(GWT.<Binder>create(Binder.class).createAndBindUi(this));
        save.setOnClick(onSave);
        filter.setOnToggle(onFilter);
        first.setOnClick(onFirst);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, ToolbarTemplate> {

    }
}
