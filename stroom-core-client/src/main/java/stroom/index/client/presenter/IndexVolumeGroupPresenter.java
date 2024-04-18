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

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.content.client.presenter.ContentTabPresenter;
import stroom.data.grid.client.WrapperView;
import stroom.dispatch.client.RestFactory;
import stroom.index.shared.IndexVolumeGroup;
import stroom.index.shared.IndexVolumeGroupResource;
import stroom.svg.client.IconColour;
import stroom.svg.client.SvgPresets;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.GwtNullSafe;
import stroom.widget.button.client.ButtonView;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.List;

public class IndexVolumeGroupPresenter extends ContentTabPresenter<WrapperView> {

    public static final String TAB_TYPE = "IndexVolumes";
    private static final IndexVolumeGroupResource INDEX_VOLUME_GROUP_RESOURCE =
            GWT.create(IndexVolumeGroupResource.class);

    private final IndexVolumeGroupListPresenter volumeStatusListPresenter;
    private final Provider<IndexVolumeGroupEditPresenter> editProvider;
    private final RestFactory restFactory;
    private final Provider<NewIndexVolumeGroupPresenter> newIndexVolumeGroupPresenterProvider;

    private final ButtonView newButton;
    private final ButtonView openButton;
    private final ButtonView deleteButton;

    @Inject
    public IndexVolumeGroupPresenter(
            final EventBus eventBus,
            final WrapperView view,
            final IndexVolumeGroupListPresenter volumeStatusListPresenter,
            final Provider<IndexVolumeGroupEditPresenter> editProvider,
            final RestFactory restFactory,
            final Provider<NewIndexVolumeGroupPresenter> newIndexVolumeGroupPresenterProvider) {

        super(eventBus, view);
        this.volumeStatusListPresenter = volumeStatusListPresenter;
        this.editProvider = editProvider;
        this.restFactory = restFactory;
        this.newIndexVolumeGroupPresenterProvider = newIndexVolumeGroupPresenterProvider;

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
        final NewIndexVolumeGroupPresenter presenter = newIndexVolumeGroupPresenterProvider.get();
        presenter.show(indexVolumeGroup -> {
            if (indexVolumeGroup != null) {
                restFactory
                        .create(INDEX_VOLUME_GROUP_RESOURCE)
                        .method(res -> res.create(indexVolumeGroup))
                        .onSuccess(indexVolumeGroup2 -> {
                            edit(indexVolumeGroup2);
                            presenter.hide();
                            refresh();
                        })
                        .exec();
            } else {
                presenter.hide();
            }
        });
    }

    private void edit() {
        final IndexVolumeGroup volume = volumeStatusListPresenter.getSelectionModel().getSelected();
        if (volume != null) {
            restFactory
                    .create(INDEX_VOLUME_GROUP_RESOURCE)
                    .method(res -> res.fetch(volume.getId()))
                    .onSuccess(this::edit)
                    .exec();
        }
    }

    private void edit(final IndexVolumeGroup indexVolumeGroup) {
        final IndexVolumeGroupEditPresenter editor = editProvider.get();
        editor.show(indexVolumeGroup, "Edit Volume Group - " + indexVolumeGroup.getName(), result -> {
            if (result != null) {
                refresh();
            }
            editor.hide();
        });
    }

    private void delete() {
        final List<IndexVolumeGroup> list = volumeStatusListPresenter.getSelectionModel().getSelectedItems();
        if (GwtNullSafe.hasItems(list)) {
            if (list.stream().anyMatch(IndexVolumeGroup::isDefaultVolume)) {
                final String msg = "You cannot delete the default volume group. This will prevent events from " +
                        "being indexed if no volume group is explicitly specified on the Index. You should first " +
                        "make another volume group the default.";
                AlertEvent.fireError(this, msg, null);
            } else {
                final String message = list.size() > 1
                        ? "Are you sure you want to delete the selected volume group?"
                        : "Are you sure you want to delete the selected volume groups?";
                ConfirmEvent.fire(IndexVolumeGroupPresenter.this, message,
                        result -> {
                            if (result) {
                                volumeStatusListPresenter.getSelectionModel().clear();
                                for (final IndexVolumeGroup volume : list) {
                                    restFactory
                                            .create(INDEX_VOLUME_GROUP_RESOURCE)
                                            .method(res -> res.delete(volume.getId()))
                                            .onSuccess(response -> refresh())
                                            .exec();
                                }
                            }
                        });
            }
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
        return "Index Volumes";
    }

    @Override
    public String getType() {
        return TAB_TYPE;
    }
}
