/*
 * Copyright 2025 Crown Copyright
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

package stroom.pathways.client.component;

import stroom.alert.client.event.AlertEvent;
import stroom.pathways.shared.pathway.Constraint;
import stroom.pathways.shared.pathway.ConstraintValueType;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.PopupSize;
import stroom.widget.popup.client.PopupType;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;

/// The New and Edit Constraint dialog ([ConstraintEditor]) of a pathway's node, made for one
/// use.
public class ConstraintEditorController extends Controller {

    private final ConstraintEditor view;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus The application's event bus.
    /// @param view     The content.
    @Inject
    public ConstraintEditorController(final EventBus eventBus, final ConstraintEditor view) {
        super(eventBus);
        this.view = view;
        initWidget(view);
    }

    /// Edits a constraint in a dialog, owned by `opener` until it is closed. On OK the
    /// constraint entered is given to `onOk`; one with no name, or a value that can't be read,
    /// is refused with an alert and the dialog stays open.
    ///
    /// @param opener     What shows it.
    /// @param caption    The dialog's caption, e.g. "New Constraint".
    /// @param constraint The constraint to start from; its value may be null (any value).
    /// @param onOk       Given the constraint entered.
    public void edit(final HasControllerNode opener,
                     final String caption,
                     final Constraint constraint,
                     final Consumer<Constraint> onOk) {
        read(constraint);
        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.resizable(800, 400))
                .caption(caption)
                .onShow(e -> view.focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        final Constraint updated;
                        try {
                            updated = write(constraint);
                        } catch (final RuntimeException ex) {
                            AlertEvent.fireErrorFromException(this, ex, e::reset);
                            return;
                        }
                        onOk.accept(updated);
                    }
                    e.hide();
                })
                .fire();
    }

    private void read(final Constraint constraint) {
        view.setName(constraint.getName());
        view.setType(ConstraintValueText.typeOf(constraint.getValue()));
        view.setValue(ConstraintValueText.toText(constraint.getValue()));
        view.setOptional(constraint.isOptional());
    }

    private Constraint write(final Constraint constraint) {
        final String name = view.getName().trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("A constraint must have a name");
        }
        final ConstraintValueType type = view.getType();
        return constraint.copy()
                .name(name)
                .value(ConstraintValueText.fromText(type, view.getValue()))
                .optional(view.isOptional())
                .build();
    }
}
