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

package stroom.index.client.component;

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.index.shared.IndexVolume;
import stroom.index.shared.IndexVolumeResource;
import stroom.node.client.NodeClient;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.shared.ModelStringUtil;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.List;
import java.util.function.Consumer;

/// Adds or edits an index volume ([IndexVolumeEdit]) in a dialog made for one use, validating it
/// on OK, and asking first if the server warns about it.
public class IndexVolumeEditController extends Controller {

    private static final IndexVolumeResource INDEX_VOLUME_RESOURCE = GWT.create(IndexVolumeResource.class);

    private final IndexVolumeEdit view;
    private final RestFactory restFactory;
    private final NodeClient nodeClient;

    /// Made by GIN, with its form.
    ///
    /// @param eventBus    The application's event bus.
    /// @param view        The form.
    /// @param restFactory Validates and saves the volume.
    /// @param nodeClient  Lists the nodes.
    @Inject
    public IndexVolumeEditController(final EventBus eventBus,
                                     final IndexVolumeEdit view,
                                     final RestFactory restFactory,
                                     final NodeClient nodeClient) {
        super(eventBus);
        this.view = view;
        this.restFactory = restFactory;
        this.nodeClient = nodeClient;
        initWidget(view);
    }

    /// Fetches the nodes, then shows a volume in a dialog, owned by `opener` until it is closed.
    ///
    /// @param opener             What shows it.
    /// @param volume             The volume, new (no id) or saved.
    /// @param caption            The dialog's caption.
    /// @param consumer           Given the saved volume on OK.
    /// @param taskMonitorFactory Shows the requests as busy.
    public void show(final HasControllerNode opener,
                     final IndexVolume volume,
                     final String caption,
                     final Consumer<IndexVolume> consumer,
                     final TaskMonitorFactory taskMonitorFactory) {
        nodeClient.listAllNodes(
                guard(nodeNames -> {
                    read(nodeNames, volume);
                    ControllerDialog.showOnce(opener, this)
                            .popupType(PopupType.OK_CANCEL_DIALOG)
                            .popupSize(PopupSize.resizableX())
                            .caption(caption)
                            .onShow(e -> view.focus())
                            .onHideRequest(e -> onHideRequest(e, volume, consumer, taskMonitorFactory))
                            .fire();
                }),
                // Not shown if the nodes can't be listed, so disposed then
                throwable -> AlertEvent.fireError(this, throwable.getMessage(), this::dispose),
                taskMonitorFactory);
    }

    // Package-private for the tests
    void onHideRequest(final HidePopupRequestEvent e,
                       final IndexVolume volume,
                       final Consumer<IndexVolume> consumer,
                       final TaskMonitorFactory taskMonitorFactory) {
        if (e.isOk()) {
            try {
                final IndexVolume updated = write(volume);
                if (updated.getId() != null) {
                    doWithVolumeValidation(updated, () ->
                            updateVolume(consumer, updated, e, taskMonitorFactory), e, taskMonitorFactory);
                } else {
                    doWithVolumeValidation(updated, () ->
                            createIndexVolume(consumer, updated, e, taskMonitorFactory), e, taskMonitorFactory);
                }
            } catch (final RuntimeException ex) {
                AlertEvent.fireError(this, ex.getMessage(), e::reset);
            }
        } else {
            e.hide();
        }
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
                        work.run();
                    } else if (validationResult.isWarning()) {
                        ConfirmEvent.fireWarn(this, validationResult.getMessage(), confirmOk -> {
                            if (confirmOk) {
                                work.run();
                            } else {
                                // Let the user change the volume or cancel
                                event.reset();
                            }
                        });
                    } else {
                        AlertEvent.fireError(this, validationResult.getMessage(), event::reset);
                    }
                })
                .onFailure(throwable -> AlertEvent.fireError(this, throwable.getMessage(), event::reset))
                .owner(this)
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
                .owner(this)
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
                .owner(this)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    private void read(final List<String> nodeNames, final IndexVolume volume) {
        view.setNodeNames(nodeNames);
        view.setNodeName(volume.getNodeName());
        view.setPath(volume.getPath());
        view.setState(volume.getState());
        view.setByteLimit(volume.getBytesLimit() != null
                ? ModelStringUtil.formatIECByteSizeString(
                        volume.getBytesLimit(),
                        true,
                        ModelStringUtil.DEFAULT_SIGNIFICANT_FIGURES)
                : "");
    }

    private IndexVolume write(final IndexVolume volume) {
        Long bytesLimit = null;
        final String limit = view.getByteLimit().trim();
        if (!limit.isEmpty()) {
            bytesLimit = ModelStringUtil.parseIECByteSizeString(limit);
        }
        return volume
                .copy()
                .nodeName(view.getNodeName())
                .path(view.getPath())
                .state(view.getState())
                .bytesLimit(bytesLimit)
                .build();
    }
}
