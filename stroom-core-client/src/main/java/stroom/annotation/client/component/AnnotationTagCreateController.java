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
import stroom.annotation.shared.CreateAnnotationTagRequest;
import stroom.dispatch.client.RestErrorHandler;
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

/// Creates an annotation tag ([AnnotationTagCreate]) in a dialog made for one use, then opens the
/// new tag in its editor.
public class AnnotationTagCreateController extends Controller {

    private final AnnotationTagCreate view;
    private final AnnotationResourceClient annotationResourceClient;
    private final Provider<AnnotationTagEditController> editProvider;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus                 The application's event bus.
    /// @param view                     The content.
    /// @param annotationResourceClient Creates the tag.
    /// @param editProvider             Makes the editor the new tag is opened in.
    @Inject
    public AnnotationTagCreateController(final EventBus eventBus,
                                         final AnnotationTagCreate view,
                                         final AnnotationResourceClient annotationResourceClient,
                                         final Provider<AnnotationTagEditController> editProvider) {
        super(eventBus);
        this.view = view;
        this.annotationResourceClient = annotationResourceClient;
        this.editProvider = editProvider;
        initWidget(view);
    }

    /// Shows the dialog, owned by `opener` until it is closed. OK creates the tag and opens it in
    /// its editor, owned by `opener` too.
    ///
    /// @param opener            What shows it.
    /// @param annotationTagType The type of tag to create.
    /// @param consumer          Given the tag once its editor has saved it.
    public void open(final HasControllerNode opener,
                     final AnnotationTagType annotationTagType,
                     final Consumer<AnnotationTag> consumer) {
        view.showTagText(annotationTagType.hasTagText(), annotationTagType.getDisplayValue());

        final ShowPopupEvent.Builder builder = ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG);
        if (annotationTagType.hasTagText()) {
            builder.popupSize(PopupSize.resizable(630, 400));
        }
        builder
                .caption("Create New " + annotationTagType.getDisplayValue())
                .onShow(e -> view.focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        try {
                            final String name = view.getName();
                            doWithNameValidation(annotationTagType, name, () ->
                                    createTag(opener, annotationTagType, consumer, name, e), e);
                        } catch (final RuntimeException ex) {
                            AlertEvent.fireError(this, ex.getMessage(), e::reset);
                        }
                    } else {
                        e.hide();
                    }
                })
                .fire();
    }

    private void doWithNameValidation(final AnnotationTagType annotationTagType,
                                      final String name,
                                      final Runnable work,
                                      final HidePopupRequestEvent event) {
        if (name == null || name.isEmpty()) {
            AlertEvent.fireError(this,
                    "You must provide a name for the annotation "
                    + annotationTagType.getDisplayValue().toLowerCase(Locale.ROOT)
                    + ".",
                    event::reset);
        } else {
            work.run();
        }
    }

    private void createTag(final HasControllerNode opener,
                           final AnnotationTagType annotationTagType,
                           final Consumer<AnnotationTag> consumer,
                           final String name,
                           final HidePopupRequestEvent event) {
        final CreateAnnotationTagRequest request = new CreateAnnotationTagRequest(
                annotationTagType,
                name,
                annotationTagType.hasTagText()
                        ? view.getTagText()
                        : null);
        annotationResourceClient.createAnnotationTag(request, guard(created -> {
            // The editor outlives this dialog, so is the opener's
            editProvider.get().open(opener, created, "Edit " + created.getName(), consumer);
            event.hide();
        }), RestErrorHandler.forPopup(this, event), taskMonitor());
    }

}
