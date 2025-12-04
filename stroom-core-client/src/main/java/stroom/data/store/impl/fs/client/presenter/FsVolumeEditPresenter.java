/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.data.store.impl.fs.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.data.store.impl.fs.client.presenter.FsVolumeEditPresenter.FsVolumeEditView;
import stroom.data.store.impl.fs.shared.FsVolume;
import stroom.data.store.impl.fs.shared.FsVolume.VolumeUseStatus;
import stroom.data.store.impl.fs.shared.FsVolumeResource;
import stroom.data.store.impl.fs.shared.FsVolumeType;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.editor.client.presenter.EditorPresenter;
import stroom.item.client.SelectionBox;
import stroom.util.shared.ModelStringUtil;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Focus;
import com.google.gwt.user.client.ui.HasText;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;

import java.util.function.Consumer;

public class FsVolumeEditPresenter
        extends MyPresenterWidget<FsVolumeEditView> {

    private static final FsVolumeResource FS_VOLUME_RESOURCE = GWT.create(FsVolumeResource.class);

    private final EditorPresenter editorPresenter;
    private final RestFactory restFactory;
    private FsVolume volume;

    @Inject
    public FsVolumeEditPresenter(final EventBus eventBus,
                                 final FsVolumeEditView view,
                                 final EditorPresenter editorPresenter,
                                 final RestFactory restFactory) {
        super(eventBus, view);
        this.editorPresenter = editorPresenter;
        this.restFactory = restFactory;

        editorPresenter.setMode(AceEditorMode.JSON);
        view.setConfigView(editorPresenter.getView());
    }

    void show(final FsVolume volume, final String title, final Consumer<FsVolume> consumer) {
        read(volume);

        final PopupSize popupSize = PopupSize.resizable(650, 650, 400, 500);
        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(popupSize)
                .caption(title)
                .onShow(e -> getView().focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        write();
                        try {
                            if (volume.getId() == null) {
                                doWithVolumeValidation(volume, () -> createVolume(consumer, volume, e), e);
                            } else {
                                doWithVolumeValidation(volume, () -> updateVolume(consumer, volume, e), e);
                            }
                        } catch (final RuntimeException ex) {
                            AlertEvent.fireError(FsVolumeEditPresenter.this, ex.getMessage(), e::reset);
                        }
                    } else {
                        consumer.accept(null);
                        e.hide();
                    }
                })
                .fire();
    }

    private void doWithVolumeValidation(final FsVolume volume,
                                        final Runnable work,
                                        final HidePopupRequestEvent event) {
        restFactory
                .create(FS_VOLUME_RESOURCE)
                .method(res -> res.validate(volume))
                .onSuccess(validationResult -> {
                    if (validationResult.isOk()) {
                        work.run();
                    } else if (validationResult.isWarning()) {
                        ConfirmEvent.fireWarn(
                                FsVolumeEditPresenter.this,
                                validationResult.getMessage(),
                                confirmOk -> {
                                    if (confirmOk) {
                                        work.run();
                                    } else {
                                        event.reset();
                                    }
                                });
                    } else {
                        AlertEvent.fireError(
                                FsVolumeEditPresenter.this,
                                validationResult.getMessage(),
                                event::reset);
                    }
                })
                .onFailure(RestErrorHandler.forPopup(this, event))
                .taskMonitorFactory(this)
                .exec();
    }

    private void updateVolume(final Consumer<FsVolume> consumer,
                              final FsVolume volume,
                              final HidePopupRequestEvent event) {
        restFactory
                .create(FS_VOLUME_RESOURCE)
                .method(res -> res.update(volume.getId(), volume))
                .onSuccess(r -> {
                    consumer.accept(r);
                    event.hide();
                })
                .onFailure(RestErrorHandler.forPopup(this, event))
                .taskMonitorFactory(this)
                .exec();
    }

    private void createVolume(final Consumer<FsVolume> consumer,
                              final FsVolume volume,
                              final HidePopupRequestEvent event) {
        restFactory
                .create(FS_VOLUME_RESOURCE)
                .method(res -> res.create(volume))
                .onSuccess(r -> {
                    consumer.accept(r);
                    event.hide();
                })
                .onFailure(RestErrorHandler.forPopup(this, event))
                .taskMonitorFactory(this)
                .exec();
    }

    private void read(final FsVolume volume) {
        this.volume = volume;

        getView().getVolumeType().addItems(FsVolumeType.values());
        getView().getVolumeType().setValue(volume.getVolumeType());
        getView().getPath().setText(volume.getPath());
        getView().getVolumeStatus().addItems(VolumeUseStatus.values());
        getView().getVolumeStatus().setValue(volume.getStatus());
        editorPresenter.setText(volume.getS3ClientConfigData());

        if (volume.getByteLimit() != null) {
            getView().getByteLimit().setText(ModelStringUtil.formatIECByteSizeString(
                    volume.getByteLimit(),
                    true,
                    ModelStringUtil.DEFAULT_SIGNIFICANT_FIGURES));
        } else {
            getView().getByteLimit().setText("");
        }
    }

    private void write() {
        volume.setVolumeType(getView().getVolumeType().getValue());
        volume.setPath(getView().getPath().getText());
        volume.setStatus(getView().getVolumeStatus().getValue());
        volume.setS3ClientConfigData(editorPresenter.getText());

        Long bytesLimit = null;
        final String limit = getView().getByteLimit().getText().trim();
        if (limit.length() > 0) {
            bytesLimit = ModelStringUtil.parseIECByteSizeString(limit);
        }
        volume.setByteLimit(bytesLimit);
    }

    public interface FsVolumeEditView extends View, Focus {

        SelectionBox<FsVolumeType> getVolumeType();

        HasText getPath();

        SelectionBox<VolumeUseStatus> getVolumeStatus();

        HasText getByteLimit();

        void setConfigView(View view);
    }
}
