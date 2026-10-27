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

package stroom.data.store.impl.fs.client.presenter;

import stroom.alert.client.event.ConfirmEvent;
import stroom.content.client.presenter.ContentTabPresenter;
import stroom.data.store.impl.fs.client.component.FsVolumeGroupEditController;
import stroom.data.store.impl.fs.client.component.FsVolumeGroupListController;
import stroom.data.store.impl.fs.client.component.NewFsVolumeGroupController;
import stroom.data.store.impl.fs.shared.FsVolumeGroup;
import stroom.data.store.impl.fs.shared.FsVolumeGroupResource;
import stroom.data.store.impl.fs.shared.FsVolumeGroupRow;
import stroom.dispatch.client.RestFactory;
import stroom.svg.client.IconColour;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.NullSafe;
import stroom.widget.button.client.ButtonView;
import stroom.widget.datagrid.client.WrapperView;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.List;

public class FsVolumeGroupPresenter extends ContentTabPresenter<WrapperView> {

    public static final String TAB_TYPE = "DataVolumes";
    private static final FsVolumeGroupResource FS_VOLUME_GROUP_RESOURCE =
            GWT.create(FsVolumeGroupResource.class);

    private final FsVolumeGroupListController fsVolumeGroupList;
    private final Provider<FsVolumeGroupEditController> editProvider;
    private final RestFactory restFactory;
    private final Provider<NewFsVolumeGroupController> newFsVolumeGroupControllerProvider;

    private final ButtonView newButton;
    private final ButtonView openButton;
    private final ButtonView deleteButton;

    @Inject
    public FsVolumeGroupPresenter(
            final EventBus eventBus,
            final WrapperView view,
            final FsVolumeGroupListController fsVolumeGroupList,
            final Provider<FsVolumeGroupEditController> editProvider,
            final RestFactory restFactory,
            final Provider<NewFsVolumeGroupController> newFsVolumeGroupControllerProvider) {

        super(eventBus, view);
        own(fsVolumeGroupList);
        this.fsVolumeGroupList = fsVolumeGroupList;
        this.editProvider = editProvider;
        this.restFactory = restFactory;
        this.newFsVolumeGroupControllerProvider = newFsVolumeGroupControllerProvider;

        fsVolumeGroupList.getToolbar().setName("Volume group actions");
        newButton = fsVolumeGroupList.getToolbar().button(SvgImage.ADD,
                "New Data Volume Group", this::add);
        openButton = fsVolumeGroupList.getToolbar().button(SvgImage.EDIT,
                "Edit Selected Data Volume Group", this::edit);
        openButton.setEnabled(false);
        deleteButton = fsVolumeGroupList.getToolbar().button(SvgImage.DELETE,
                "Delete Selected Data Volume Group", this::delete);
        deleteButton.setEnabled(false);

        view.setView(fsVolumeGroupList);

        own(fsVolumeGroupList.getSelectionModel().addSelectionHandler(event -> {
            enableButtons();
            if (event.getSelectionType().isDoubleSelect()) {
                edit();
            }
        }));
    }

    private void add() {
        newFsVolumeGroupControllerProvider.get().show(this, "", volumeGroup -> {
            edit(volumeGroup);
            refresh();
        });
    }

    private void edit() {
        final FsVolumeGroupRow selected = fsVolumeGroupList.getSelectionModel().getSelected();
        if (selected != null) {
            final FsVolumeGroup volumeGroup = selected.getGroup();
            restFactory
                    .create(FS_VOLUME_GROUP_RESOURCE)
                    .method(res -> res.fetch(volumeGroup.getId()))
                    .onSuccess(this::edit)
                    .taskMonitorFactory(this)
                    .exec();
        }
    }

    private void edit(final FsVolumeGroup volumeGroup) {
        editProvider.get().show(this, volumeGroup, "Edit Data Volume Group - " + volumeGroup.getName(), result -> {
            if (result != null) {
                refresh();
            }
        });
    }

    private void delete() {
        final List<FsVolumeGroupRow> list = fsVolumeGroupList.getSelectionModel().getSelectedItems();
        if (NullSafe.hasItems(list)) {
            String message = "Are you sure you want to delete the selected volume group?";
            if (list.size() > 1) {
                message = "Are you sure you want to delete the selected volume groups?";
            }
            ConfirmEvent.fire(FsVolumeGroupPresenter.this, message,
                    result -> {
                        if (result) {
                            fsVolumeGroupList.getSelectionModel().clear();
                            for (final FsVolumeGroupRow row : list) {
                                final FsVolumeGroup volume = row.getGroup();
                                restFactory
                                        .create(FS_VOLUME_GROUP_RESOURCE)
                                        .method(res -> res.delete(volume.getId()))
                                        .onSuccess(response -> refresh())
                                        .taskMonitorFactory(this)
                                        .exec();
                            }
                        }
                    });
        }
    }

    private void enableButtons() {
        final boolean enabled = fsVolumeGroupList.getSelectionModel().getSelected() != null;
        openButton.setEnabled(enabled);
        deleteButton.setEnabled(enabled);
    }

    public void refresh() {
        fsVolumeGroupList.refresh();
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
        return "Data Volumes";
    }

    @Override
    public String getType() {
        return TAB_TYPE;
    }
}
