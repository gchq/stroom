/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.data.store.impl.fs.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.data.store.impl.fs.shared.FsVolume;
import stroom.data.store.impl.fs.shared.FsVolumeGroup;
import stroom.data.store.impl.fs.shared.FsVolumeGroupResource;
import stroom.data.store.impl.fs.shared.FsVolumeResource;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.svg.client.SvgPresets;
import stroom.util.client.DelayedUpdate;
import stroom.widget.button.client.ButtonView;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class FsVolumeGroupEditPresenter
        extends MyPresenterWidget<FsVolumeGroupEditPresenter.FsVolumeGroupEditView> {

    private static final FsVolumeResource FS_VOLUME_RESOURCE = GWT.create(FsVolumeResource.class);
    private static final FsVolumeGroupResource FS_VOLUME_GROUP_RESOURCE =
            GWT.create(FsVolumeGroupResource.class);

    private final FsVolumeStatusListPresenter volumeStatusListPresenter;
    private final Provider<FsVolumeEditPresenter> editProvider;
    private final RestFactory restFactory;

    private final ButtonView newButton;
    private final ButtonView openButton;
    private final ButtonView deleteButton;
    private final ButtonView rescanButton;

    private FsVolumeGroup volumeGroup;
    private boolean opening;
    private boolean open;

    private final DelayedUpdate delayedUpdate;

    @Inject
    public FsVolumeGroupEditPresenter(final EventBus eventBus,
                                      final FsVolumeGroupEditView view,
                                      final FsVolumeStatusListPresenter volumeStatusListPresenter,
                                      final Provider<FsVolumeEditPresenter> editProvider,
                                      final RestFactory restFactory) {
        super(eventBus, view);
        this.volumeStatusListPresenter = volumeStatusListPresenter;
        this.editProvider = editProvider;
        this.restFactory = restFactory;

        newButton = volumeStatusListPresenter.getView().addButton(SvgPresets.NEW_ITEM);
        openButton = volumeStatusListPresenter.getView().addButton(SvgPresets.EDIT);
        deleteButton = volumeStatusListPresenter.getView().addButton(SvgPresets.DELETE);
        rescanButton = volumeStatusListPresenter.getView().addButton(SvgPresets.REFRESH_GREEN);
        rescanButton.setTitle("Rescan Volumes");

        view.setListView(volumeStatusListPresenter.getView());
        delayedUpdate = new DelayedUpdate(volumeStatusListPresenter::refresh);
    }

    @Override
    protected void onBind() {
        registerHandler(volumeStatusListPresenter.getSelectionModel().addSelectionHandler(event -> {
            enableButtons();
            if (event.getSelectionType().isDoubleSelect()) {
                edit();
            }
        }));
        registerHandler(newButton.addClickHandler(event -> create()));
        registerHandler(openButton.addClickHandler(event -> edit()));
        registerHandler(deleteButton.addClickHandler(event -> delete()));
        registerHandler(rescanButton.addClickHandler(event -> {
            final Rest<Boolean> rest = restFactory.create();
            delayedUpdate.reset();
            rest
                    .onSuccess(response -> delayedUpdate.update())
                    .onFailure(throwable -> {
                    })
                    .call(FS_VOLUME_RESOURCE)
                    .rescan();

        }));
    }

    private void create() {
        final FsVolume fsVolume = new FsVolume();
        fsVolume.setVolumeGroupId(volumeGroup.getId());
        editVolume(fsVolume, "Add Volume");
    }

    private void edit() {
        final FsVolume volume = volumeStatusListPresenter.getSelectionModel().getSelected();
        if (volume != null) {
            final Rest<FsVolume> rest = restFactory.create();
            rest
                    .onSuccess(result -> editVolume(result, "Edit Volume"))
                    .call(FS_VOLUME_RESOURCE)
                    .fetch(volume.getId());
        }
    }

    private void editVolume(final FsVolume fsVolume, final String caption) {
        final FsVolumeEditPresenter editor = editProvider.get();
        editor.show(fsVolume, caption, result -> {
            if (result != null) {
                volumeStatusListPresenter.refresh();
            }
            editor.hide();
        });
    }

    private void delete() {
        final List<FsVolume> list = volumeStatusListPresenter.getSelectionModel().getSelectedItems();
        if (list != null && list.size() > 0) {
            String message = "Are you sure you want to delete the selected volume?";
            if (list.size() > 1) {
                message = "Are you sure you want to delete the selected volumes?";
            }
            ConfirmEvent.fire(FsVolumeGroupEditPresenter.this, message,
                    result -> {
                        if (result) {
                            volumeStatusListPresenter.getSelectionModel().clear();
                            for (final FsVolume volume : list) {
                                final Rest<Boolean> rest = restFactory.create();
                                rest.onSuccess(response -> volumeStatusListPresenter.refresh()).call(
                                        FS_VOLUME_RESOURCE).delete(volume.getId());
                            }
                        }
                    });
        }
    }

    private void enableButtons() {
        final boolean enabled = volumeStatusListPresenter.getSelectionModel().getSelected() != null;
        openButton.setEnabled(enabled);
        deleteButton.setEnabled(enabled);
    }

    void show(final FsVolumeGroup volumeGroup,
              final String title,
              final Consumer<FsVolumeGroup> consumer) {
        if (!opening) {
            opening = true;
            open(volumeGroup, title, consumer);

//            final ExpressionOperator expression = ExpressionUtil.equals(FsVolumeFields.GROUP_ID,
//                    volumeGroup.getId());
//            final ExpressionCriteria expressionCriteria = new ExpressionCriteria(expression);
//            // TODO: 09/09/2022 Need to implement user defined sorting
//            expressionCriteria.setSort(FsVolumeFields.NODE_NAME.getName());
//            expressionCriteria.addSort(FsVolumeFields.PATH.getName());
//
//            volumeStatusListPresenter.init(expressionCriteria, volumes ->
//                    open(volumeGroup, title, consumer));
        }
    }

    private void open(final FsVolumeGroup volumeGroup,
                      final String title,
                      final Consumer<FsVolumeGroup> consumer) {
        if (!open) {
            open = true;

            this.volumeGroup = volumeGroup;
            volumeStatusListPresenter.setGroup(volumeGroup);
            getView().setName(volumeGroup.getName());

            final PopupSize popupSize = PopupSize.resizable(1000, 600);
            ShowPopupEvent.builder(this)
                    .popupType(PopupType.OK_CANCEL_DIALOG)
                    .popupSize(popupSize)
                    .caption(title)
                    .onShow(e -> getView().focus())
                    .onHideRequest(event -> {
                        if (event.isOk()) {
                            volumeGroup.setName(getView().getName());
                            try {
                                doWithGroupNameValidation(getView().getName(), volumeGroup.getId(), () ->
                                        createVolumeGroup(consumer, volumeGroup));
                            } catch (final RuntimeException e) {
                                AlertEvent.fireError(
                                        FsVolumeGroupEditPresenter.this,
                                        e.getMessage(),
                                        null);
                            }
                        } else {
                            consumer.accept(null);
                        }
                    })
                    .fire();
        }
    }

    private void doWithGroupNameValidation(final String groupName,
                                           final Integer groupId,
                                           final Runnable work) {
        if (groupName == null || groupName.isEmpty()) {
            AlertEvent.fireError(
                    FsVolumeGroupEditPresenter.this,
                    "You must provide a name for the index volume group.",
                    null);
        } else {
            final Rest<FsVolumeGroup> rest = restFactory.create();
            rest
                    .onSuccess(grp -> {
                        if (grp != null && !Objects.equals(groupId, grp.getId())) {
                            AlertEvent.fireError(
                                    FsVolumeGroupEditPresenter.this,
                                    "Group name '"
                                            + groupName
                                            + "' is already in use by another group.",
                                    null);
                        } else {
                            work.run();
                        }
                    })
                    .call(FS_VOLUME_GROUP_RESOURCE)
                    .fetchByName(getView().getName());
        }
    }

    private void createVolumeGroup(final Consumer<FsVolumeGroup> consumer,
                                   final FsVolumeGroup volumeGroup) {
        final Rest<FsVolumeGroup> rest = restFactory.create();
        rest
                .onSuccess(consumer)
                .call(FS_VOLUME_GROUP_RESOURCE)
                .update(volumeGroup.getId(), volumeGroup);
    }

    void hide() {
        HidePopupEvent.builder(this).fire();
        open = false;
        opening = false;
    }


    // --------------------------------------------------------------------------------


    public interface FsVolumeGroupEditView extends View, Focus {

        String getName();

        void setName(String name);

        boolean isDefault();

        void setDefault(final boolean isDefault);

        void setListView(View listView);
    }
}
