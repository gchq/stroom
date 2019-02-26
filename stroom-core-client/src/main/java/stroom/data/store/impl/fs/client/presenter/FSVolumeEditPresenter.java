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

import com.google.gwt.user.client.ui.HasText;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import stroom.alert.client.event.AlertEvent;
import stroom.data.store.impl.fs.shared.FsVolume;
import stroom.data.store.impl.fs.shared.FsVolume.VolumeUseStatus;
import stroom.data.store.impl.fs.shared.UpdateFsVolumeAction;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.entity.shared.EntityServiceFindAction;
import stroom.item.client.ItemListBox;
import stroom.node.shared.FindNodeCriteria;
import stroom.node.shared.Node;
import stroom.util.shared.ModelStringUtil;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.DefaultPopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;
import stroom.widget.tab.client.event.CloseEvent;

public class FSVolumeEditPresenter extends MyPresenterWidget<FSVolumeEditPresenter.VolumeEditView> {
    private final PopupSize popupSize = new PopupSize(400, 197, 400, 197, 1000, 197, true);
    private final ClientDispatchAsync clientDispatchAsync;
    private FsVolume volume;
    private boolean opening;

    @Inject
    public FSVolumeEditPresenter(final EventBus eventBus, final VolumeEditView view,
                                 final ClientDispatchAsync clientDispatchAsync) {
        super(eventBus, view);
        this.clientDispatchAsync = clientDispatchAsync;
    }

    public void addVolume(final FsVolume volume, final PopupUiHandlers popupUiHandlers) {
        read(volume, "Add Volume", popupUiHandlers);
    }

    public void editVolume(final FsVolume volume, final PopupUiHandlers popupUiHandlers) {
        read(volume, "Edit Volume", popupUiHandlers);
    }

    private void read(final FsVolume volume, final String title, final PopupUiHandlers popupUiHandlers) {
        if (!opening) {
            opening = true;

            this.volume = volume;
            clientDispatchAsync.exec(new EntityServiceFindAction<FindNodeCriteria, Node>(new FindNodeCriteria())).onSuccess(result -> {
                getView().getPath().setText(volume.getPath());
                getView().getStatus().addItems(VolumeUseStatus.values());
                getView().getStatus().setSelectedItem(volume.getStatus());

                if (volume.getByteLimit() != null) {
                    getView().getByteLimit().setText(ModelStringUtil.formatIECByteSizeString(volume.getByteLimit()));
                } else {
                    getView().getByteLimit().setText("");
                }

                opening = false;
                ShowPopupEvent.fire(this, this, PopupType.OK_CANCEL_DIALOG, popupSize, title,
                        new DelegatePopupUiHandlers(popupUiHandlers));
            });
        }
    }

    private void write() {
        try {
            volume.setPath(getView().getPath().getText());
            volume.setStatus(getView().getStatus().getSelectedItem());

            Long bytesLimit = null;
            final String limit = getView().getByteLimit().getText().trim();
            if (limit.length() > 0) {
                bytesLimit = ModelStringUtil.parseIECByteSizeString(limit);
            }
            volume.setByteLimit(bytesLimit);

            clientDispatchAsync.exec(new UpdateFsVolumeAction(volume)).onSuccess(result -> {
                volume = result;
                HidePopupEvent.fire(FSVolumeEditPresenter.this, FSVolumeEditPresenter.this, false, true);
                // Only fire this event here as the parent only
                // needs to
                // refresh if there has been a change.
                CloseEvent.fire(FSVolumeEditPresenter.this);
            });

        } catch (final RuntimeException e) {
            AlertEvent.fireError(this, e.getMessage(), null);
        }
    }

    public interface VolumeEditView extends View {
        HasText getPath();

        ItemListBox<VolumeUseStatus> getStatus();

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
                HidePopupEvent.fire(FSVolumeEditPresenter.this, FSVolumeEditPresenter.this, autoClose, ok);
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
