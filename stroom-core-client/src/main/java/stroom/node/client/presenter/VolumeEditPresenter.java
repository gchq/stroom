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

package stroom.node.client.presenter;

import com.google.gwt.user.client.ui.HasText;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import stroom.alert.client.event.AlertEvent;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.entity.shared.EntityServiceFindAction;
import stroom.entity.shared.EntityServiceSaveAction;
import stroom.item.client.ItemListBox;
import stroom.node.shared.FindNodeCriteria;
import stroom.node.shared.Node;
import stroom.node.shared.Volume;
import stroom.node.shared.Volume.VolumeType;
import stroom.node.shared.Volume.VolumeUseStatus;
import stroom.util.shared.ModelStringUtil;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.DefaultPopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;
import stroom.widget.tab.client.event.CloseEvent;

public class VolumeEditPresenter extends MyPresenterWidget<VolumeEditPresenter.VolumeEditView> {
    private final PopupSize popupSize = new PopupSize(400, 197, 400, 197, 1000, 197, true);
    private final ClientDispatchAsync clientDispatchAsync;
    private Volume volume;

    @Inject
    public VolumeEditPresenter(final EventBus eventBus, final VolumeEditView view,
                               final ClientDispatchAsync clientDispatchAsync) {
        super(eventBus, view);
        this.clientDispatchAsync = clientDispatchAsync;
    }

    public void addVolume(final Volume volume, final PopupUiHandlers popupUiHandlers) {
        this.volume = volume;
        read();

        ShowPopupEvent.fire(this, this, PopupType.OK_CANCEL_DIALOG, popupSize, "Add Volume",
                new DelegatePopupUiHandlers(popupUiHandlers));
    }

    public void editVolume(final Volume volume, final PopupUiHandlers popupUiHandlers) {
        this.volume = volume;
        read();

        ShowPopupEvent.fire(this, this, PopupType.OK_CANCEL_DIALOG, popupSize, "Edit Volume",
                new DelegatePopupUiHandlers(popupUiHandlers));
    }

    private void read() {
        clientDispatchAsync.exec(new EntityServiceFindAction<FindNodeCriteria, Node>(new FindNodeCriteria())).onSuccess(result -> {
            getView().getNode().addItems(result.getValues());
            getView().getNode().setSelectedItem(volume.getNode());
        });
        getView().getPath().setText(volume.getPath());
        getView().getVolumeType().addItems(VolumeType.values());
        getView().getVolumeType().setSelectedItem(volume.getVolumeType());
        getView().getStreamStatus().addItems(VolumeUseStatus.values());
        getView().getStreamStatus().setSelectedItem(volume.getStreamStatus());
        getView().getIndexStatus().addItems(VolumeUseStatus.values());
        getView().getIndexStatus().setSelectedItem(volume.getIndexStatus());

        if (volume.getBytesLimit() != null) {
            getView().getBytesLimit().setText(ModelStringUtil.formatIECByteSizeString(volume.getBytesLimit()));
        } else {
            getView().getBytesLimit().setText("");
        }
    }

    private void write() {
        try {
            volume.setNode(getView().getNode().getSelectedItem());
            volume.setPath(getView().getPath().getText());
            volume.setVolumeType(getView().getVolumeType().getSelectedItem());
            volume.setStreamStatus(getView().getStreamStatus().getSelectedItem());
            volume.setIndexStatus(getView().getIndexStatus().getSelectedItem());

            Long bytesLimit = null;
            final String limit = getView().getBytesLimit().getText().trim();
            if (limit.length() > 0) {
                bytesLimit = ModelStringUtil.parseIECByteSizeString(limit);
            }
            volume.setBytesLimit(bytesLimit);

            clientDispatchAsync.exec(new EntityServiceSaveAction<>(volume)).onSuccess(result -> {
                volume = result;
                HidePopupEvent.fire(VolumeEditPresenter.this, VolumeEditPresenter.this, false, true);
                // Only fire this event here as the parent only
                // needs to
                // refresh if there has been a change.
                CloseEvent.fire(VolumeEditPresenter.this);
            });

        } catch (final Exception e) {
            AlertEvent.fireError(this, e.getMessage(), null);
        }
    }

    public interface VolumeEditView extends View {
        ItemListBox<Node> getNode();

        HasText getPath();

        ItemListBox<VolumeType> getVolumeType();

        ItemListBox<VolumeUseStatus> getStreamStatus();

        ItemListBox<VolumeUseStatus> getIndexStatus();

        HasText getBytesLimit();
    }

    private class DelegatePopupUiHandlers extends DefaultPopupUiHandlers {
        private final PopupUiHandlers popupUiHandlers;

        public DelegatePopupUiHandlers(final PopupUiHandlers popupUiHandlers) {
            this.popupUiHandlers = popupUiHandlers;
        }

        @Override
        public void onHideRequest(final boolean autoClose, final boolean ok) {
            if (ok) {
                write();
            } else {
                HidePopupEvent.fire(VolumeEditPresenter.this, VolumeEditPresenter.this, autoClose, ok);
            }

            if (popupUiHandlers != null) {
                popupUiHandlers.onHideRequest(autoClose, ok);
            }
        }

        @Override
        public void onHide(final boolean autoClose, final boolean ok) {
            if (popupUiHandlers != null) {
                popupUiHandlers.onHide(autoClose, ok);
            }
        }
    }
}
