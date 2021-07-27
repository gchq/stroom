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

import stroom.annotation.client.ChangeStatusPresenter.ChangeStatusView;
import stroom.annotation.shared.AnnotationResource;
import stroom.annotation.shared.SetStatusRequest;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.DefaultPopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.List;
import java.util.Objects;

public class ChangeStatusPresenter extends MyPresenterWidget<ChangeStatusView>
        implements ChangeStatusUiHandlers {

    private final PopupUiHandlers popupUiHandlers;
    private final RestFactory restFactory;
    private final ChooserPresenter statusPresenter;
    private List<Long> annotationIdList;
    private String currentStatus;

    @Inject
    public ChangeStatusPresenter(final EventBus eventBus,
                                 final ChangeStatusView view,
                                 final RestFactory restFactory,
                                 final ChooserPresenter statusPresenter) {
        super(eventBus, view);
        popupUiHandlers = new DefaultPopupUiHandlers(this) {
            @Override
            public void onHideRequest(final boolean autoClose, final boolean ok) {
                if (ok) {
                    final AnnotationResource annotationResource = GWT.create(AnnotationResource.class);
                    final Rest<Integer> rest = restFactory.create();

                    final SetStatusRequest request = new SetStatusRequest(annotationIdList, currentStatus);
                    rest.onSuccess(values -> GWT.log("Updated " + values + " annotations"))
                            .call(annotationResource)
                            .setStatus(request);
                }
                hide(autoClose, ok);
            }
        };
        this.restFactory = restFactory;
        this.statusPresenter = statusPresenter;
        getView().setUiHandlers(this);
    }

    @Override
    protected void onBind() {
        super.onBind();

        registerHandler(statusPresenter.addDataSelectionHandler(e -> {
            final String selected = statusPresenter.getSelected();
            changeStatus(selected);
        }));
    }

    public void show(final List<Long> annotationIdList) {
        this.annotationIdList = annotationIdList;

        if (currentStatus == null) {
            final AnnotationResource annotationResource = GWT.create(AnnotationResource.class);
            final Rest<List<String>> rest = restFactory.create();
            rest.onSuccess(values -> {
                if (currentStatus == null && values != null && values.size() > 0) {
                    changeStatus(values.get(0));
                }
            }).call(annotationResource).getStatus(null);
        }

        ShowPopupEvent.fire(this, this, PopupType.OK_CANCEL_DIALOG, "Change Status", popupUiHandlers);
    }

    private void changeStatus(final String selected) {
        if (!Objects.equals(currentStatus, selected)) {
            currentStatus = selected;
            getView().setStatus(selected);
            HidePopupEvent.fire(this, statusPresenter, true, true);
        }
    }

    @Override
    public void showStatusChooser(final Element element) {
        statusPresenter.setDataSupplier((filter, consumer) -> {
            final AnnotationResource annotationResource = GWT.create(AnnotationResource.class);
            final Rest<List<String>> rest = restFactory.create();
            rest.onSuccess(consumer).call(annotationResource).getStatus(filter);
        });
        statusPresenter.clearFilter();
        statusPresenter.setSelected(currentStatus);
        final PopupPosition popupPosition = new PopupPosition(element.getAbsoluteLeft() - 1,
                element.getAbsoluteTop() + element.getClientHeight() + 2);
        ShowPopupEvent.fire(this, statusPresenter, PopupType.POPUP, popupPosition, null, element);
    }

    public interface ChangeStatusView extends View, HasUiHandlers<ChangeStatusUiHandlers> {

        void setStatus(String status);
    }
}
