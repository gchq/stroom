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

package stroom.data.store.impl.fs.client.component;

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.data.store.impl.fs.shared.FsVolume;
import stroom.data.store.impl.fs.shared.FsVolumeGroup;
import stroom.data.store.impl.fs.shared.FsVolumeGroupResource;
import stroom.data.store.impl.fs.shared.FsVolumeResource;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.svg.shared.SvgImage;
import stroom.task.client.DefaultTaskMonitorFactory;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.client.DelayedUpdate;
import stroom.util.shared.PathSafeNames;
import stroom.widget.button.client.ButtonView;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/// Edits a data volume group ([FsVolumeGroupEdit]) in a dialog made for one use: its name, and
/// its volumes, which are added, edited, deleted and rescanned from the list's toolbar.
public class FsVolumeGroupEditController extends Controller {

    private static final FsVolumeResource FS_VOLUME_RESOURCE = GWT.create(FsVolumeResource.class);
    private static final FsVolumeGroupResource FS_VOLUME_GROUP_RESOURCE =
            GWT.create(FsVolumeGroupResource.class);

    private final FsVolumeGroupEdit view;
    private final FsVolumeStatusListController fsVolumeStatusList;
    private final Provider<FsVolumeEditController> editProvider;
    private final RestFactory restFactory;
    private final ButtonView openButton;
    private final ButtonView deleteButton;
    private final DelayedUpdate delayedUpdate;

    private FsVolumeGroup volumeGroup;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus           The application's event bus.
    /// @param view               The content.
    /// @param fsVolumeStatusList The group's volumes.
    /// @param editProvider       Makes the dialog that adds or edits a volume.
    /// @param restFactory        Fetches, deletes and rescans volumes, and saves the group.
    @Inject
    public FsVolumeGroupEditController(final EventBus eventBus,
                                       final FsVolumeGroupEdit view,
                                       final FsVolumeStatusListController fsVolumeStatusList,
                                       final Provider<FsVolumeEditController> editProvider,
                                       final RestFactory restFactory) {
        super(eventBus);
        this.view = view;
        this.fsVolumeStatusList = own(fsVolumeStatusList);
        this.editProvider = editProvider;
        this.restFactory = restFactory;
        initWidget(view);

        fsVolumeStatusList.getToolbar().setName("Volume actions");
        fsVolumeStatusList.getToolbar().button(SvgImage.ADD, "New Data Volume", this::create);
        openButton = fsVolumeStatusList.getToolbar().button(SvgImage.EDIT,
                "Edit Selected Data Volume", this::edit);
        openButton.setEnabled(false);
        deleteButton = fsVolumeStatusList.getToolbar().button(SvgImage.DELETE,
                "Delete Selected Data Volumes", this::delete);
        deleteButton.setEnabled(false);
        fsVolumeStatusList.getToolbar().button(SvgImage.REFRESH, "Rescan Data Volumes", this::onRescan);

        view.setList(fsVolumeStatusList);
        delayedUpdate = new DelayedUpdate(fsVolumeStatusList::refresh);
        onDispose(delayedUpdate::reset);

        own(fsVolumeStatusList.getSelectionModel().addSelectionHandler(event -> {
            enableButtons();
            if (event.getSelectionType().isDoubleSelect()) {
                edit();
            }
        }));
    }

