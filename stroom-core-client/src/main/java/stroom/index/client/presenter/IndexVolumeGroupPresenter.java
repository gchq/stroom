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
import stroom.alert.client.event.ConfirmEvent;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.index.shared.IndexVolumeGroup;
import stroom.index.shared.IndexVolumeGroupResource;
import stroom.node.client.view.WrapperView;
import stroom.svg.client.SvgPresets;
import stroom.widget.button.client.ButtonView;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.DefaultPopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

import java.util.List;

public class IndexVolumeGroupPresenter extends MyPresenterWidget<WrapperView> {
    private static final IndexVolumeGroupResource INDEX_VOLUME_GROUP_RESOURCE = GWT.create(IndexVolumeGroupResource.class);

    private final IndexVolumeGroupListPresenter volumeStatusListPresenter;
    private final Provider<IndexVolumeGroupEditPresenter> editProvider;
    private final RestFactory restFactory;

    private final ButtonView newButton;
    private final ButtonView openButton;
    private final ButtonView deleteButton;

    @Inject
    public IndexVolumeGroupPresenter(final EventBus eventBus,
                                     final WrapperView view,
                                     final IndexVolumeGroupListPresenter volumeStatusListPresenter,
                                     final Provider<IndexVolumeGroupEditPresenter> editProvider,
                                     final RestFactory restFactory) {
        super(eventBus, view);
        this.volumeStatusListPresenter = volumeStatusListPresenter;
        this.editProvider = editProvider;
        this.restFactory = restFactory;

        newButton = volumeStatusListPresenter.getView().addButton(SvgPresets.NEW_ITEM);
        openButton = volumeStatusListPresenter.getView().addButton(SvgPresets.EDIT);
        deleteButton = volumeStatusListPresenter.getView().addButton(SvgPresets.DELETE);

        view.setView(volumeStatusListPresenter.getView());
    }

    @Override
    protected void onBind() {
        final PopupUiHandlers popupUiHandlers = new DefaultPopupUiHandlers() {
            @Override
            public void onHide(final boolean autoClose, final boolean ok) {
                refresh();
            }
        };

        registerHandler(volumeStatusListPresenter.getSelectionModel().addSelectionHandler(event -> {
            enableButtons();
            if (event.getSelectionType().isDoubleSelect()) {
                edit(popupUiHandlers);
            }
        }));
        registerHandler(newButton.addClickHandler(event -> add(popupUiHandlers)));
        registerHandler(openButton.addClickHandler(event -> edit(popupUiHandlers)));
        registerHandler(deleteButton.addClickHandler(event -> delete()));
    }

    public void show() {
        final PopupSize popupSize = new PopupSize(1000, 600, true);
        ShowPopupEvent.fire(this, this,
                PopupType.CLOSE_DIALOG, null, popupSize, "Index Volumes", null, null);
    }

    private void add(final PopupUiHandlers popupUiHandlers) {
        final IndexVolumeGroupEditPresenter editor = editProvider.get();
        editor.addVolumeGroup(popupUiHandlers);
    }

    private void edit(final PopupUiHandlers popupUiHandlers) {
        final IndexVolumeGroup volume = volumeStatusListPresenter.getSelectionModel().getSelected();
        if (volume != null) {
            final Rest<IndexVolumeGroup> rest = restFactory.create();
            rest.onSuccess(result -> {
                final IndexVolumeGroupEditPresenter editor = editProvider.get();
                editor.editVolume(result, popupUiHandlers);
            }).call(INDEX_VOLUME_GROUP_RESOURCE).read(volume.getId());
        }
    }

    private void delete() {
        final List<IndexVolumeGroup> list = volumeStatusListPresenter.getSelectionModel().getSelectedItems();
        if (list != null && list.size() > 0) {
            String message = "Are you sure you want to delete the selected volume group?";
            if (list.size() > 1) {
                message = "Are you sure you want to delete the selected volume groups?";
            }
            ConfirmEvent.fire(IndexVolumeGroupPresenter.this, message,
                    result -> {
                        if (result) {
                            volumeStatusListPresenter.getSelectionModel().clear();
                            for (final IndexVolumeGroup volume : list) {
                                final Rest<Boolean> rest = restFactory.create();
                                rest.onSuccess(response -> refresh()).call(INDEX_VOLUME_GROUP_RESOURCE).delete(volume.getId());
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

    public void refresh() {
        volumeStatusListPresenter.refresh();
    }
}
