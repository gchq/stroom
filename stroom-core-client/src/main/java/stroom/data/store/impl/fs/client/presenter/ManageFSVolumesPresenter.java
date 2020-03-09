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

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenter;
import com.gwtplatform.mvp.client.annotations.ProxyCodeSplit;
import com.gwtplatform.mvp.client.proxy.Proxy;
import stroom.alert.client.event.ConfirmEvent;
import stroom.data.store.impl.fs.shared.FsVolume;
import stroom.data.store.impl.fs.shared.FsVolumeResource;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.node.client.view.WrapperView;
import stroom.svg.client.SvgPresets;
import stroom.widget.button.client.ButtonView;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.DefaultPopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

import java.util.List;

public class ManageFSVolumesPresenter extends MyPresenter<WrapperView, ManageFSVolumesPresenter.ManageVolumesProxy> {
    private static final FsVolumeResource FS_VOLUME_RESOURCE = GWT.create(FsVolumeResource.class);

    private final FSVolumeStatusListPresenter volumeStatusListPresenter;
    private final Provider<FSVolumeEditPresenter> editProvider;
    private final RestFactory restFactory;

    private final ButtonView newButton;
    private final ButtonView openButton;
    private final ButtonView deleteButton;
    private final ButtonView rescanButton;

    @Inject
    public ManageFSVolumesPresenter(final EventBus eventBus, final WrapperView view, final ManageVolumesProxy proxy,
                                    final FSVolumeStatusListPresenter volumeStatusListPresenter, final Provider<FSVolumeEditPresenter> editProvider,
                                    final RestFactory restFactory) {
        super(eventBus, view, proxy);
        this.volumeStatusListPresenter = volumeStatusListPresenter;
        this.editProvider = editProvider;
        this.restFactory = restFactory;

        newButton = volumeStatusListPresenter.getView().addButton(SvgPresets.NEW_ITEM);
        openButton = volumeStatusListPresenter.getView().addButton(SvgPresets.EDIT);
        deleteButton = volumeStatusListPresenter.getView().addButton(SvgPresets.DELETE);
        rescanButton = volumeStatusListPresenter.getView().addButton(SvgPresets.REFRESH_GREEN);
        rescanButton.setTitle("Rescan Public Volumes");

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
                open(popupUiHandlers);
            }
        }));
        registerHandler(newButton.addClickHandler(event -> {
            final FSVolumeEditPresenter editor = editProvider.get();
            editor.addVolume(new FsVolume(), popupUiHandlers);
        }));
        registerHandler(openButton.addClickHandler(event -> open(popupUiHandlers)));
        registerHandler(deleteButton.addClickHandler(event -> delete()));
        registerHandler(rescanButton.addClickHandler(event -> {
            final Rest<Boolean> rest = restFactory.create();
            rest.onSuccess(response -> refresh()).call(FS_VOLUME_RESOURCE).rescan();
        }));
    }

    private void open(final PopupUiHandlers popupUiHandlers) {
        final FsVolume volume = volumeStatusListPresenter.getSelectionModel().getSelected();
        if (volume != null) {
            final Rest<FsVolume> rest = restFactory.create();
            rest.onSuccess(result -> {
                final FSVolumeEditPresenter editor = editProvider.get();
                editor.editVolume(result, popupUiHandlers);
            }).call(FS_VOLUME_RESOURCE).read(volume.getId());
        }
    }

    private void delete() {
        final List<FsVolume> list = volumeStatusListPresenter.getSelectionModel().getSelectedItems();
        if (list != null && list.size() > 0) {
            String message = "Are you sure you want to delete the selected volume?";
            if (list.size() > 1) {
                message = "Are you sure you want to delete the selected volumes?";
            }
            ConfirmEvent.fire(ManageFSVolumesPresenter.this, message,
                    result -> {
                        if (result) {
                            volumeStatusListPresenter.getSelectionModel().clear();
                            for (final FsVolume volume : list) {
                                final Rest<Boolean> rest = restFactory.create();
                                rest.onSuccess(response -> refresh()).call(FS_VOLUME_RESOURCE).delete(volume.getId());
                            }
                        }
                    });
        }
    }

    @Override
    protected void revealInParent() {
        final PopupSize popupSize = new PopupSize(1000, 600, true);
        ShowPopupEvent.fire(this, this, PopupType.CLOSE_DIALOG, null, popupSize, "Manage Volumes", null, null);
    }

    private void enableButtons() {
        final boolean enabled = volumeStatusListPresenter.getSelectionModel().getSelected() != null;
        openButton.setEnabled(enabled);
        deleteButton.setEnabled(enabled);
    }

    public void refresh() {
        volumeStatusListPresenter.refresh();
    }

    @ProxyCodeSplit
    public interface ManageVolumesProxy extends Proxy<ManageFSVolumesPresenter> {
    }
}
