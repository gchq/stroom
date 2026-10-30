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

package stroom.index.client.component;

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.entity.shared.ExpressionCriteria;
import stroom.index.shared.IndexVolume;
import stroom.index.shared.IndexVolumeFields;
import stroom.index.shared.IndexVolumeGroup;
import stroom.index.shared.IndexVolumeGroupResource;
import stroom.index.shared.IndexVolumeResource;
import stroom.node.client.NodeClient;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionUtil;
import stroom.svg.shared.SvgImage;
import stroom.util.client.DelayedUpdate;
import stroom.util.shared.PathSafeNames;
import stroom.widget.button.client.IsButton;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.PopupSize;
import stroom.widget.popup.client.PopupType;
import stroom.widget.popup.client.event.HidePopupRequestEvent;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/// Edits an index volume group ([IndexVolumeGroupEditor]) in a dialog made for one use: its name,
/// and its volumes, which are added, edited, deleted and rescanned from the list's toolbar.
public class IndexVolumeGroupEditorController extends Controller {

    private static final IndexVolumeResource INDEX_VOLUME_RESOURCE = GWT.create(IndexVolumeResource.class);
    private static final IndexVolumeGroupResource INDEX_VOLUME_GROUP_RESOURCE =
            GWT.create(IndexVolumeGroupResource.class);

    private final IndexVolumeGroupEditor view;
    private final IndexVolumeStatusListController indexVolumeStatusList;
    private final Provider<IndexVolumeEditorController> editProvider;
    private final RestFactory restFactory;
    private final NodeClient nodeClient;
    private final IsButton openButton;
    private final IsButton deleteButton;
    private final DelayedUpdate delayedUpdate;

    private IndexVolumeGroup volumeGroup;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus              The application's event bus.
    /// @param view                  The content.
    /// @param indexVolumeStatusList The group's volumes, attached to the content's list.
    /// @param editProvider          Makes the dialog that adds or edits a volume.
    /// @param restFactory           Fetches, deletes and rescans volumes, and saves the group.
    /// @param nodeClient            Lists the nodes, whose volumes are rescanned.
    @Inject
    public IndexVolumeGroupEditorController(final EventBus eventBus,
                                            final IndexVolumeGroupEditor view,
                                            final IndexVolumeStatusListController indexVolumeStatusList,
                                            final Provider<IndexVolumeEditorController> editProvider,
                                            final RestFactory restFactory,
                                            final NodeClient nodeClient) {
        super(eventBus);
        this.view = view;
        this.editProvider = editProvider;
        this.restFactory = restFactory;
        this.nodeClient = nodeClient;
        initWidget(view);
        this.indexVolumeStatusList = own(indexVolumeStatusList).attach(view.getList());

        indexVolumeStatusList.getToolbar().setName("Volume actions");
        indexVolumeStatusList.getToolbar().button(SvgImage.ADD, "New Index Volume", this::create);
        openButton = indexVolumeStatusList.getToolbar().button(SvgImage.EDIT,
                "Edit Selected Index Volume", this::edit);
        openButton.setEnabled(false);
        deleteButton = indexVolumeStatusList.getToolbar().button(SvgImage.DELETE,
                "Delete Selected Index Volumes", this::delete);
        deleteButton.setEnabled(false);
        indexVolumeStatusList.getToolbar().button(SvgImage.REFRESH, "Rescan Index Volumes", this::onRescan);
        delayedUpdate = new DelayedUpdate(indexVolumeStatusList::refresh);
        onDispose(delayedUpdate::reset);

        own(indexVolumeStatusList.getSelectionModel().addSelectionHandler(event -> {
            enableButtons();
            if (event.getSelectionType().isDoubleSelect()) {
                edit();
            }
        }));
    }

    /// Finds a group's volumes, then shows the group in a dialog, owned by `opener` until it is
    /// closed. OK saves its name.
    ///
    /// @param opener      What shows it.
    /// @param volumeGroup The group.
    /// @param title       The dialog's caption.
    /// @param consumer    Given the saved group on OK.
    public void show(final HasControllerNode opener,
                     final IndexVolumeGroup volumeGroup,
                     final String title,
                     final Consumer<IndexVolumeGroup> consumer) {
        final ExpressionOperator expression = ExpressionUtil.equalsId(IndexVolumeFields.GROUP_ID,
                volumeGroup.getId());
        final ExpressionCriteria expressionCriteria = new ExpressionCriteria(expression);
        // TODO: 09/09/2022 Need to implement user defined sorting
        expressionCriteria.setSort(IndexVolumeFields.NODE_NAME.getFldName());
        expressionCriteria.addSort(IndexVolumeFields.PATH.getFldName());

        // Not shown if the volumes can't be found, so disposed then
        indexVolumeStatusList.init(expressionCriteria,
                guard(volumes -> open(opener, volumeGroup, title, consumer)),
                this::dispose);
    }

