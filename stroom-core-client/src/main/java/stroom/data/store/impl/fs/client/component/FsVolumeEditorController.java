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

package stroom.data.store.impl.fs.client.component;

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.data.store.impl.fs.shared.FsVolume;
import stroom.data.store.impl.fs.shared.FsVolumeResource;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.editor.client.component.CodeEditorController;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.NullSafe;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.PopupSize;
import stroom.widget.popup.client.PopupType;
import stroom.widget.popup.client.event.HidePopupRequestEvent;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;

import java.util.function.Consumer;

/// Adds or edits a data volume ([FsVolumeEditor]) in a dialog made for one use, validating it on
/// OK, and asking first if the server warns about it.
public class FsVolumeEditorController extends Controller {

    private static final FsVolumeResource FS_VOLUME_RESOURCE = GWT.create(FsVolumeResource.class);

    private final FsVolumeEditor view;
    private final CodeEditorController configEditor;
    private final RestFactory restFactory;

    /// Made by GIN, with its form.
    ///
    /// @param eventBus           The application's event bus.
    /// @param view               The form.
    /// @param codeEditorProvider Makes the controller of the S3 configuration's editor.
    /// @param restFactory        Validates and saves the volume.
    @Inject
    public FsVolumeEditorController(final EventBus eventBus,
                                    final FsVolumeEditor view,
                                    final Provider<CodeEditorController> codeEditorProvider,
                                    final RestFactory restFactory) {
        super(eventBus);
        this.view = view;
        this.restFactory = restFactory;
        initWidget(view);
        configEditor = make(codeEditorProvider).attach(view.getConfigEditor());
        configEditor.setMode(AceEditorMode.JSON);
    }

    /// Shows a volume in a dialog, owned by `opener` until it is closed.
    ///
    /// @param opener   What shows it.
    /// @param volume   The volume, new (no id) or saved.
    /// @param title    The dialog's caption.
    /// @param consumer Given the saved volume on OK, or `null` on Cancel.
    public void show(final HasControllerNode opener,
                     final FsVolume volume,
                     final String title,
                     final Consumer<FsVolume> consumer) {
        read(volume);

        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.resizable(650, 650, 400, 500))
                .caption(title)
                .onShow(ignored -> view.focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        try {
                            final FsVolume updated = write(volume);
                            if (updated.getId() == null) {
                                doWithVolumeValidation(updated, () -> createVolume(consumer, updated, e), e);
                            } else {
                                doWithVolumeValidation(updated, () -> updateVolume(consumer, updated, e), e);
                            }
                        } catch (final RuntimeException ex) {
                            AlertEvent.fireError(this, ex.getMessage(), e::reset);
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
                        ConfirmEvent.fireWarn(this, validationResult.getMessage(), confirmOk -> {
                            if (confirmOk) {
                                work.run();
                            } else {
                                event.reset();
                            }
                        });
                    } else {
                        AlertEvent.fireError(this, validationResult.getMessage(), event::reset);
                    }
                })
                .onFailure(RestErrorHandler.forPopup(this, event))
                .owner(this)
                .taskMonitorFactory(taskMonitor())
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
                .owner(this)
                .taskMonitorFactory(taskMonitor())
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
                .owner(this)
                .taskMonitorFactory(taskMonitor())
                .exec();
    }

    private void read(final FsVolume volume) {
        view.setVolumeType(volume.getVolumeType());
        view.setPath(volume.getPath());
        view.setStatus(volume.getStatus());
        configEditor.setText(volume.getS3ClientConfigData());
        view.setByteLimit(volume.getByteLimit() != null
                ? ModelStringUtil.formatIECByteSizeString(
                        volume.getByteLimit(),
                        true,
                        ModelStringUtil.DEFAULT_SIGNIFICANT_FIGURES)
                : "");
    }

    private FsVolume write(final FsVolume volume) {
        Long bytesLimit = null;
        final String limit = view.getByteLimit().trim();
        if (NullSafe.isNonEmptyString(limit)) {
            bytesLimit = ModelStringUtil.parseIECByteSizeString(limit);
        }
        return volume
                .copy()
                .volumeType(view.getVolumeType())
                .path(view.getPath())
                .status(view.getStatus())
                .s3ClientConfigData(configEditor.getText())
                .byteLimit(bytesLimit)
                .build();
    }

}
