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

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import stroom.alert.client.event.ConfirmEvent;
import stroom.alert.client.presenter.ConfirmCallback;
import stroom.entity.client.event.DirtyEvent;
import stroom.entity.client.event.DirtyEvent.DirtyHandler;
import stroom.entity.client.event.HasDirtyHandlers;
import stroom.entity.client.presenter.HasRead;
import stroom.entity.client.presenter.HasWrite;
import stroom.index.shared.Index;
import stroom.node.client.presenter.VolumeListPresenter;
import stroom.node.client.presenter.VolumeStatusListPresenter;
import stroom.node.client.view.WrapperView;
import stroom.node.shared.Volume;
import stroom.svg.client.SvgPresets;
import stroom.widget.button.client.ButtonView;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;
import stroom.widget.util.client.MultiSelectionModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class IndexVolumeListPresenter extends MyPresenterWidget<WrapperView>
        implements HasRead<Index>, HasWrite<Index>, HasDirtyHandlers {
    private final VolumeListPresenter volumeListPresenter;
    private final VolumeStatusListPresenter volumeStatusListPresenter;
    private final ButtonView addButton;
    private final ButtonView removeButton;
    private List<Volume> volumes;

    @Inject
    public IndexVolumeListPresenter(final EventBus eventBus, final WrapperView view,
                                    final VolumeListPresenter volumeListPresenter, final VolumeStatusListPresenter volumeStatusListPresenter) {
        super(eventBus, view);
        this.volumeListPresenter = volumeListPresenter;
        this.volumeStatusListPresenter = volumeStatusListPresenter;

        view.setView(volumeListPresenter.getView());

//        volumeStatusListPresenter.setSelectionModel(new MySingleSelectionModel<>());
//        volumeListPresenter.setSelectionModel(new MultiSelectionModel<>());

        addButton = volumeListPresenter.getView().addButton(SvgPresets.ADD);
        addButton.setTitle("Add Volume");
        removeButton = volumeListPresenter.getView().addButton(SvgPresets.DELETE);
        removeButton.setTitle("Remove Volume");
    }

    @Override
    protected void onBind() {
        super.onBind();

        registerHandler(addButton.addClickHandler(event -> onAdd(event)));
        registerHandler(removeButton.addClickHandler(event -> onRemove(event)));
        registerHandler(volumeListPresenter.getSelectionModel().addSelectionHandler(event -> {
            final MultiSelectionModel<Volume> selectionModel = volumeListPresenter.getSelectionModel();
            removeButton.setEnabled(selectionModel.getSelectedItems().size() > 0);
        }));
        registerHandler(volumeStatusListPresenter.getSelectionModel().addSelectionHandler(event -> {
            if (event.getSelectionType().isDoubleSelect()) {
                addVolume(true);
            }
        }));
    }

    private void onAdd(final ClickEvent event) {
        final PopupUiHandlers popupUiHandlers = new PopupUiHandlers() {
            @Override
            public void onHideRequest(final boolean autoClose, final boolean ok) {
                addVolume(ok);
            }

            @Override
            public void onHide(final boolean autoClose, final boolean ok) {
                // Do nothing.
            }
        };

        final PopupSize popupSize = new PopupSize(600, 400, true);
        volumeStatusListPresenter.refresh();
        final MultiSelectionModel<Volume> selectionModel = (MultiSelectionModel<Volume>) volumeStatusListPresenter.getSelectionModel();
        selectionModel.clear();
        ShowPopupEvent.fire(this, volumeStatusListPresenter, PopupType.OK_CANCEL_DIALOG, null, popupSize,
                "Add Volume To Index", popupUiHandlers, true);
    }

    private void addVolume(final boolean ok) {
        if (ok) {
            final MultiSelectionModel<Volume> selectionModel = (MultiSelectionModel<Volume>) volumeStatusListPresenter.getSelectionModel();
            final List<Volume> selected = selectionModel.getSelectedItems();
            if (selected != null && selected.size() > 0) {
                for (final Volume vol : selected) {
                    if (vol != null && !volumes.contains(vol)) {
                        volumes.add(vol);
                        sortVolumes();
                        DirtyEvent.fire(IndexVolumeListPresenter.this, true);
                        refresh();
                    }
                }
            }
        }

        HidePopupEvent.fire(this, volumeStatusListPresenter);
    }

    public void onRemove(final ClickEvent event) {
        final MultiSelectionModel<Volume> selectionModel = (MultiSelectionModel<Volume>) volumeListPresenter.getSelectionModel();
        final List<Volume> selected = selectionModel.getSelectedItems();
        if (selected != null && selected.size() > 0) {
            String message = "Are you sure you want to remove this volume as a possible destination for this index?";
            if (selected.size() > 1) {
                message = "Are you sure you want to remove these volumes as possible destinations for this index?";
            }

            ConfirmEvent.fire(this,
                    message,
                    new ConfirmCallback() {
                        @Override
                        public void onResult(final boolean result) {
                            if (result) {
                                volumes.removeAll(selected);
                                selectionModel.clear();
                                DirtyEvent.fire(IndexVolumeListPresenter.this, true);
                                refresh();
                            }
                        }
                    });
        }
    }

    @Override
    public void read(final Index index) {
        volumes = new ArrayList<>();
        if (index != null) {
            if (index.getVolumes() != null) {
                volumes.addAll(index.getVolumes());
                sortVolumes();
            }
        }
        refresh();
    }

    private void sortVolumes() {
        Collections.sort(volumes, new Comparator<Volume>() {
            @Override
            public int compare(final Volume arg0, final Volume arg1) {
                return arg0.getPath().compareTo(arg1.getPath());
            }
        });
    }

    @Override
    public void write(final Index index) {
        final Set<Volume> set = new HashSet<>(volumes);
        index.setVolumes(set);
    }

    private void refresh() {
        volumeListPresenter.setData(volumes);
    }

    @Override
    public HandlerRegistration addDirtyHandler(final DirtyHandler handler) {
        return addHandlerToSource(DirtyEvent.getType(), handler);
    }
}
