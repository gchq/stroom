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

package stroom.index.client.component;

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.content.client.component.ContentTabController;
import stroom.dispatch.client.RestFactory;
import stroom.index.shared.IndexVolumeGroup;
import stroom.index.shared.IndexVolumeGroupResource;
import stroom.svg.client.IconColour;
import stroom.svg.shared.SvgImage;
import stroom.util.client.CountDownAndRun;
import stroom.widget.button.client.IsButton;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.List;
import java.util.Objects;

/// Shows the index volume groups, and adds, edits and deletes them.
public class IndexVolumeGroupsController extends ContentTabController {

    public static final String TAB_TYPE = "IndexVolumes";
    private static final IndexVolumeGroupResource INDEX_VOLUME_GROUP_RESOURCE =
            GWT.create(IndexVolumeGroupResource.class);

    private final IndexVolumeGroupListController indexVolumeGroupList;
    private final Provider<IndexVolumeGroupEditorController> editProvider;
    private final RestFactory restFactory;
    private final Provider<NewIndexVolumeGroupController> newIndexVolumeGroupControllerProvider;

    private final IsButton newButton;
    private final IsButton openButton;
    private final IsButton deleteButton;

    /// Made by GIN, with its parts.
    ///
    /// @param eventBus                              The application's event bus.
    /// @param indexVolumeGroupList                  The index volume group list.
    /// @param editProvider                          Makes the edit.
    /// @param restFactory                           Makes REST requests.
    /// @param newIndexVolumeGroupControllerProvider Makes the new index volume group controller.
    @Inject
    public IndexVolumeGroupsController(
            final EventBus eventBus,
            final IndexVolumeGroupListController indexVolumeGroupList,
            final Provider<IndexVolumeGroupEditorController> editProvider,
            final RestFactory restFactory,
            final Provider<NewIndexVolumeGroupController> newIndexVolumeGroupControllerProvider) {

        super(eventBus);
        own(indexVolumeGroupList);
        this.indexVolumeGroupList = indexVolumeGroupList;
        this.editProvider = editProvider;
        this.restFactory = restFactory;
        this.newIndexVolumeGroupControllerProvider = newIndexVolumeGroupControllerProvider;

        indexVolumeGroupList.getToolbar().setName("Volume group actions");
        newButton = indexVolumeGroupList.getToolbar().button(SvgImage.ADD,
                "New Index Volume Group", this::add);
        openButton = indexVolumeGroupList.getToolbar().button(SvgImage.EDIT,
                "Edit Selected Index Volume Group",
                "Edit", this::edit);
        openButton.setEnabled(false);
        deleteButton = indexVolumeGroupList.getToolbar().button(SvgImage.DELETE,
                "Delete Selected Index Volume Group",
                "Delete", this::delete);
        deleteButton.setEnabled(false);

        initWidget(indexVolumeGroupList.asWidget());

        own(indexVolumeGroupList.getSelectionModel().addSelectionHandler(event -> {
            enableButtons();
            if (event.getSelectionType().isDoubleSelect()) {
                edit();
            }
        }));
    }

    private void add() {
        newIndexVolumeGroupControllerProvider.get().show(this, "", indexVolumeGroup -> {
            // The new group, if the page shows it (UI-GUIDE.md, "What a list selects")
            indexVolumeGroupList.getSelectionModel().selectWhenShown(row ->
                    Objects.equals(row.getId(), indexVolumeGroup.getId()));
            edit(indexVolumeGroup);
            refresh();
        });
    }

    private void edit() {
        final IndexVolumeGroup volume = indexVolumeGroupList.getSelectionModel().getSelected();
        if (volume != null) {
            restFactory
                    .create(INDEX_VOLUME_GROUP_RESOURCE)
                    .method(res -> res.fetch(volume.getId()))
                    .onSuccess(this::edit)
                    .taskMonitorFactory(this)
                    .exec();
        }
    }

    private void edit(final IndexVolumeGroup indexVolumeGroup) {
        editProvider.get().show(this,
                indexVolumeGroup,
                "Edit Index Volume Group - " + indexVolumeGroup.getName(),
                result -> refresh());
    }

    private void delete() {
        final List<IndexVolumeGroup> list = indexVolumeGroupList.getSelectionModel().getSelectedItems();
        if (list != null && list.size() > 0) {
            String message = "Are you sure you want to delete the selected volume group?";
            if (list.size() > 1) {
                message = "Are you sure you want to delete the selected volume groups?";
            }
            ConfirmEvent.fire(IndexVolumeGroupsController.this, message,
                    result -> {
                        if (result) {
                            // Once all are answered, the group in place of the first
                            final CountDownAndRun countdown = new CountDownAndRun(list.size(), () -> {
                                indexVolumeGroupList.getSelectionModel().selectNextAfterRemove(list);
                                refresh();
                            });
                            for (final IndexVolumeGroup volume : list) {
                                restFactory
                                        .create(INDEX_VOLUME_GROUP_RESOURCE)
                                        .method(res -> res.delete(volume.getId()))
                                        .onSuccess(response -> countdown.countdown())
                                        .onFailure(error -> {
                                            AlertEvent.fireError(this, error.getMessage(), null);
                                            countdown.countdown();
                                        })
                                        .taskMonitorFactory(this)
                                        .exec();
                            }
                        }
                    });
        }
    }

    private void enableButtons() {
        // Open edits one group; Delete deletes all selected
        final int count = indexVolumeGroupList.getSelectionModel().getSelectedCount();
        openButton.setEnabled(count == 1);
        deleteButton.setEnabled(count > 0);
    }

    /// Fetches the volume groups again.
    public void refresh() {
        indexVolumeGroupList.refresh();
    }

    @Override
    public SvgImage getIcon() {
        return SvgImage.VOLUMES;
    }

    @Override
    public IconColour getIconColour() {
        return IconColour.GREY;
    }

    @Override
    public String getLabel() {
        return "Index Volumes";
    }

    @Override
    public String getType() {
        return TAB_TYPE;
    }
}
