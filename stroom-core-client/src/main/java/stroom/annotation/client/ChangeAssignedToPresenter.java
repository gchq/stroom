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

import stroom.annotation.client.ChangeAssignedToPresenter.ChangeAssignedToView;
import stroom.annotation.shared.ChangeAssignedTo;
import stroom.annotation.shared.MultiAnnotationChangeRequest;
import stroom.dispatch.client.RestErrorHandler;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.client.presenter.UserRefPopupPresenter;
import stroom.security.shared.FindUserContext;
import stroom.util.shared.UserRef;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.List;
import java.util.Objects;

public class ChangeAssignedToPresenter
        extends MyPresenterWidget<ChangeAssignedToView>
        implements ChangeAssignedToUiHandlers {

    private final AnnotationResourceClient annotationResourceClient;
    private final UserRefPopupPresenter assignedToPresenter;
    private final ClientSecurityContext clientSecurityContext;

    private UserRef currentAssignedTo;

    @Inject
    public ChangeAssignedToPresenter(final EventBus eventBus,
                                     final ChangeAssignedToView view,
                                     final AnnotationResourceClient annotationResourceClient,
                                     final UserRefPopupPresenter assignedToPresenter,
                                     final ClientSecurityContext clientSecurityContext) {
        super(eventBus, view);
        this.annotationResourceClient = annotationResourceClient;
        this.assignedToPresenter = assignedToPresenter;
        this.clientSecurityContext = clientSecurityContext;
        getView().setUiHandlers(this);
        assignedToPresenter.setContext(FindUserContext.ANNOTATION_ASSIGNMENT);
    }

    public void show(final List<Long> annotationIdList) {
        setAssignedTo(null);

        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.resizableX(500))
                .caption("Change Assigned To")
                .onShow(e -> getView().focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        final MultiAnnotationChangeRequest request = new MultiAnnotationChangeRequest(
                                annotationIdList,
                                new ChangeAssignedTo(currentAssignedTo));
                        annotationResourceClient.batchChange(request,
                                values -> {
                                    GWT.log("Updated " + values + " annotations");
                                    if (values > 0) {
                                        AnnotationChangeEvent.fire(this, null);
                                    }
                                    e.hide();
                                },
                                RestErrorHandler.forPopup(this, e),
                                this);
                    } else {
                        e.hide();
                    }
                })
                .fire();
    }

    private void changeAssignedTo(final UserRef selected) {
        if (!Objects.equals(currentAssignedTo, selected)) {
            setAssignedTo(selected);
        }
    }

    private void setAssignedTo(final UserRef assignedTo) {
        currentAssignedTo = assignedTo;
        assignedToPresenter.resolve(assignedTo, userRef -> {
            currentAssignedTo = userRef;
            getView().setAssignedTo(userRef);
            getView().setAssignYourselfVisible(!Objects.equals(userRef, clientSecurityContext.getUserRef()));
            assignedToPresenter.setSelected(currentAssignedTo);
        });
    }

    @Override
    public void showAssignedToChooser(final Element element) {
        assignedToPresenter.setSelected(currentAssignedTo);
        assignedToPresenter.show(this::changeAssignedTo);
    }

    @Override
    public void assignYourself() {
        changeAssignedTo(clientSecurityContext.getUserRef());
    }

    public interface ChangeAssignedToView extends View, Focus, HasUiHandlers<ChangeAssignedToUiHandlers> {

        void setAssignedTo(UserRef assignedTo);

        void setAssignYourselfVisible(boolean visible);
    }
}
