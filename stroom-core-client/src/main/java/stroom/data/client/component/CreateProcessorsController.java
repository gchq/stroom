/*
 * Copyright 2020 Crown Copyright
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

package stroom.data.client.component;

import stroom.data.client.ProcessChoice;
import stroom.util.shared.Selection;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.datepicker.client.DateTimeBoxController;
import stroom.widget.popup.client.PopupType;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;

/// The Create Processors dialog ([CreateProcessors]) of the data browser, made for one use.
public class CreateProcessorsController extends Controller {

    private final CreateProcessors view;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus                      The application's event bus.
    /// @param view                          The content.
    /// @param dateTimeBoxControllerProvider Makes the create time boxes' controllers.
    @Inject
    public CreateProcessorsController(final EventBus eventBus,
                                      final CreateProcessors view,
                                      final Provider<DateTimeBoxController> dateTimeBoxControllerProvider) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        make(dateTimeBoxControllerProvider).attach(view.getMinMetaCreateTimeBox());
        make(dateTimeBoxControllerProvider).attach(view.getMaxMetaCreateTimeBox());
        view.setOnReprocessChange(() -> view.setMaxMetaCreateTimeMs(System.currentTimeMillis()));
    }

    /// Shows the dialog, owned by `opener` until it is closed, and gives the choice made to
    /// `onChoice` on OK.
    ///
    /// @param opener    What shows it.
    /// @param selection The streams chosen; when they are particular streams, not all of them,
    ///                  the latest create time starts at now.
    /// @param onChoice  Given the choice made.
    public void show(final HasControllerNode opener,
                     final Selection<Long> selection,
                     final Consumer<ProcessChoice> onChoice) {
        if (!selection.isMatchAll() && selection.size() > 0) {
            view.setMaxMetaCreateTimeMs(System.currentTimeMillis());
        }
        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .modal()
                .caption("Create Processors")
                .onShow(e -> view.focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        onChoice.accept(new ProcessChoice(
                                view.getPriority(),
                                view.isAutoPriority(),
                                view.isReprocess(),
                                view.isEnabled(),
                                view.getMinMetaCreateTimeMs(),
                                view.getMaxMetaCreateTimeMs()));
                    }
                    e.hide();
                })
                .fire();
    }
}
