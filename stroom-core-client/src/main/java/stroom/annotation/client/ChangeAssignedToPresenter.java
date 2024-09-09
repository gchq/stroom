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

import stroom.annotation.client.ChangeAssignedToPresenter.ChangeAssignedToView;
import stroom.annotation.shared.AnnotationResource;
import stroom.annotation.shared.SetAssignedToRequest;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.UserResource;
import stroom.util.shared.UserName;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
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

    private final RestFactory restFactory;
    private final ChooserPresenter<UserName> assignedToPresenter;
    private final ClientSecurityContext clientSecurityContext;
    private UserName currentAssignedTo;

    @Inject
    public ChangeAssignedToPresenter(final EventBus eventBus,
                                     final ChangeAssignedToView view,
                                     final RestFactory restFactory,
                                     final ChooserPresenter<UserName> assignedToPresenter,
                                     final ClientSecurityContext clientSecurityContext) {
        super(eventBus, view);
        this.restFactory = restFactory;
        this.assignedToPresenter = assignedToPresenter;
        this.clientSecurityContext = clientSecurityContext;
        getView().setUiHandlers(this);
    }

    @Override
    protected void onBind() {
        super.onBind();

        registerHandler(assignedToPresenter.addDataSelectionHandler(e -> {
            final UserName selected = assignedToPresenter.getSelected();
            changeAssignedTo(selected);
        }));
    }

    public void show(final List<Long> annotationIdList) {
        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.resizableX(500))
                .caption("Change Assigned To")
                .onShow(e -> getView().focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        final AnnotationResource annotationResource = GWT.create(AnnotationResource.class);
                        final SetAssignedToRequest request = new SetAssignedToRequest(annotationIdList,
                                currentAssignedTo);
                        restFactory
                                .create(annotationResource)
                                .method(res -> res.setAssignedTo(request))
                                .onSuccess(values -> {
                                    GWT.log("Updated " + values + " annotations");
                                    e.hide();
                                })
                                .onFailure(RestErrorHandler.forPopup(this, e))
                                .taskHandlerFactory(this)
                                .exec();
                    } else {
                        e.hide();
                    }
                })
                .fire();
    }

    private void changeAssignedTo(final UserName selected) {
        if (!Objects.equals(currentAssignedTo, selected)) {
            currentAssignedTo = selected;
            getView().setAssignedTo(selected.getUserIdentityForAudit());
            HidePopupRequestEvent.builder(assignedToPresenter)
                    .fire();
        }
    }

    @Override
    public void showAssignedToChooser(final Element element) {
        if (currentAssignedTo == null) {
            assignedToPresenter.setClearSelectionText(null);
        } else {
            assignedToPresenter.setClearSelectionText("Clear");
        }
        assignedToPresenter.setDataSupplier((filter, consumer) -> {
            final UserResource userResource = GWT.create(UserResource.class);
            restFactory
                    .create(userResource)
                    .method(res -> res.getAssociates(filter))
                    .onSuccess(consumer)
                    .taskHandlerFactory(this)
                    .exec();
        });
        assignedToPresenter.clearFilter();
        assignedToPresenter.setSelected(currentAssignedTo);
        final PopupPosition popupPosition = new PopupPosition(element.getAbsoluteLeft() - 1,
                element.getAbsoluteTop() + element.getClientHeight() + 2);
        ShowPopupEvent.builder(assignedToPresenter)
                .popupType(PopupType.POPUP)
                .popupPosition(popupPosition)
                .addAutoHidePartner(element)
                .onShow(e -> assignedToPresenter.focus())
                .fire();
    }

    @Override
    public void assignYourself() {
        changeAssignedTo(clientSecurityContext.getUserName());
    }

    public interface ChangeAssignedToView extends View, Focus, HasUiHandlers<ChangeAssignedToUiHandlers> {

        void setAssignedTo(String assignedTo);
    }
}
