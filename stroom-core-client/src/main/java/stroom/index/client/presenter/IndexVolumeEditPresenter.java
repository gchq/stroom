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
import com.google.gwt.user.client.ui.HasText;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import stroom.alert.client.event.AlertEvent;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.index.client.presenter.IndexVolumeEditPresenter.IndexVolumeEditView;
import stroom.index.shared.IndexVolume;
import stroom.index.shared.IndexVolume.VolumeUseState;
import stroom.index.shared.IndexVolumeResource;
import stroom.item.client.ItemListBox;
import stroom.node.client.NodeCache;
import stroom.util.shared.ModelStringUtil;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.DefaultPopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;
import stroom.widget.tab.client.event.CloseEvent;

import java.util.List;

public class IndexVolumeEditPresenter extends MyPresenterWidget<IndexVolumeEditView> {
    private static final IndexVolumeResource INDEX_VOLUME_RESOURCE = GWT.create(IndexVolumeResource.class);

    private final PopupSize popupSize = new PopupSize(400, 197, 400, 197, 1000, 197, true);
    private final RestFactory restFactory;
    private final NodeCache nodeCache;
    private IndexVolume volume;
    private boolean opening;

    @Inject
    public IndexVolumeEditPresenter(final EventBus eventBus,
                                    final IndexVolumeEditView view,
                                    final RestFactory restFactory,
                                    final NodeCache nodeCache) {
        super(eventBus, view);
        this.restFactory = restFactory;
        this.nodeCache = nodeCache;
    }

    public void addVolume(final IndexVolume volume, final PopupUiHandlers popupUiHandlers) {
        read(volume, "Add Volume", popupUiHandlers);
    }

    public void editVolume(final IndexVolume volume, final PopupUiHandlers popupUiHandlers) {
        read(volume, "Edit Volume", popupUiHandlers);
    }

    private void read(final IndexVolume volume, final String title, final PopupUiHandlers popupUiHandlers) {
        if (!opening) {
            opening = true;
            nodeCache.listEnabledNodes(
                    nodeNames -> read(nodeNames, volume, title, popupUiHandlers),
                    throwable -> {
                    });
        }
    }

    private void read(final List<String> nodeNames, final IndexVolume volume, final String title, final PopupUiHandlers popupUiHandlers) {
        this.volume = volume;

        getView().setNodeNames(nodeNames);
        getView().getNodeName().setText(volume.getNodeName());
        getView().getPath().setText(volume.getPath());
        getView().getState().addItems(VolumeUseState.values());
        getView().getState().setSelectedItem(volume.getState());

        if (volume.getBytesLimit() != null) {
            getView().getByteLimit().setText(ModelStringUtil.formatIECByteSizeString(volume.getBytesLimit()));
        } else {
            getView().getByteLimit().setText("");
        }

        opening = false;
        ShowPopupEvent.fire(this, this, PopupType.OK_CANCEL_DIALOG, popupSize, title,
                new DelegatePopupUiHandlers(popupUiHandlers));
    }

    private void write() {
        try {
            volume.setNodeName(getView().getNodeName().getText());
            volume.setPath(getView().getPath().getText());
            volume.setState(getView().getState().getSelectedItem());

            Long bytesLimit = null;
            final String limit = getView().getByteLimit().getText().trim();
            if (limit.length() > 0) {
                bytesLimit = ModelStringUtil.parseIECByteSizeString(limit);
            }
            volume.setBytesLimit(bytesLimit);

            final Rest<IndexVolume> rest = restFactory.create();
            rest.onSuccess(result -> {
                volume = result;
                HidePopupEvent.fire(IndexVolumeEditPresenter.this, IndexVolumeEditPresenter.this, false, true);
                // Only fire this event here as the parent only
                // needs to
                // refresh if there has been a change.
                CloseEvent.fire(IndexVolumeEditPresenter.this);
            }).call(INDEX_VOLUME_RESOURCE).update(volume.getId(), volume);

        } catch (final RuntimeException e) {
            AlertEvent.fireError(this, e.getMessage(), null);
        }
    }

    public interface IndexVolumeEditView extends View {
        void setNodeNames(List<String> nodeNames);

        HasText getNodeName();

        HasText getPath();

        ItemListBox<VolumeUseState> getState();

        HasText getByteLimit();
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
                HidePopupEvent.fire(IndexVolumeEditPresenter.this, IndexVolumeEditPresenter.this, autoClose, ok);
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
