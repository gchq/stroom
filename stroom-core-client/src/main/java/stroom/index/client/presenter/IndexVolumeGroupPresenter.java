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

package stroom.index.client.presenter;

import stroom.alert.client.event.ConfirmEvent;
import stroom.content.client.presenter.ContentTabPresenter;
import stroom.dispatch.client.RestFactory;
import stroom.index.client.component.IndexVolumeGroupListController;
import stroom.index.client.component.NewIndexVolumeGroupController;
import stroom.index.shared.IndexVolumeGroup;
import stroom.index.shared.IndexVolumeGroupResource;
import stroom.svg.client.IconColour;
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.ButtonView;
import stroom.widget.datagrid.client.WrapperView;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.List;

public class IndexVolumeGroupPresenter extends ContentTabPresenter<WrapperView> {

    public static final String TAB_TYPE = "IndexVolumes";
    private static final IndexVolumeGroupResource INDEX_VOLUME_GROUP_RESOURCE =
            GWT.create(IndexVolumeGroupResource.class);

    private final IndexVolumeGroupListController indexVolumeGroupList;
    private final Provider<IndexVolumeGroupEditPresenter> editProvider;
    private final RestFactory restFactory;
    private final Provider<NewIndexVolumeGroupController> newIndexVolumeGroupControllerProvider;

    private final ButtonView newButton;
    private final ButtonView openButton;
    private final ButtonView deleteButton;

    @Inject
    public IndexVolumeGroupPresenter(
            final EventBus eventBus,
            final WrapperView view,
            final IndexVolumeGroupListController indexVolumeGroupList,
            final Provider<IndexVolumeGroupEditPresenter> editProvider,
            final RestFactory restFactory,
            final Provider<NewIndexVolumeGroupController> newIndexVolumeGroupControllerProvider) {

        super(eventBus, view);
        own(indexVolumeGroupList);
        this.indexVolumeGroupList = indexVolumeGroupList;
        this.editProvider = editProvider;
        this.restFactory = restFactory;
        this.newIndexVolumeGroupControllerProvider = newIndexVolumeGroupControllerProvider;

        indexVolumeGroupList.getToolbar().setName("Volume group actions");
        newButton = indexVolumeGroupList.getToolbar().button(SvgImage.ADD,
                "New Index Volume Group", this::add);
        openButton = indexVolumeGroupList.getToolbar().button(SvgImage.EDIT,
                "Edit Selected Index Volume Group", this::edit);
        openButton.setEnabled(false);
        deleteButton = indexVolumeGroupList.getToolbar().button(SvgImage.DELETE,
                "Delete Selected Index Volume Group", this::delete);
        deleteButton.setEnabled(false);

        view.setView(indexVolumeGroupList);

        own(indexVolumeGroupList.getSelectionModel().addSelectionHandler(event -> {
            enableButtons();
            if (event.getSelectionType().isDoubleSelect()) {
                edit();
            }
        }));
    }

//    public void show() {
//        final PopupUiHandlers popupUiHandlers = new PopupUiHandlers() {
//            @Override
//            public void onHideRequest(final boolean autoClose, final boolean ok) {
//                hide();
//            }
//        };
//        final PopupSize popupSize = PopupSize.resizable(1000, 600);
//        ShowPopupEvent.fire(
//                this,
//                this,
//                PopupType.CLOSE_DIALOG,
//                null,
//                popupSize,
//                "Index Volumes",
//                popupUiHandlers,
//                null);
//    }
//
//    public void hide() {
//        HidePopupEvent.fire(this, this, false, true);
//    }

    private void add() {
        newIndexVolumeGroupControllerProvider.get().show(this, "", indexVolumeGroup -> {
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
        final IndexVolumeGroupEditPresenter editor = makeDialog(editProvider);
        editor.show(indexVolumeGroup,
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
            ConfirmEvent.fire(IndexVolumeGroupPresenter.this, message,
                    result -> {
                        if (result) {
                            indexVolumeGroupList.getSelectionModel().clear();
                            for (final IndexVolumeGroup volume : list) {
                                restFactory
                                        .create(INDEX_VOLUME_GROUP_RESOURCE)
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
        final boolean enabled = indexVolumeGroupList.getSelectionModel().getSelected() != null;
        openButton.setEnabled(enabled);
        deleteButton.setEnabled(enabled);
    }

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
