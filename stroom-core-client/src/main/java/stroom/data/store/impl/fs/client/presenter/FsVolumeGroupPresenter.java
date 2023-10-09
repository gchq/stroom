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

import stroom.alert.client.event.ConfirmEvent;
import stroom.content.client.presenter.ContentTabPresenter;
import stroom.data.grid.client.WrapperView;
import stroom.data.store.impl.fs.shared.FsVolumeGroup;
import stroom.data.store.impl.fs.shared.FsVolumeGroupResource;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.svg.client.IconColour;
import stroom.svg.client.SvgPresets;
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.ButtonView;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.List;

public class FsVolumeGroupPresenter extends ContentTabPresenter<WrapperView> {

    private static final FsVolumeGroupResource FS_VOLUME_GROUP_RESOURCE =
            GWT.create(FsVolumeGroupResource.class);

    private final FsVolumeGroupListPresenter volumeStatusListPresenter;
    private final Provider<FsVolumeGroupEditPresenter> editProvider;
    private final RestFactory restFactory;
    private final Provider<NewFsVolumeGroupPresenter> newFsVolumeGroupPresenterProvider;

    private final ButtonView newButton;
    private final ButtonView openButton;
    private final ButtonView deleteButton;

    @Inject
    public FsVolumeGroupPresenter(
            final EventBus eventBus,
            final WrapperView view,
            final FsVolumeGroupListPresenter volumeStatusListPresenter,
            final Provider<FsVolumeGroupEditPresenter> editProvider,
            final RestFactory restFactory,
            final Provider<NewFsVolumeGroupPresenter> newFsVolumeGroupPresenterProvider) {

        super(eventBus, view);
        this.volumeStatusListPresenter = volumeStatusListPresenter;
        this.editProvider = editProvider;
        this.restFactory = restFactory;
        this.newFsVolumeGroupPresenterProvider = newFsVolumeGroupPresenterProvider;

        newButton = volumeStatusListPresenter.getView().addButton(SvgPresets.NEW_ITEM);
        openButton = volumeStatusListPresenter.getView().addButton(SvgPresets.EDIT);
        deleteButton = volumeStatusListPresenter.getView().addButton(SvgPresets.DELETE);

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
        registerHandler(newButton.addClickHandler(event -> add()));
        registerHandler(openButton.addClickHandler(event -> edit()));
        registerHandler(deleteButton.addClickHandler(event -> delete()));
    }

    private void add() {
        final NewFsVolumeGroupPresenter presenter = newFsVolumeGroupPresenterProvider.get();
        presenter.show("", name -> {
            if (name != null) {
                final Rest<FsVolumeGroup> rest = restFactory.create();
                rest
                        .onSuccess(FsVolumeGroup -> {
                            edit(FsVolumeGroup);
                            presenter.hide();
                            refresh();
                        })
                        .call(FS_VOLUME_GROUP_RESOURCE)
                        .create(name);
            } else {
                presenter.hide();
            }
        });
    }

    private void edit() {
        final FsVolumeGroup volume = volumeStatusListPresenter.getSelectionModel().getSelected();
        if (volume != null) {
            final Rest<FsVolumeGroup> rest = restFactory.create();
            rest
                    .onSuccess(this::edit)
                    .call(FS_VOLUME_GROUP_RESOURCE)
                    .fetch(volume.getId());
        }
    }

    private void edit(final FsVolumeGroup FsVolumeGroup) {
        final FsVolumeGroupEditPresenter editor = editProvider.get();
        editor.show(FsVolumeGroup, "Edit Volume Group - " + FsVolumeGroup.getName(), result -> {
            if (result != null) {
                refresh();
            }
            editor.hide();
        });
    }

    private void delete() {
        final List<FsVolumeGroup> list = volumeStatusListPresenter.getSelectionModel().getSelectedItems();
        if (list != null && list.size() > 0) {
            String message = "Are you sure you want to delete the selected volume group?";
            if (list.size() > 1) {
                message = "Are you sure you want to delete the selected volume groups?";
            }
            ConfirmEvent.fire(FsVolumeGroupPresenter.this, message,
                    result -> {
                        if (result) {
                            volumeStatusListPresenter.getSelectionModel().clear();
                            for (final FsVolumeGroup volume : list) {
                                final Rest<Boolean> rest = restFactory.create();
                                rest.onSuccess(response ->
                                        refresh()).call(FS_VOLUME_GROUP_RESOURCE).delete(volume.getId());
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
}
