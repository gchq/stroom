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

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import stroom.alert.client.event.ConfirmEvent;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.document.client.event.DirtyEvent;
import stroom.document.client.event.DirtyEvent.DirtyHandler;
import stroom.document.client.event.HasDirtyHandlers;
import stroom.entity.client.presenter.HasDocumentRead;
import stroom.entity.client.presenter.HasWrite;
import stroom.index.shared.FetchIndexVolumesAction;
import stroom.index.shared.IndexDoc;
import stroom.index.shared.SaveIndexVolumesAction;
import stroom.node.client.presenter.VolumeListPresenter;
import stroom.node.client.presenter.VolumeStatusListPresenter;
import stroom.node.client.view.WrapperView;
import stroom.node.shared.Volume;
import stroom.query.api.v2.DocRef;
import stroom.svg.client.SvgPresets;
import stroom.widget.button.client.ButtonView;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;
import stroom.widget.util.client.MultiSelectionModel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class IndexVolumeListPresenter extends MyPresenterWidget<WrapperView>
        implements HasDocumentRead<IndexDoc>, HasWrite<IndexDoc>, HasDirtyHandlers {
    private final VolumeListPresenter volumeListPresenter;
    private final VolumeStatusListPresenter volumeStatusListPresenter;
    private final ClientDispatchAsync dispatcher;
    private final ButtonView addButton;
    private final ButtonView removeButton;

    private DocRef docRef;
    private List<Volume> volumes;

    @Inject
    public IndexVolumeListPresenter(final EventBus eventBus,
                                    final WrapperView view,
                                    final VolumeListPresenter volumeListPresenter,
                                    final VolumeStatusListPresenter volumeStatusListPresenter,
                                    final ClientDispatchAsync dispatcher) {
        super(eventBus, view);
        this.volumeListPresenter = volumeListPresenter;
        this.volumeStatusListPresenter = volumeStatusListPresenter;
        this.dispatcher = dispatcher;

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

        registerHandler(addButton.addClickHandler(this::onAdd));
        registerHandler(removeButton.addClickHandler(this::onRemove));
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
        final MultiSelectionModel<Volume> selectionModel = volumeStatusListPresenter.getSelectionModel();
        selectionModel.clear();
        ShowPopupEvent.fire(this, volumeStatusListPresenter, PopupType.OK_CANCEL_DIALOG, null, popupSize,
                "Add Volume To Index", popupUiHandlers, true);
    }

    private void addVolume(final boolean ok) {
        if (ok) {
            final MultiSelectionModel<Volume> selectionModel = volumeStatusListPresenter.getSelectionModel();
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

    private void onRemove(final ClickEvent event) {
        final MultiSelectionModel<Volume> selectionModel = volumeListPresenter.getSelectionModel();
        final List<Volume> selected = selectionModel.getSelectedItems();
        if (selected != null && selected.size() > 0) {
            String message = "Are you sure you want to remove this volume as a possible destination for this index?";
            if (selected.size() > 1) {
                message = "Are you sure you want to remove these volumes as possible destinations for this index?";
            }

            ConfirmEvent.fire(this,
                    message,
                    result -> {
                        if (result) {
                            volumes.removeAll(selected);
                            selectionModel.clear();
                            DirtyEvent.fire(IndexVolumeListPresenter.this, true);
                            refresh();
                        }
                    });
        }
    }

    @Override
    public void read(final DocRef docRef, final IndexDoc index) {
        this.docRef = docRef;
        volumes = new ArrayList<>();
        if (index != null) {
            dispatcher.exec(new FetchIndexVolumesAction(docRef)).onSuccess(result -> {
                volumes.addAll(result);
                sortVolumes();
            });
        }
        refresh();
    }

    private void sortVolumes() {
        volumes.sort(Comparator.comparing(Volume::getPath));
    }

    @Override
    public void write(final IndexDoc index) {
        final Set<Volume> set = new HashSet<>(volumes);
        dispatcher.exec(new SaveIndexVolumesAction(docRef, set));
    }

    private void refresh() {
        volumeListPresenter.setData(volumes);
    }

    @Override
    public HandlerRegistration addDirtyHandler(final DirtyHandler handler) {
        return addHandlerToSource(DirtyEvent.getType(), handler);
    }
}
