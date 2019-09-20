/*
 * Copyright 2018 Crown Copyright
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

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import org.fusesource.restygwt.client.Defaults;
import org.fusesource.restygwt.client.Method;
import org.fusesource.restygwt.client.MethodCallback;
import stroom.alert.client.event.AlertEvent;
import stroom.annotation.client.AnnotationEditPresenter.AnnotationEditView;
import stroom.annotation.shared.Annotation;
import stroom.annotation.shared.AnnotationResource;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.entity.shared.PageRequest;
import stroom.widget.customdatebox.client.ClientDateUtil;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

public class AnnotationEditPresenter extends MyPresenterWidget<AnnotationEditView> {
    private final ClientDispatchAsync dispatcher;
    private final RestFactory restFactory;

    private Annotation annotation;

    @Inject
    public AnnotationEditPresenter(final EventBus eventBus,
                                   final AnnotationEditView view,
                                   final ClientDispatchAsync dispatcher,
                                   final RestFactory restFactory) {
        super(eventBus, view);
        this.dispatcher = dispatcher;
        this.restFactory = restFactory;
    }

    public void show(final String eventId) {
//        MethodCallback<Annotation> callback = new MethodCallback<Annotation>() {
//            @Override
//            public void onFailure(Method method, Throwable caught) {
//                AlertEvent.fireError(AnnotationEditPresenter.this, caught.getMessage(), null);
//            }
//
//            @Override
//            public void onSuccess(Method method, Annotation result) {
//                edit(result);
//            }
//        };
//
//        String hostPageBaseUrl = GWT.getHostPageBaseURL();
//        hostPageBaseUrl = hostPageBaseUrl.substring(0, hostPageBaseUrl.lastIndexOf("/"));
//        hostPageBaseUrl = hostPageBaseUrl.substring(0, hostPageBaseUrl.lastIndexOf("/"));
//        final String apiUrl = hostPageBaseUrl + "/api/";
//        Defaults.setServiceRoot(apiUrl);
//        AnnotationClient client = GWT.create(AnnotationClient.class);
//        client.get(eventId, callback);

        final AnnotationResource annotationResource = GWT.create(AnnotationResource.class);
        final Rest<Annotation> rest = restFactory.create();
        rest.onSuccess(this::edit).call(annotationResource).get(eventId);
    }

    private void edit(Annotation annotation) {
        read(annotation);

//            getView().getHtml().setHTML(activityEditorBody);
//
        final PopupUiHandlers internalPopupUiHandlers = new PopupUiHandlers() {
            @Override
            public void onHideRequest(final boolean autoClose, final boolean ok) {
//                    if (ok) {
//                        write(consumer);
//                    } else {
//                        consumer.accept(activity);
                hide();
//                    }
            }

            @Override
            public void onHide(final boolean autoClose, final boolean ok) {
            }
        };

        final PopupSize popupSize = new PopupSize(640, 480, true);
        ShowPopupEvent.fire(this,
                this,
                PopupType.OK_CANCEL_DIALOG,
                popupSize, "Edit Annotation: " + annotation.getMetaId() + ":" + annotation.getEventId(),
                internalPopupUiHandlers);
    }

    private void hide() {
        HidePopupEvent.fire(AnnotationEditPresenter.this, AnnotationEditPresenter.this);
    }

    private void read(final Annotation annotation) {
        this.annotation = annotation;
        getView().setTitle("Event Id: " + annotation.getMetaId() + ":" + annotation.getEventId());
        getView().setCreatedBy(annotation.getCreatedBy());
        getView().setCreatedOn(ClientDateUtil.toDateString(annotation.getCreatedOn()));
        getView().setStatus(annotation.getStatus());
        getView().setAssignedTo(annotation.getAssignedTo());
    }

    public interface AnnotationEditView extends View {
        String getTitle();

        void setTitle(String title);

        String getCreatedBy();

        void setCreatedBy(String createdBy);

        String getCreatedOn();

        void setCreatedOn(String createdOn);

        String getStatus();

        void setStatus(String status);

        String getAssignedTo();

        void setAssignedTo(String assignedTo);

        void setHistoryView(View view);

        void setCommentView(View view);
    }
}