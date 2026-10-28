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

package stroom.pipeline.stepping.client.component;

import stroom.pipeline.shared.stepping.StepLocation;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.PopupType;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;

/// Shows the "Set Location" dialog ([StepLocationPicker]) for one use, e.g. for the stepping
/// screen's location link.
public class StepLocationPickerController extends Controller {

    private final StepLocationPicker view;

    /// Made by GIN for one use, with the dialog's content.
    ///
    /// @param eventBus The application's event bus.
    /// @param view     Its component.
    @Inject
    public StepLocationPickerController(final EventBus eventBus, final StepLocationPicker view) {
        super(eventBus);
        this.view = view;
        initWidget(view);
    }

    /// Shows the dialog, owned by `opener` until it is closed. OK gives the location entered to
    /// `onOk`.
    ///
    /// @param opener       What shows it.
    /// @param stepLocation The location to start from.
    /// @param onOk         Given the location entered.
    public void edit(final HasControllerNode opener,
                     final StepLocation stepLocation,
                     final Consumer<StepLocation> onOk) {
        view.setStepLocation(stepLocation);
        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .modal()
                .caption("Set Location")
                .onShow(e -> view.focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        onOk.accept(view.getStepLocation());
                    }
                    e.hide();
                })
                .fire();
    }
}
