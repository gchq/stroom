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

package stroom.annotation.client;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import stroom.annotation.client.ChangeAssignedToPresenter.ChangeAssignedToView;
import stroom.annotation.shared.AnnotationResource;
import stroom.annotation.shared.SetAssignedToRequest;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.client.presenter.UserRefSelectionBoxPresenter;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import java.util.List;

public class ChangeAssignedToPresenter
        extends MyPresenterWidget<ChangeAssignedToView>
        implements ChangeAssignedToUiHandlers {

    private final RestFactory restFactory;
    private final UserRefSelectionBoxPresenter userRefSelectionBoxPresenter;
    private final ClientSecurityContext clientSecurityContext;

    @Inject
    public ChangeAssignedToPresenter(final EventBus eventBus,
                                     final ChangeAssignedToView view,
                                     final RestFactory restFactory,
                                     final UserRefSelectionBoxPresenter userRefSelectionBoxPresenter,
                                     final ClientSecurityContext clientSecurityContext) {
        super(eventBus, view);
        this.restFactory = restFactory;
        this.userRefSelectionBoxPresenter = userRefSelectionBoxPresenter;
        this.clientSecurityContext = clientSecurityContext;
        getView().setUserView(userRefSelectionBoxPresenter.getView());
        getView().setUiHandlers(this);
    }

    public void show(final List<Long> annotationIdList) {
        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.resizableX(500))
                .caption("Change Assigned To")
                .onShow(e -> userRefSelectionBoxPresenter.focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        final AnnotationResource annotationResource = GWT.create(AnnotationResource.class);
                        final SetAssignedToRequest request = new SetAssignedToRequest(
                                annotationIdList,
                                userRefSelectionBoxPresenter.getSelected());
                        restFactory
                                .create(annotationResource)
                                .method(res -> res.setAssignedTo(request))
                                .onSuccess(values -> {
                                    GWT.log("Updated " + values + " annotations");
                                    e.hide();
                                })
                                .onFailure(RestErrorHandler.forPopup(this, e))
                                .taskListener(this)
                                .exec();
                    } else {
                        e.hide();
                    }
                })
                .fire();
    }

    @Override
    public void assignYourself() {
        userRefSelectionBoxPresenter.setSelected(clientSecurityContext.getUserRef());
    }

    public interface ChangeAssignedToView extends View, HasUiHandlers<ChangeAssignedToUiHandlers> {

        void setUserView(final View view);
    }
}
