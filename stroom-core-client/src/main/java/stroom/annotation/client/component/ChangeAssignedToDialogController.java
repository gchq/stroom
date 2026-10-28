/*
 * Copyright 2019 Crown Copyright
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

import stroom.annotation.client.AnnotationChangeEvent;
import stroom.annotation.client.AnnotationResourceClient;
import stroom.annotation.shared.ChangeAssignedTo;
import stroom.annotation.shared.MultiAnnotationChangeRequest;
import stroom.dispatch.client.RestErrorHandler;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.client.component.UserRefPopupController;
import stroom.security.shared.FindUserContext;
import stroom.util.shared.UserRef;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.PopupSize;
import stroom.widget.popup.client.PopupType;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.List;
import java.util.Objects;

/// Change Assigned To ([ChangeAssignedToDialog]): assigns several annotations to one user, in a
/// dialog made for one use.
public class ChangeAssignedToDialogController extends Controller {

    private final ChangeAssignedToDialog view;
    private final AnnotationResourceClient annotationResourceClient;
    private final UserRefPopupController userPicker;
    private final ClientSecurityContext clientSecurityContext;

    private UserRef currentAssignedTo;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus                 The application's event bus.
    /// @param view                     The content.
    /// @param annotationResourceClient Changes the annotations.
    /// @param userPicker               Chooses who to assign them to, which this owns.
    /// @param clientSecurityContext    Gives the current user, for Assign Yourself.
    @Inject
    public ChangeAssignedToDialogController(final EventBus eventBus,
                                            final ChangeAssignedToDialog view,
                                            final AnnotationResourceClient annotationResourceClient,
                                            final UserRefPopupController userPicker,
                                            final ClientSecurityContext clientSecurityContext) {
        super(eventBus);
        this.view = view;
        this.annotationResourceClient = annotationResourceClient;
        this.userPicker = own(userPicker);
        this.clientSecurityContext = clientSecurityContext;
        initWidget(view);
        userPicker.setContext(FindUserContext.ANNOTATION_ASSIGNMENT);
        view.setOnChooseAssignedTo(() -> {
            userPicker.setSelected(currentAssignedTo);
            userPicker.show(this::changeAssignedTo);
        });
        view.setOnAssignYourself(() -> changeAssignedTo(clientSecurityContext.getUserRef()));
    }

    /// Shows the dialog, owned by `opener` until it is closed; OK assigns the annotations to who is
    /// chosen, or unassigns them for nobody.
    ///
    /// @param opener           What shows it.
    /// @param annotationIdList The annotations.
    public void show(final HasControllerNode opener, final List<Long> annotationIdList) {
        setAssignedTo(null);
        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .modal()
                .popupSize(PopupSize.resizableX(500))
                .caption("Change Assigned To")
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        final MultiAnnotationChangeRequest request = new MultiAnnotationChangeRequest(
                                annotationIdList,
                                new ChangeAssignedTo(currentAssignedTo));
                        annotationResourceClient.batchChange(this, request,
                                values -> {
                                    GWT.log("Updated " + values + " annotations");
                                    if (values > 0) {
                                        AnnotationChangeEvent.fire(this, null);
                                    }
                                    e.hide();
                                },
                                RestErrorHandler.forPopup(this, e),
                                taskMonitor());
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
        userPicker.resolve(assignedTo, userRef -> {
            currentAssignedTo = userRef;
            view.setAssignedTo(userRef);
            view.setAssignYourselfEnabled(!Objects.equals(userRef, clientSecurityContext.getUserRef()));
            userPicker.setSelected(currentAssignedTo);
        });
    }

}
