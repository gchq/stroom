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

package stroom.node.client.component;

import stroom.alert.client.event.AlertEvent;
import stroom.dispatch.client.RestErrorHandler;
import stroom.document.client.component.NameEntry;
import stroom.node.client.NodeGroupClient;
import stroom.node.shared.NodeGroup;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PathSafeNames;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.DialogAction;
import stroom.widget.popup.client.PopupType;
import stroom.widget.popup.client.event.HidePopupRequestEvent;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;

/// The New node group dialog ([NameEntry]), made for one use: creates a node group with the name
/// given, if no other group has it.
public class NewNodeGroupController extends Controller {

    private final NameEntry view;
    private final NodeGroupClient nodeGroupClient;

    /// Made by GIN, with its name field.
    ///
    /// @param eventBus        The application's event bus.
    /// @param view            The name field.
    /// @param nodeGroupClient Checks the name and creates the group.
    @Inject
    public NewNodeGroupController(final EventBus eventBus,
                                  final NameEntry view,
                                  final NodeGroupClient nodeGroupClient) {
        super(eventBus);
        this.view = view;
        this.nodeGroupClient = nodeGroupClient;
        initWidget(view);
        view.setOnEnter(() -> ControllerDialog.pressAction(this, DialogAction.OK));
    }

    /// Shows the dialog, owned by `opener` until it is closed.
    ///
    /// @param opener    What shows it.
    /// @param name      The name to start with.
    /// @param onCreated Given the group once it is created.
    public void show(final HasControllerNode opener,
                     final String name,
                     final Consumer<NodeGroup> onCreated) {
        view.setName(name);
        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .caption("New")
                .onShow(e -> view.focus())
                .onHideRequest(e -> onHideRequest(e, onCreated))
                .fire();
    }

    private void onHideRequest(final HidePopupRequestEvent e, final Consumer<NodeGroup> onCreated) {
        if (e.isOk()) {
            final String name = NullSafe.trim(view.getName());
            if (name.isEmpty()) {
                AlertEvent.fireError(this, "You must provide a name", e::reset);
            } else if (PathSafeNames.validate(name) != null) {
                AlertEvent.fireError(this, PathSafeNames.validate(name), e::reset);
            } else {
                checkNameAndCreate(name, e, onCreated);
            }
        } else {
            e.hide();
        }
    }

    private void checkNameAndCreate(final String name,
                                    final HidePopupRequestEvent e,
                                    final Consumer<NodeGroup> onCreated) {
        // NodeGroupClient takes no owner: a name checked after the dialog is cancelled creates nothing
        nodeGroupClient.checkNodeGroupName(name, result -> {
            if (isDisposed()) {
                return;
            }
            if (result != null) {
                AlertEvent.fireError(this,
                        "Group name '" + name + "' is already in use by another group.",
                        e::reset);
            } else {
                nodeGroupClient.create(name, nodeGroup -> {
                    onCreated.accept(nodeGroup);
                    e.hide();
                }, RestErrorHandler.forPopup(this, e), taskMonitor());
            }
        }, RestErrorHandler.forPopup(this, e), taskMonitor());
    }

}
