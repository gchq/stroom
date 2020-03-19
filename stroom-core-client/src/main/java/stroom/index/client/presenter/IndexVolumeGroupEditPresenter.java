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

package stroom.index.client.presenter;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.entity.shared.ExpressionCriteria;
import stroom.index.shared.IndexVolume;
import stroom.index.shared.IndexVolumeFields;
import stroom.index.shared.IndexVolumeGroup;
import stroom.index.shared.IndexVolumeGroupResource;
import stroom.index.shared.IndexVolumeResource;
import stroom.node.client.NodeCache;
import stroom.node.client.view.WrapperView;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.svg.client.SvgPresets;
import stroom.widget.button.client.ButtonView;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.DefaultPopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

import java.util.List;
import java.util.function.Consumer;

public class IndexVolumeGroupEditPresenter extends MyPresenterWidget<WrapperView> {
    private static final IndexVolumeResource INDEX_VOLUME_RESOURCE = GWT.create(IndexVolumeResource.class);
    private static final IndexVolumeGroupResource INDEX_VOLUME_GROUP_RESOURCE = GWT.create(IndexVolumeGroupResource.class);

    private final IndexVolumeStatusListPresenter volumeStatusListPresenter;
    private final Provider<IndexVolumeEditPresenter> editProvider;
    private final RestFactory restFactory;
    private final NodeCache nodeCache;

    private final ButtonView newButton;
    private final ButtonView openButton;
    private final ButtonView deleteButton;
    private final ButtonView rescanButton;

    private IndexVolumeGroup volumeGroup;
    private boolean opening;

    @Inject
    public IndexVolumeGroupEditPresenter(final EventBus eventBus,
                                         final WrapperView view,
                                         final IndexVolumeStatusListPresenter volumeStatusListPresenter,
                                         final Provider<IndexVolumeEditPresenter> editProvider,
                                         final RestFactory restFactory,
                                         final NodeCache nodeCache) {
        super(eventBus, view);
        this.volumeStatusListPresenter = volumeStatusListPresenter;
        this.editProvider = editProvider;
        this.restFactory = restFactory;
        this.nodeCache = nodeCache;

        newButton = volumeStatusListPresenter.getView().addButton(SvgPresets.NEW_ITEM);
        openButton = volumeStatusListPresenter.getView().addButton(SvgPresets.EDIT);
        deleteButton = volumeStatusListPresenter.getView().addButton(SvgPresets.DELETE);
        rescanButton = volumeStatusListPresenter.getView().addButton(SvgPresets.REFRESH_GREEN);
        rescanButton.setTitle("Rescan Volumes");

        view.setView(volumeStatusListPresenter.getView());
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
            nodeCache.listAllNodes(nodeNames ->
                            nodeNames.forEach(nodeName ->
                                    rest
                                            .onSuccess(response -> volumeStatusListPresenter.refresh())
                                            .onFailure(throwable -> {
                                            })
                                            .call(INDEX_VOLUME_RESOURCE)
                                            .rescan(nodeName)
                            ),
                    throwable -> {
                    });

        }));
    }

    private void create() {
        final IndexVolume indexVolume = new IndexVolume.Builder().indexVolumeGroupName(volumeGroup.getName()).build();
        editVolume(indexVolume, "Add Volume");
    }

    private void edit() {
        final IndexVolume volume = volumeStatusListPresenter.getSelectionModel().getSelected();
        if (volume != null) {
            final Rest<IndexVolume> rest = restFactory.create();
            rest
                    .onSuccess(result -> editVolume(result, "Edit Volume"))
                    .call(INDEX_VOLUME_RESOURCE)
                    .read(volume.getId());
        }
    }

    private void editVolume(final IndexVolume indexVolume, final String caption) {
        final IndexVolumeEditPresenter editor = editProvider.get();
        editor.show(indexVolume, caption, result -> {
            if (result != null) {
                volumeStatusListPresenter.refresh();
            }
            editor.hide();
        });
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
                                final Rest<Boolean> rest = restFactory.create();
                                rest.onSuccess(response -> volumeStatusListPresenter.refresh()).call(INDEX_VOLUME_RESOURCE).delete(volume.getId());
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

    void show(final IndexVolumeGroup volumeGroup, final String title, final Consumer<IndexVolumeGroup> consumer) {
        if (!opening) {
            opening = true;

            this.volumeGroup = volumeGroup;

            final ExpressionOperator expression = new ExpressionOperator.Builder()
                    .addTerm(IndexVolumeFields.GROUP_NAME, Condition.EQUALS, volumeGroup.getName())
                    .build();
            volumeStatusListPresenter.init(new ExpressionCriteria(expression), volumes -> {
                opening = false;

                final PopupUiHandlers popupUiHandlers = new DefaultPopupUiHandlers() {
                    @Override
                    public void onHideRequest(final boolean autoClose, final boolean ok) {
                        if (ok) {
                            try {
                                final Rest<IndexVolumeGroup> rest = restFactory.create();
                                rest
                                        .onSuccess(consumer)
                                        .call(INDEX_VOLUME_GROUP_RESOURCE)
                                        .update(volumeGroup.getId(), volumeGroup);

                            } catch (final RuntimeException e) {
                                AlertEvent.fireError(IndexVolumeGroupEditPresenter.this, e.getMessage(), null);
                            }
                        } else {
                            consumer.accept(null);
                        }
                    }
                };

                final PopupSize popupSize = new PopupSize(1000, 600, true);
                ShowPopupEvent.fire(this, this, PopupType.OK_CANCEL_DIALOG, popupSize, title,
                        popupUiHandlers);
            });
        }
    }

    void hide() {
        HidePopupEvent.fire(this, this, false, true);
    }
}
