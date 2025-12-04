/*
 * Copyright 2016-2025 Crown Copyright
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
import stroom.node.client.NodeManager;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionUtil;
import stroom.svg.client.SvgPresets;
import stroom.util.client.DelayedUpdate;
import stroom.widget.button.client.ButtonView;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
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

public class IndexVolumeGroupEditPresenter
        extends MyPresenterWidget<IndexVolumeGroupEditPresenter.IndexVolumeGroupEditView> {

    private static final IndexVolumeResource INDEX_VOLUME_RESOURCE = GWT.create(IndexVolumeResource.class);
    private static final IndexVolumeGroupResource INDEX_VOLUME_GROUP_RESOURCE =
            GWT.create(IndexVolumeGroupResource.class);

    private final IndexVolumeStatusListPresenter volumeStatusListPresenter;
    private final Provider<IndexVolumeEditPresenter> editProvider;
    private final RestFactory restFactory;
    private final NodeManager nodeManager;

    private final ButtonView newButton;
    private final ButtonView openButton;
    private final ButtonView deleteButton;
    private final ButtonView rescanButton;

    private IndexVolumeGroup volumeGroup;
    private boolean opening;
    private boolean open;

    private final DelayedUpdate delayedUpdate;

    @Inject
    public IndexVolumeGroupEditPresenter(final EventBus eventBus,
                                         final IndexVolumeGroupEditView view,
                                         final IndexVolumeStatusListPresenter volumeStatusListPresenter,
                                         final Provider<IndexVolumeEditPresenter> editProvider,
                                         final RestFactory restFactory,
                                         final NodeManager nodeManager) {
        super(eventBus, view);
        this.volumeStatusListPresenter = volumeStatusListPresenter;
        this.editProvider = editProvider;
        this.restFactory = restFactory;
        this.nodeManager = nodeManager;

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
            delayedUpdate.reset();
            nodeManager.listAllNodes(nodeNames ->
                            nodeNames.forEach(nodeName ->
                                    restFactory
                                            .create(INDEX_VOLUME_RESOURCE)
                                            .method(res -> res.rescan(nodeName))
                                            .onSuccess(response -> delayedUpdate.update())
                                            .onFailure(throwable -> {
                                            })
                                            .taskMonitorFactory(this)
                                            .exec()
                            ),
                    throwable -> {
                    },
                    volumeStatusListPresenter.getTaskListener());

        }));
    }

    private void create() {
        final IndexVolume indexVolume = IndexVolume.builder().indexVolumeGroupId(volumeGroup.getId()).build();
        editVolume(indexVolume, "Add Volume");
    }

    private void edit() {
        final IndexVolume volume = volumeStatusListPresenter.getSelectionModel().getSelected();
        if (volume != null) {
            restFactory
                    .create(INDEX_VOLUME_RESOURCE)
                    .method(res -> res.fetch(volume.getId()))
                    .onSuccess(result -> editVolume(result, "Edit Volume"))
                    .taskMonitorFactory(volumeStatusListPresenter.getTaskListener())
                    .exec();
        }
    }

    private void editVolume(final IndexVolume indexVolume, final String caption) {
        final IndexVolumeEditPresenter editor = editProvider.get();
        editor.show(indexVolume, caption, result -> {
            if (result != null) {
                volumeStatusListPresenter.refresh();
            }
        }, volumeStatusListPresenter.getTaskListener());
    }

    private void delete() {
        final List<IndexVolume> list = volumeStatusListPresenter.getSelectionModel().getSelectedItems();
        if (list != null && list.size() > 0) {
            String message = "Are you sure you want to delete the selected volume?";
            if (list.size() > 1) {
                message = "Are you sure you want to delete the selected volumes?";
            }
            ConfirmEvent.fire(IndexVolumeGroupEditPresenter.this, message,
                    result -> {
                        if (result) {
                            volumeStatusListPresenter.getSelectionModel().clear();
                            for (final IndexVolume volume : list) {
                                restFactory
                                        .create(INDEX_VOLUME_RESOURCE)
                                        .method(res -> res.delete(volume.getId()))
                                        .onSuccess(response -> volumeStatusListPresenter.refresh())
                                        .taskMonitorFactory(this)
                                        .exec();
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

    void show(final IndexVolumeGroup volumeGroup,
              final String title,
              final Consumer<IndexVolumeGroup> consumer) {
        if (!opening) {
            opening = true;
            final ExpressionOperator expression = ExpressionUtil.equalsId(IndexVolumeFields.GROUP_ID,
                    volumeGroup.getId());
            final ExpressionCriteria expressionCriteria = new ExpressionCriteria(expression);
            // TODO: 09/09/2022 Need to implement user defined sorting
            expressionCriteria.setSort(IndexVolumeFields.NODE_NAME.getFldName());
            expressionCriteria.addSort(IndexVolumeFields.PATH.getFldName());

            volumeStatusListPresenter.init(expressionCriteria, volumes ->
                    open(volumeGroup, title, consumer));
        }
    }

    private void open(final IndexVolumeGroup volumeGroup,
                      final String title,
                      final Consumer<IndexVolumeGroup> consumer) {
        if (!open) {
            open = true;

            this.volumeGroup = volumeGroup;
            getView().setName(volumeGroup.getName());

            final PopupSize popupSize = PopupSize.resizable(1400, 600);
            ShowPopupEvent.builder(this)
                    .popupType(PopupType.OK_CANCEL_DIALOG)
                    .popupSize(popupSize)
                    .caption(title)
                    .onShow(e -> getView().focus())
                    .onHideRequest(e -> {
                        if (e.isOk()) {
                            volumeGroup.setName(getView().getName());
                            try {
                                doWithGroupNameValidation(getView().getName(), volumeGroup.getId(), () ->
                                        createVolumeGroup(consumer, volumeGroup, e), e);
                            } catch (final RuntimeException ex) {
                                AlertEvent.fireError(
                                        IndexVolumeGroupEditPresenter.this,
                                        ex.getMessage(),
                                        e::reset);
                            }
                        } else {
                            e.hide();
                        }
                    })
                    .onHide(e -> {
                        open = false;
                        opening = false;
                    })
                    .fire();
        }
    }

    private void doWithGroupNameValidation(final String groupName,
                                           final Integer groupId,
                                           final Runnable work,
                                           final HidePopupRequestEvent event) {
        if (groupName == null || groupName.isEmpty()) {
            AlertEvent.fireError(
                    IndexVolumeGroupEditPresenter.this,
                    "You must provide a name for the index volume group.",
                    event::reset);
        } else {
            restFactory
                    .create(INDEX_VOLUME_GROUP_RESOURCE)
                    .method(res -> res.fetchByName(getView().getName()))
                    .onSuccess(grp -> {
                        if (grp != null && !Objects.equals(groupId, grp.getId())) {
                            AlertEvent.fireError(
                                    IndexVolumeGroupEditPresenter.this,
                                    "Group name '"
                                            + groupName
                                            + "' is already in use by another group.",
                                    event::reset);
                        } else {
                            work.run();
                        }
                    })
                    .onFailure(RestErrorHandler.forPopup(this, event))
                    .taskMonitorFactory(this)
                    .exec();
        }
    }

    private void createVolumeGroup(final Consumer<IndexVolumeGroup> consumer,
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
                .taskMonitorFactory(this)
                .exec();
    }


    // --------------------------------------------------------------------------------


    public interface IndexVolumeGroupEditView extends View, Focus {

        String getName();

        void setName(String name);

        void setListView(View listView);
    }
}
