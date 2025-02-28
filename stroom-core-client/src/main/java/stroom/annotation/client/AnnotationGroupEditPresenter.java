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

package stroom.annotation.client;

import stroom.alert.client.event.AlertEvent;
import stroom.annotation.shared.AnnotationGroup;
import stroom.dispatch.client.RestErrorHandler;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.Objects;
import java.util.function.Consumer;

public class AnnotationGroupEditPresenter
        extends MyPresenterWidget<AnnotationGroupEditPresenter.AnnotationGroupEditView> {

    private final AnnotationResourceClient annotationResourceClient;
    private AnnotationGroup annotationGroup;

    @Inject
    public AnnotationGroupEditPresenter(final EventBus eventBus,
                                        final AnnotationGroupEditView view,
                                        final AnnotationResourceClient annotationResourceClient) {
        super(eventBus, view);
        this.annotationResourceClient = annotationResourceClient;
    }

    void open(final AnnotationGroup ag,
              final String title,
              final Consumer<AnnotationGroup> consumer) {
        final String groupUuid = ag.getUuid();
        this.annotationGroup = ag;
        getView().setName(annotationGroup.getName());

        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .caption(title)
                .onShow(e -> getView().focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        annotationGroup = annotationGroup.copy().name(getView().getName()).build();
                        try {
                            doWithGroupNameValidation(getView().getName(), groupUuid, () -> {
                                if (groupUuid == null) {
                                    createGroup(consumer, annotationGroup, e);
                                } else {
                                    updateGroup(consumer, annotationGroup, e);
                                }
                            }, e);
                        } catch (final RuntimeException ex) {
                            AlertEvent.fireError(
                                    AnnotationGroupEditPresenter.this,
                                    ex.getMessage(),
                                    e::reset);
                        }
                    } else {
                        e.hide();
                    }
                })
                .fire();
    }

    private void doWithGroupNameValidation(final String groupName,
                                           final String groupUuid,
                                           final Runnable work,
                                           final HidePopupRequestEvent event) {
        if (groupName == null || groupName.isEmpty()) {
            AlertEvent.fireError(
                    AnnotationGroupEditPresenter.this,
                    "You must provide a name for the annotation group.",
                    event::reset);
        } else {
            annotationResourceClient.fetchAnnotationGroupByName(getView().getName(),
                    grp -> {
                        if (grp != null && !Objects.equals(groupUuid, grp.getUuid())) {
                            AlertEvent.fireError(
                                    AnnotationGroupEditPresenter.this,
                                    "Group name '"
                                    + groupName
                                    + "' is already in use by another group.",
                                    event::reset);
                        } else {
                            work.run();
                        }
                    },
                    RestErrorHandler.forPopup(this, event),
                    this);
        }
    }

    private void createGroup(final Consumer<AnnotationGroup> consumer,
                             final AnnotationGroup annotationGroup,
                             final HidePopupRequestEvent event) {
        annotationResourceClient.createAnnotationGroup(annotationGroup.getName(),
                r -> {
                    consumer.accept(r);
                    event.hide();
                }, RestErrorHandler.forPopup(this, event), this);
    }

    private void updateGroup(final Consumer<AnnotationGroup> consumer,
                             final AnnotationGroup annotationGroup,
                             final HidePopupRequestEvent event) {
        annotationResourceClient.updateAnnotationGroup(annotationGroup,
                r -> {
                    consumer.accept(r);
                    event.hide();
                }, RestErrorHandler.forPopup(this, event), this);
    }


    // --------------------------------------------------------------------------------


    public interface AnnotationGroupEditView extends View, Focus {

        String getName();

        void setName(String name);
    }
}
