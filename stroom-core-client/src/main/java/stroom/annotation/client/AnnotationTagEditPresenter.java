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
import stroom.annotation.client.AnnotationTagEditPresenter.AnnotationTagEditView;
import stroom.annotation.shared.AnnotationTag;
import stroom.annotation.shared.AnnotationTagType;
import stroom.dispatch.client.RestErrorHandler;
import stroom.docref.DocRef;
import stroom.query.api.ConditionalFormattingStyle;
import stroom.security.client.presenter.DocumentUserPermissionsPresenter;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.function.Consumer;

public class AnnotationTagEditPresenter
        extends MyPresenterWidget<AnnotationTagEditView>
        implements AnnotationTagEditUiHandlers {

    private final AnnotationResourceClient annotationResourceClient;
    private final Provider<DocumentUserPermissionsPresenter> documentUserPermissionsPresenterProvider;
    private AnnotationTag annotationTag;

    @Inject
    public AnnotationTagEditPresenter(final EventBus eventBus,
                                      final AnnotationTagEditView view,
                                      final AnnotationResourceClient annotationResourceClient,
                                      final Provider<DocumentUserPermissionsPresenter>
                                              documentUserPermissionsPresenterProvider) {
        super(eventBus, view);
        view.setUiHandlers(this);
        this.annotationResourceClient = annotationResourceClient;
        this.documentUserPermissionsPresenterProvider = documentUserPermissionsPresenterProvider;
    }

    void open(final AnnotationTag annotationTag,
              final String title,
              final Consumer<AnnotationTag> consumer) {
        this.annotationTag = annotationTag;
        getView().setName(annotationTag.getName());
        getView().setStyle(annotationTag.getStyle());
        getView().showStyle(AnnotationTagType.LABEL.equals(annotationTag.getType()));

        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .caption(title)
                .onShow(e -> getView().focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        this.annotationTag = this.annotationTag
                                .copy()
                                .name(getView().getName())
                                .style(getView().getStyle())
                                .build();
                        try {
                            doWithNameValidation(getView().getName(), () ->
                                    updateTag(consumer, this.annotationTag, e), e);
                        } catch (final RuntimeException ex) {
                            AlertEvent.fireError(
                                    AnnotationTagEditPresenter.this,
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
                    AnnotationTagEditPresenter.this,
                    "You must provide a name for the annotation group.",
                    event::reset);
        } else {
            work.run();
        }
    }

    private void updateTag(final Consumer<AnnotationTag> consumer,
                           final AnnotationTag annotationCollection,
                           final HidePopupRequestEvent event) {
        annotationResourceClient.updateAnnotationTag(annotationCollection,
                r -> {
                    consumer.accept(r);
                    event.hide();
                }, RestErrorHandler.forPopup(this, event), this);
    }

    @Override
    public void onPermissions() {
        final DocumentUserPermissionsPresenter presenter = documentUserPermissionsPresenterProvider.get();
        presenter.setDocRef(new DocRef(AnnotationTag.TYPE, annotationTag.getUuid(), annotationTag.getName()));
        presenter.setTaskMonitorFactory(this);
        final PopupSize popupSize = PopupSize.resizable(800, 800);
        ShowPopupEvent.builder(presenter)
                .popupType(PopupType.CLOSE_DIALOG)
                .popupSize(popupSize)
                .caption("Change Permissions")
                .onShow(e -> getView().focus())
                .onHideRequest(HidePopupRequestEvent::hide)
                .fire();
    }

    public interface AnnotationTagEditView extends View, Focus, HasUiHandlers<AnnotationTagEditUiHandlers> {

        String getName();

        void setName(String name);

        void showStyle(boolean show);

        ConditionalFormattingStyle getStyle();

        void setStyle(ConditionalFormattingStyle style);
    }
}
