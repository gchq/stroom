/*
 * Copyright 2022 Crown Copyright
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

import stroom.dashboard.shared.LayoutConstraints;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.PopupType;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;

/// The Set Layout Constraints dialog ([LayoutConstraint]), made for one use: each change applies
/// at once, and Close closes it.
public class LayoutConstraintController extends Controller {

    private final LayoutConstraint view;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus The application's event bus.
    /// @param view     The content.
    @Inject
    public LayoutConstraintController(final EventBus eventBus, final LayoutConstraint view) {
        super(eventBus);
        this.view = view;
        initWidget(view);
    }

    /// Shows a dashboard's layout constraints in a dialog, owned by `opener` until it is closed.
    ///
    /// @param opener      What shows it.
    /// @param constraints The constraints, or null for the defaults shown.
    /// @param onChange    Given the constraints each time the user changes one.
    public void edit(final HasControllerNode opener,
                     final LayoutConstraints constraints,
                     final Consumer<LayoutConstraints> onChange) {
        view.setConstraints(constraints);
        view.setOnChange(onChange);
        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.CLOSE_DIALOG)
                .caption("Set Layout Constraints")
                .onShow(e -> view.focus())
                .fire();
    }
}
