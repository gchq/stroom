/*
 * Copyright 2025 Crown Copyright
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

package stroom.annotation.client.component;

import stroom.alert.client.event.AlertEvent;
import stroom.annotation.client.AnnotationResourceClient;
import stroom.annotation.shared.AnnotationTag;
import stroom.annotation.shared.AnnotationTagType;
import stroom.dispatch.client.RestErrorHandler;
import stroom.docref.DocRef;
import stroom.security.client.component.DocumentUserPermissionsTabController;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.PopupSize;
import stroom.widget.popup.client.PopupType;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Locale;
import java.util.function.Consumer;

/// Edits an annotation tag ([AnnotationTagEditor]) in a dialog made for one use, saving it on OK.
/// Change Permissions opens the tag's permissions in a dialog of their own.
public class AnnotationTagEditorController extends Controller {

    private final AnnotationTagEditor view;
    private final AnnotationResourceClient annotationResourceClient;
    private final Provider<DocumentUserPermissionsTabController> permissionsProvider;
    private AnnotationTag annotationTag;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus                 The application's event bus.
    /// @param view                     The content.
    /// @param annotationResourceClient Saves the tag.
    /// @param permissionsProvider      Makes the tag's permissions dialog.
    @Inject
    public AnnotationTagEditorController(final EventBus eventBus,
                                         final AnnotationTagEditor view,
                                         final AnnotationResourceClient annotationResourceClient,
                                         final Provider<DocumentUserPermissionsTabController>
                                               permissionsProvider) {
        super(eventBus);
        this.view = view;
        this.annotationResourceClient = annotationResourceClient;
        this.permissionsProvider = permissionsProvider;
        initWidget(view);
        view.setOnPermissions(this::onPermissions);
    }

    /// Shows a tag in a dialog, owned by `opener` until it is closed.
    ///
    /// @param opener        What shows it.
    /// @param annotationTag The tag.
    /// @param title         The dialog's caption.
    /// @param consumer      Given the saved tag once OK has saved it.
    public void open(final HasControllerNode opener,
                     final AnnotationTag annotationTag,
                     final String title,
                     final Consumer<AnnotationTag> consumer) {
        read(annotationTag);

        final ShowPopupEvent.Builder builder = ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG);
        if (annotationTag.getType().hasTagText()) {
            builder.popupSize(PopupSize.resizable(630, 400));
        }
        builder
                .caption(title)
                .onShow(e -> view.focus())
                .onHideRequest(e -> onHideRequest(e, consumer))
                .fire();
    }

    // Package-private for the tests
    void read(final AnnotationTag annotationTag) {
        this.annotationTag = annotationTag;
        view.setName(annotationTag.getName());
        view.showTagText(annotationTag.getType().hasTagText(), annotationTag.getType().getDisplayValue());
        view.setTagText(annotationTag.getTagText());
        view.setStyle(annotationTag.getStyle());
        view.showStyle(AnnotationTagType.LABEL.equals(annotationTag.getType()));
    }

    // Package-private for the tests
    void onHideRequest(final HidePopupRequestEvent e, final Consumer<AnnotationTag> consumer) {
        if (e.isOk()) {
            annotationTag = annotationTag
                    .copy()
                    .name(view.getName())
                    .tagText(annotationTag.getType().hasTagText()
                            ? view.getTagText()
                            : null)
                    .style(view.getStyle())
                    .build();
            try {
                doWithNameValidation(view.getName(), () -> updateTag(consumer, annotationTag, e), e);
            } catch (final RuntimeException ex) {
                AlertEvent.fireError(this, ex.getMessage(), e::reset);
            }
        } else {
            e.hide();
        }
    }

    private void doWithNameValidation(final String name,
                                      final Runnable work,
                                      final HidePopupRequestEvent event) {
        if (name == null || name.isEmpty()) {
            AlertEvent.fireError(this,
                    "You must provide a name for the annotation "
                    + annotationTag.getType().getDisplayValue().toLowerCase(Locale.ROOT)
                    + ".",
                    event::reset);
        } else {
            work.run();
        }
    }

    private void updateTag(final Consumer<AnnotationTag> consumer,
                           final AnnotationTag tag,
                           final HidePopupRequestEvent event) {
        annotationResourceClient.updateAnnotationTag(tag, guard(saved -> {
            consumer.accept(saved);
            event.hide();
        }), RestErrorHandler.forPopup(this, event), taskMonitor());
    }

    private void onPermissions() {
        // Made for one use, and disposed once it is closed
        final DocumentUserPermissionsTabController permissions = permissionsProvider.get();
        permissions.setDocRef(new DocRef(AnnotationTag.TYPE, annotationTag.getUuid(), annotationTag.getName()));
        ControllerDialog.showOnce(this, permissions)
                .popupType(PopupType.CLOSE_DIALOG)
                .modal()
                .popupSize(PopupSize.resizable(800, 800))
                .caption("Change Permissions")
                .onShow(e -> view.focus())
                .onHideRequest(HidePopupRequestEvent::hide)
                .fire();
    }

}
