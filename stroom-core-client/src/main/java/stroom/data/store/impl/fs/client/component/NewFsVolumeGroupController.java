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

package stroom.data.store.impl.fs.client.component;

import stroom.alert.client.event.AlertEvent;
import stroom.data.store.impl.fs.shared.FsVolumeGroup;
import stroom.data.store.impl.fs.shared.FsVolumeGroupResource;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.entity.client.component.NameEntry;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PathSafeNames;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.popup.client.view.DialogAction;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;

/// The New Data Volume Group dialog ([NameEntry]), made for one use: creates a volume group
/// with the name given, if no other group has it.
public class NewFsVolumeGroupController extends Controller {

    private static final FsVolumeGroupResource FS_VOLUME_GROUP_RESOURCE =
            GWT.create(FsVolumeGroupResource.class);

    private final NameEntry view;
    private final RestFactory restFactory;

    /// Made by GIN, with its name field.
    ///
    /// @param eventBus    The application's event bus.
    /// @param view        The name field.
    /// @param restFactory Checks the name and creates the group.
    @Inject
    public NewFsVolumeGroupController(final EventBus eventBus,
                                      final NameEntry view,
                                      final RestFactory restFactory) {
        super(eventBus);
        this.view = view;
        this.restFactory = restFactory;
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
                     final Consumer<FsVolumeGroup> onCreated) {
        view.setName(name);
        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .caption("New Data Volume Group")
                .onShow(e -> view.focus())
                .onHideRequest(e -> onHideRequest(e, onCreated))
                .fire();
    }

    private void onHideRequest(final HidePopupRequestEvent e, final Consumer<FsVolumeGroup> onCreated) {
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
                                    final Consumer<FsVolumeGroup> onCreated) {
        restFactory
                .create(FS_VOLUME_GROUP_RESOURCE)
                .method(res -> res.fetchByName(name))
                .onSuccess(result -> {
                    if (result != null) {
                        AlertEvent.fireError(this,
                                "Group name '" + name + "' is already in use by another group.",
                                e::reset);
                    } else {
                        create(name, e, onCreated);
                    }
                })
                .onFailure(RestErrorHandler.forPopup(this, e))
                .owner(this)
                .taskMonitorFactory(taskMonitor())
                .exec();
    }

    private void create(final String name,
                        final HidePopupRequestEvent e,
                        final Consumer<FsVolumeGroup> onCreated) {
        restFactory
                .create(FS_VOLUME_GROUP_RESOURCE)
                .method(res -> res.create(name))
                .onSuccess(group -> {
                    onCreated.accept(group);
                    e.hide();
                })
                .onFailure(RestErrorHandler.forPopup(this, e))
                .owner(this)
                .taskMonitorFactory(taskMonitor())
                .exec();
    }

}