    private void open(final HasControllerNode opener,
                      final IndexVolumeGroup volumeGroup,
                      final String title,
                      final Consumer<IndexVolumeGroup> consumer) {
        this.volumeGroup = volumeGroup;
        view.setName(volumeGroup.getName());

        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.resizable(1400, 600))
                .caption(title)
                .onShow(e -> view.focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        final IndexVolumeGroup updated = volumeGroup.copy().name(view.getName()).build();
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
        nodeClient.listAllNodes(guard(nodeNames ->
                        nodeNames.forEach(nodeName ->
                                restFactory
                                        .create(INDEX_VOLUME_RESOURCE)
                                        .method(res -> res.rescan(nodeName))
                                        .onSuccess(response -> delayedUpdate.update())
                                        .onFailure(throwable -> {
                                        })
                                        .owner(this)
                                        .taskMonitorFactory(taskMonitor())
                                        .exec())),
                throwable -> {
                },
                indexVolumeStatusList.getTaskListener());
    }

    private void create() {
        editVolume(IndexVolume.builder().indexVolumeGroupId(volumeGroup.getId()).build(), "New Index Volume");
    }

    private void edit() {
        final IndexVolume volume = indexVolumeStatusList.getSelectionModel().getSelected();
        if (volume != null) {
            restFactory
                    .create(INDEX_VOLUME_RESOURCE)
                    .method(res -> res.fetch(volume.getId()))
                    .onSuccess(result -> editVolume(result, "Edit Index Volume"))
                    .owner(this)
                    .taskMonitorFactory(indexVolumeStatusList.getTaskListener())
                    .exec();
        }
    }

    private void editVolume(final IndexVolume indexVolume, final String caption) {
        editProvider.get().show(this, indexVolume, caption, result -> {
            if (result != null) {
                indexVolumeStatusList.refresh();
            }
        }, indexVolumeStatusList.getTaskListener());
    }

    private void delete() {
        final List<IndexVolume> list = indexVolumeStatusList.getSelectionModel().getSelectedItems();
        if (list != null && !list.isEmpty()) {
            final String message = list.size() > 1
                    ? "Are you sure you want to delete the selected volumes?"
                    : "Are you sure you want to delete the selected volume?";
            ConfirmEvent.fire(this, message, result -> {
                if (result) {
                    indexVolumeStatusList.getSelectionModel().clear();
                    for (final IndexVolume volume : list) {
                        restFactory
                                .create(INDEX_VOLUME_RESOURCE)
                                .method(res -> res.delete(volume.getId()))
                                .onSuccess(response -> indexVolumeStatusList.refresh())
                                .owner(this)
                                .taskMonitorFactory(taskMonitor())
                                .exec();
                    }
                }
            });
        }
    }

    private void enableButtons() {
        final boolean enabled = indexVolumeStatusList.getSelectionModel().getSelected() != null;
        openButton.setEnabled(enabled);
        deleteButton.setEnabled(enabled);
    }

    private void doWithGroupNameValidation(final String groupName,
                                           final Integer groupId,
                                           final Runnable work,
                                           final HidePopupRequestEvent event) {
        if (groupName == null || groupName.isEmpty()) {
            AlertEvent.fireError(this, "You must provide a name for the index volume group.", event::reset);
        } else if (PathSafeNames.validate(groupName) != null) {
            AlertEvent.fireError(this, PathSafeNames.validate(groupName), event::reset);
        } else {
            restFactory
                    .create(INDEX_VOLUME_GROUP_RESOURCE)
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

    private void updateVolumeGroup(final Consumer<IndexVolumeGroup> consumer,
                                   final IndexVolumeGroup volumeGroup,
                                   final HidePopupRequestEvent event) {
        restFactory
                .create(INDEX_VOLUME_GROUP_RESOURCE)
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

}