    /// Shows a group in a dialog, owned by `opener` until it is closed. OK saves its name.
    ///
    /// @param opener      What shows it.
    /// @param volumeGroup The group.
    /// @param title       The dialog's caption.
    /// @param consumer    Given the saved group on OK.
    public void show(final HasControllerNode opener,
                     final FsVolumeGroup volumeGroup,
                     final String title,
                     final Consumer<FsVolumeGroup> consumer) {
        this.volumeGroup = volumeGroup;
        fsVolumeStatusList.setGroup(volumeGroup);
        view.setName(volumeGroup.getName());

        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.resizable(1400, 600))
                .caption(title)
                .onShow(e -> view.focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        final FsVolumeGroup updated = volumeGroup.copy().name(view.getName()).build();
                        try {
                            doWithGroupNameValidation(view.getName(), updated.getId(), () ->
                                    updateVolumeGroup(consumer, updated, e), e);
                        } catch (final RuntimeException ex) {
                            AlertEvent.fireError(this, ex.getMessage(), e::reset);
                        }
                    } else {
                        e.hide();
                    }
                })
                .fire();
    }

    private void onRescan() {
        delayedUpdate.reset();
        restFactory
                .create(FS_VOLUME_RESOURCE)
                .method(FsVolumeResource::rescan)
                .onSuccess(response -> delayedUpdate.update())
                .onFailure(throwable -> {
                })
                .owner(this)
                .taskMonitorFactory(taskMonitor())
                .exec();
    }

    private void create() {
        editVolume(FsVolume.builder().volumeGroup(volumeGroup).build(), "New Data Volume");
    }

    private void edit() {
        final FsVolume volume = fsVolumeStatusList.getSelectionModel().getSelected();
        if (volume != null) {
            restFactory
                    .create(FS_VOLUME_RESOURCE)
                    .method(res -> res.fetch(volume.getId()))
                    .onSuccess(result -> editVolume(result, "Edit Data Volume"))
                    .owner(this)
                    .taskMonitorFactory(taskMonitor())
                    .exec();
        }
    }

    private void editVolume(final FsVolume fsVolume, final String caption) {
        editProvider.get().show(this, fsVolume, caption, result -> {
            if (result != null) {
                fsVolumeStatusList.refresh();
            }
        });
    }

    private void delete() {
        final List<FsVolume> list = fsVolumeStatusList.getSelectionModel().getSelectedItems();
        if (list != null && !list.isEmpty()) {
            final String message = list.size() > 1
                    ? "Are you sure you want to delete the selected volumes?"
                    : "Are you sure you want to delete the selected volume?";
            ConfirmEvent.fire(this, message, result -> {
                if (result) {
                    fsVolumeStatusList.getSelectionModel().clear();
                    for (final FsVolume volume : list) {
                        restFactory
                                .create(FS_VOLUME_RESOURCE)
                                .method(res -> res.delete(volume.getId()))
                                .onSuccess(response -> fsVolumeStatusList.refresh())
                                .owner(this)
                                .taskMonitorFactory(taskMonitor())
                                .exec();
                    }
                }
            });
        }
    }

    private void enableButtons() {
        final boolean enabled = fsVolumeStatusList.getSelectionModel().getSelected() != null;
        openButton.setEnabled(enabled);
        deleteButton.setEnabled(enabled);
    }

    private void doWithGroupNameValidation(final String groupName,
                                           final Integer groupId,
                                           final Runnable work,
                                           final HidePopupRequestEvent event) {
        if (groupName == null || groupName.isEmpty()) {
            AlertEvent.fireError(this, "You must provide a name for the data volume group.", event::reset);
        } else if (PathSafeNames.validate(groupName) != null) {
            AlertEvent.fireError(this, PathSafeNames.validate(groupName), event::reset);
        } else {
            restFactory
                    .create(FS_VOLUME_GROUP_RESOURCE)
                    .method(res -> res.fetchByName(groupName))
                    .onSuccess(grp -> {
                        if (grp != null && !Objects.equals(groupId, grp.getId())) {
                            AlertEvent.fireError(this,
                                    "Group name '" + groupName + "' is already in use by another group.",
                                    event::reset);
                        } else {
                            work.run();
                        }
                    })
                    .onFailure(RestErrorHandler.forPopup(this, event))
                    .owner(this)
                    .taskMonitorFactory(taskMonitor())
                    .exec();
        }
    }

    private void updateVolumeGroup(final Consumer<FsVolumeGroup> consumer,
                                   final FsVolumeGroup volumeGroup,
                                   final HidePopupRequestEvent event) {
        restFactory
                .create(FS_VOLUME_GROUP_RESOURCE)
                .method(res -> res.update(volumeGroup.getId(), volumeGroup))
                .onSuccess(r -> {
                    consumer.accept(r);
                    event.hide();
                })
                .onFailure(RestErrorHandler.forPopup(this, event))
                .owner(this)
                .taskMonitorFactory(taskMonitor())
                .exec();
    }

    private TaskMonitorFactory taskMonitor() {
        final TaskMonitorFactory provided = getProvidedTaskMonitor();
        return provided != null ? provided : new DefaultTaskMonitorFactory(this);
    }
}
