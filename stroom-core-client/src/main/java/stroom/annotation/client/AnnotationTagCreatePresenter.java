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

package stroom.annotation.client;

import stroom.alert.client.event.AlertEvent;
import stroom.annotation.client.AnnotationTagCreatePresenter.AnnotationTagCreateView;
import stroom.annotation.shared.AnnotationTag;
import stroom.annotation.shared.AnnotationTagType;
import stroom.annotation.shared.CreateAnnotationTagRequest;
import stroom.dispatch.client.RestErrorHandler;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.Locale;
import java.util.function.Consumer;

public class AnnotationTagCreatePresenter
        extends MyPresenterWidget<AnnotationTagCreateView> {

    private final AnnotationResourceClient annotationResourceClient;
    private final Provider<AnnotationTagEditPresenter> editProvider;
    private AnnotationTagType annotationTagType;

    @Inject
    public AnnotationTagCreatePresenter(final EventBus eventBus,
                                        final AnnotationTagCreateView view,
                                        final AnnotationResourceClient annotationResourceClient,
                                        final Provider<AnnotationTagEditPresenter> editProvider) {
        super(eventBus, view);
        this.annotationResourceClient = annotationResourceClient;
        this.editProvider = editProvider;
    }

    void open(final AnnotationTagType annotationTagType,
              final Consumer<AnnotationTag> consumer) {
        this.annotationTagType = annotationTagType;

        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .caption("Create New " + annotationTagType.getDisplayValue())
                .onShow(e -> getView().focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        try {
                            final String name = getView().getName();
                            doWithNameValidation(name, () -> createTag(consumer, name, e), e);
                        } catch (final RuntimeException ex) {
                            AlertEvent.fireError(
                                    AnnotationTagCreatePresenter.this,
                                    ex.getMessage(),
                                    e::reset);
                        }
                    } else {
                        e.hide();
                    }
                })
                .fire();
    }

    private void doWithNameValidation(final String name,
                                      final Runnable work,
                                      final HidePopupRequestEvent event) {
        if (name == null || name.isEmpty()) {
            AlertEvent.fireError(
                    AnnotationTagCreatePresenter.this,
                    "You must provide a name for the annotation " +
                    annotationTagType.getDisplayValue().toLowerCase(
                            Locale.ROOT) +
                    ".",
                    event::reset);
        } else {
            work.run();
        }
    }

    private void createTag(final Consumer<AnnotationTag> consumer,
                           final String name,
                           final HidePopupRequestEvent event) {
        final CreateAnnotationTagRequest request = new CreateAnnotationTagRequest(annotationTagType, name);
        annotationResourceClient.createAnnotationTag(request,
                r -> {
                    editProvider.get().open(r, "Edit " + r.getName(), consumer);
                    event.hide();
                }, RestErrorHandler.forPopup(this, event), this);
    }

    // --------------------------------------------------------------------------------


    public interface AnnotationTagCreateView extends View, Focus {

        String getName();

        void setName(String name);
    }
}
