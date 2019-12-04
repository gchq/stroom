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
import com.google.gwt.dom.client.Element;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import stroom.annotation.client.ChangeAssignedToPresenter.ChangeAssignedToView;
import stroom.annotation.shared.AnnotationResource;
import stroom.annotation.shared.SetAssignedToRequest;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.security.client.ClientSecurityContext;
import stroom.security.shared.UserResource;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

import java.util.List;
import java.util.Objects;

public class ChangeAssignedToPresenter extends MyPresenterWidget<ChangeAssignedToView> implements PopupUiHandlers, ChangeAssignedToUiHandlers {
    private final RestFactory restFactory;
    private final ChooserPresenter assignedToPresenter;
    private final ClientSecurityContext clientSecurityContext;
    private List<Long> annotationIdList;
    private String currentAssignedTo;

    @Inject
    public ChangeAssignedToPresenter(final EventBus eventBus,
                                     final ChangeAssignedToView view,
                                     final RestFactory restFactory,
                                     final ChooserPresenter assignedToPresenter,
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
            final String selected = assignedToPresenter.getSelected();
            changeAssignedTo(selected);
        }));
    }

    public void show(final List<Long> annotationIdList) {
        this.annotationIdList = annotationIdList;

//        if (currentAssignedTo == null) {
//            final AnnotationResource annotationResource = GWT.create(AnnotationResource.class);
//            final Rest<List<String>> rest = restFactory.create();
//            rest.onSuccess(values -> {
//                if (currentAssignedTo == null && values != null && values.size() > 0) {
//                    changeAssignedTo(values.get(0), false);
//                }
//            }).call(annotationResource).getStatus(null);
//        }

        final PopupSize popupSize = new PopupSize(400, 83, 400, 83, true);
        ShowPopupEvent.fire(this, this, PopupType.OK_CANCEL_DIALOG, popupSize, "Change Assigned To", this);
    }

    private void changeAssignedTo(final String selected) {
        if (!Objects.equals(currentAssignedTo, selected)) {
            currentAssignedTo = selected;
            getView().setAssignedTo(selected);
            HidePopupEvent.fire(this, assignedToPresenter, true, true);
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
            final Rest<List<String>> rest = restFactory.create();
            rest.onSuccess(consumer).call(userResource).getAssociates(filter);
        });
        assignedToPresenter.clearFilter();
        assignedToPresenter.setSelected(currentAssignedTo);
        final PopupPosition popupPosition = new PopupPosition(element.getAbsoluteLeft() - 1,
                element.getAbsoluteTop() + element.getClientHeight() + 2);
        ShowPopupEvent.fire(this, assignedToPresenter, PopupType.POPUP, popupPosition, null, element);
    }

    @Override
    public void assignYourself() {
        changeAssignedTo(clientSecurityContext.getUserId());
    }

    @Override
    public void onHideRequest(final boolean autoClose, final boolean ok) {
        if (ok) {
            final AnnotationResource annotationResource = GWT.create(AnnotationResource.class);
            final Rest<Integer> rest = restFactory.create();

            final SetAssignedToRequest request = new SetAssignedToRequest(annotationIdList, currentAssignedTo);
            rest.onSuccess(values -> GWT.log("Updated " + values + " annotations"))
                    .call(annotationResource)
                    .setAssignedTo(request);
        }
        HidePopupEvent.fire(this, this);
    }

    @Override
    public void onHide(final boolean autoClose, final boolean ok) {
    }

    public interface ChangeAssignedToView extends View, HasUiHandlers<ChangeAssignedToUiHandlers> {
        void setAssignedTo(String assignedTo);
    }
}
