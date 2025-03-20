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
import stroom.annotation.shared.AnnotationTag;
import stroom.annotation.shared.AnnotationTagFields;
import stroom.annotation.shared.AnnotationTagType;
import stroom.annotation.shared.MultiAnnotationChangeRequest;
import stroom.annotation.shared.SetTag;
import stroom.dispatch.client.DefaultErrorHandler;
import stroom.dispatch.client.RestErrorHandler;
import stroom.entity.shared.ExpressionCriteria;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm;
import stroom.query.api.v2.ExpressionTerm.Condition;
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

public class ChangeStatusPresenter
        extends MyPresenterWidget<ChangeStatusView>
        implements ChangeStatusUiHandlers {

    private final AnnotationResourceClient annotationResourceClient;
    private final ChooserPresenter<AnnotationTag> statusPresenter;
    private AnnotationTag currentStatus;

    @Inject
    public ChangeStatusPresenter(final EventBus eventBus,
                                 final ChangeStatusView view,
                                 final AnnotationResourceClient annotationResourceClient,
                                 final ChooserPresenter<AnnotationTag> statusPresenter) {
        super(eventBus, view);
        this.annotationResourceClient = annotationResourceClient;
        this.statusPresenter = statusPresenter;
        getView().setUiHandlers(this);
    }

    @Override
    protected void onBind() {
        super.onBind();

        registerHandler(statusPresenter.addDataSelectionHandler(e -> {
            final AnnotationTag selected = statusPresenter.getSelected();
            changeStatus(selected);
        }));
    }

    public void show(final List<Long> annotationIdList) {
        if (currentStatus == null) {
            final ExpressionOperator expression = ExpressionOperator
                    .builder()
                    .addTerm(ExpressionTerm.builder()
                            .field(AnnotationTagFields.TYPE_ID)
                            .condition(Condition.EQUALS)
                            .value(AnnotationTagType.STATUS.getDisplayValue())
                            .build())
                    .build();
            final ExpressionCriteria criteria = new ExpressionCriteria(expression);
            annotationResourceClient.findAnnotationTags(criteria, values -> {
                if (currentStatus == null && values != null && !values.isEmpty()) {
                    changeStatus(values.getValues().get(0));
                }
            }, new DefaultErrorHandler(this, null), this);
        }

        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.resizableX(300))
                .caption("Change Status")
                .onShow(e -> getView().focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        final MultiAnnotationChangeRequest request = new MultiAnnotationChangeRequest(annotationIdList,
                                new SetTag(currentStatus));
                        annotationResourceClient.batchChange(request,
                                values -> {
                                    GWT.log("Updated " + values + " annotations");
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

    private void changeStatus(final AnnotationTag selected) {
        if (!Objects.equals(currentStatus, selected)) {
            currentStatus = selected;
            getView().setStatus(selected);
            HidePopupRequestEvent.builder(statusPresenter).fire();
        }
    }

    @Override
    public void showStatusChooser(final Element element) {
        statusPresenter.setDataSupplier((filter, consumer) -> {
            final ExpressionOperator expression = ExpressionOperator
                    .builder()
                    .addTerm(ExpressionTerm.builder()
                            .field(AnnotationTagFields.TYPE_ID)
                            .condition(Condition.EQUALS)
                            .value(AnnotationTagType.STATUS.getDisplayValue())
                            .build())
                    .addTerm(ExpressionTerm.builder()
                            .field(AnnotationTagFields.NAME)
                            .condition(Condition.CONTAINS)
                            .value(filter)
                            .build())
                    .build();
            final ExpressionCriteria criteria = new ExpressionCriteria(expression);
            annotationResourceClient.findAnnotationTags(criteria, values ->
                            consumer.accept(values.getValues()),
                    new DefaultErrorHandler(this, null), this);
        });
        statusPresenter.clearFilter();
        statusPresenter.setSelected(currentStatus);
        final PopupPosition popupPosition = new PopupPosition(element.getAbsoluteLeft() - 1,
                element.getAbsoluteTop() + element.getClientHeight() + 2);
        ShowPopupEvent.builder(statusPresenter)
                .popupType(PopupType.POPUP)
                .popupPosition(popupPosition)
                .addAutoHidePartner(element)
                .onShow(e -> statusPresenter.focus())
                .fire();
    }

    public interface ChangeStatusView extends View, Focus, HasUiHandlers<ChangeStatusUiHandlers> {

        void setStatus(AnnotationTag status);
    }
}
