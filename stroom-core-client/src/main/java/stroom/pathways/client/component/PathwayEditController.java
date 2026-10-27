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

import stroom.pathways.shared.PathwaysDoc;
import stroom.pathways.shared.otel.trace.NanoTime;
import stroom.pathways.shared.pathway.PathNode;
import stroom.pathways.shared.pathway.Pathway;
import stroom.widget.component.client.Contexts;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import javax.validation.ValidationException;

/// A pathway's name, paths and constraints ([PathwayEdit]), in a dialog its owner keeps and shows
/// again. Choosing a path shows its constraints; the owner saves the pathway on OK.
public class PathwayEditController extends Controller {

    private final PathwayEdit view;
    private final PathwayTreeController pathwayTree;
    private final ConstraintListController constraintList;
    private Pathway pathway;
    private boolean readOnly = true;

    /// Made by GIN, with its content and its parts.
    ///
    /// @param eventBus       The application's event bus.
    /// @param view           The content.
    /// @param pathwayTree    The pathway's paths.
    /// @param constraintList The chosen path's constraints.
    @Inject
    public PathwayEditController(final EventBus eventBus,
                                 final PathwayEdit view,
                                 final PathwayTreeController pathwayTree,
                                 final ConstraintListController constraintList) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        this.pathwayTree = own(pathwayTree);
        this.constraintList = own(constraintList);
        view.setTree(pathwayTree);
        view.setConstraints(constraintList);

        own(pathwayTree.getSelectionModel().addSelectionChangeHandler(e -> {
            final PathNode selected = pathwayTree.getSelectionModel().getSelectedObject();
            constraintList.setData(selected, readOnly);
        }));
    }

    /// Shows a pathway, with no path chosen.
    ///
    /// @param pathwaysDoc The pathway's document.
    /// @param pathway     The pathway.
    public void read(final PathwaysDoc pathwaysDoc, final Pathway pathway) {
        // Read only as its document is
        this.readOnly = context(Contexts.READ_ONLY);
        this.pathway = pathway;
        pathwayTree.read(pathwaysDoc, pathway);
        constraintList.setData(null, readOnly);
        view.setName(pathway.getName());
    }

    /// @return The pathway with the name typed, updated now.
    /// @throws ValidationException If no name is typed.
    public Pathway write() {
        final String name = view.getName().trim();
        if (name.isEmpty()) {
            throw new ValidationException("A pathway must have a name");
        }

        final NanoTime now = NanoTime.ofMillis(System.currentTimeMillis());
        return pathway.copy().name(name).updateTime(now).build();
    }

    /// Shows the dialog.
    ///
    /// @param caption The dialog's caption.
    /// @param handler Told when OK or Cancel is pressed; it hides the dialog.
    public void show(final String caption, final HidePopupRequestEvent.Handler handler) {
        ControllerDialog.show(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.resizable(1024, 800))
                .caption(caption)
                .onShow(e -> view.focus())
                .onHideRequest(handler)
                .fire();
    }
}
