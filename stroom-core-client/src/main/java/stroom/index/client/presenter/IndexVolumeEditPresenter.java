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

package stroom.index.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.index.client.presenter.IndexVolumeEditPresenter.IndexVolumeEditView;
import stroom.index.shared.IndexVolume;
import stroom.index.shared.IndexVolume.VolumeUseState;
import stroom.index.shared.IndexVolumeResource;
import stroom.item.client.SelectionBox;
import stroom.node.client.NodeManager;
import stroom.task.client.TaskMonitorFactory;
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

import java.util.List;
import java.util.function.Consumer;

public class IndexVolumeEditPresenter extends MyPresenterWidget<IndexVolumeEditView> {

    private static final IndexVolumeResource INDEX_VOLUME_RESOURCE = GWT.create(IndexVolumeResource.class);

    private final RestFactory restFactory;
    private final NodeManager nodeManager;

    private IndexVolume volume;

    @Inject
    public IndexVolumeEditPresenter(final EventBus eventBus,
                                    final IndexVolumeEditView view,
                                    final RestFactory restFactory,
                                    final NodeManager nodeManager) {
        super(eventBus, view);
        this.restFactory = restFactory;
        this.nodeManager = nodeManager;
    }

    void show(final IndexVolume volume,
              final String caption,
              final Consumer<IndexVolume> consumer,
              final TaskMonitorFactory taskMonitorFactory) {
        nodeManager.listAllNodes(
                nodeNames -> {
                    read(nodeNames, volume);

                    final PopupSize popupSize = PopupSize.resizableX();
                    ShowPopupEvent.builder(this)
                            .popupType(PopupType.OK_CANCEL_DIALOG)
                            .popupSize(popupSize)
                            .caption(caption)
                            .onShow(e -> getView().focus())
                            .onHideRequest(e -> {
                                if (e.isOk()) {
                                    try {
                                        write();
                                        if (volume.getId() != null) {
                                            doWithVolumeValidation(volume, () ->
                                                            updateVolume(consumer, volume, e, taskMonitorFactory),
                                                    e, taskMonitorFactory);
                                        } else {
                                            doWithVolumeValidation(volume, () ->
                                                            createIndexVolume(consumer, volume, e, taskMonitorFactory),
                                                    e, taskMonitorFactory);
                                        }

                                    } catch (final RuntimeException ex) {
                                        AlertEvent.fireError(IndexVolumeEditPresenter.this,
                                                ex.getMessage(),
                                                e::reset);
                                    }
                                } else {
                                    e.hide();
                                }
                            })
                            .fire();
                },
                throwable -> AlertEvent.fireError(IndexVolumeEditPresenter.this, throwable.getMessage(), null),
                taskMonitorFactory);
    }

    private void doWithVolumeValidation(final IndexVolume volume,
                                        final Runnable work,
                                        final HidePopupRequestEvent event,
                                        final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(INDEX_VOLUME_RESOURCE)
                .method(res -> res.validate(volume))
                .onSuccess(validationResult -> {
                    if (validationResult.isOk()) {
                        if (work != null) {
                            work.run();
                        }
                    } else if (validationResult.isWarning()) {
                        ConfirmEvent.fireWarn(
                                IndexVolumeEditPresenter.this,
                                validationResult.getMessage(),
                                confirmOk -> {
                                    if (confirmOk) {
                                        if (work != null) {
                                            work.run();
                                        }
                                    }
                                });
                    } else {
                        AlertEvent.fireError(
                                IndexVolumeEditPresenter.this,
                                validationResult.getMessage(),
                                event::reset);
                    }
                })
                .onFailure(throwable -> {
                    AlertEvent.fireError(IndexVolumeEditPresenter.this, throwable.getMessage(), event::reset);
                })
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    private void createIndexVolume(final Consumer<IndexVolume> savedVolumeConsumer,
                                   final IndexVolume volume,
                                   final HidePopupRequestEvent event,
                                   final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(INDEX_VOLUME_RESOURCE)
                .method(res -> res.create(volume))
                .onSuccess(r -> {
                    savedVolumeConsumer.accept(r);
                    event.hide();
                })
                .onFailure(RestErrorHandler.forPopup(this, event))
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    private void updateVolume(final Consumer<IndexVolume> consumer,
                              final IndexVolume volume,
                              final HidePopupRequestEvent event,
                              final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(INDEX_VOLUME_RESOURCE)
                .method(res -> res.update(volume.getId(), volume))
                .onSuccess(r -> {
                    consumer.accept(r);
                    event.hide();
                })
                .onFailure(RestErrorHandler.forPopup(this, event))
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    private void read(final List<String> nodeNames, final IndexVolume volume) {
        this.volume = volume;

        getView().setNodeNames(nodeNames);
        getView().getNodeName().setText(volume.getNodeName());
        getView().getPath().setText(volume.getPath());
        getView().getState().addItems(VolumeUseState.values());
        getView().getState().setValue(volume.getState());

        if (volume.getBytesLimit() != null) {
            getView().getByteLimit().setText(ModelStringUtil.formatIECByteSizeString(
                    volume.getBytesLimit(),
                    true,
                    ModelStringUtil.DEFAULT_SIGNIFICANT_FIGURES));
        } else {
            getView().getByteLimit().setText("");
        }
    }

    private void write() {
        volume.setNodeName(getView().getNodeName().getText());
        volume.setPath(getView().getPath().getText());
        volume.setState(getView().getState().getValue());

        Long bytesLimit = null;
        final String limit = getView().getByteLimit().getText().trim();
        if (limit.length() > 0) {
            bytesLimit = ModelStringUtil.parseIECByteSizeString(limit);
        }
        volume.setBytesLimit(bytesLimit);
    }

    public interface IndexVolumeEditView extends View, Focus {

        void setNodeNames(List<String> nodeNames);

        HasText getNodeName();

        HasText getPath();

        SelectionBox<VolumeUseState> getState();

        HasText getByteLimit();
    }
}
