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

import stroom.annotation.client.ChangeStatusPresenter.ChangeStatusView;
import stroom.annotation.shared.AnnotationTag;
import stroom.annotation.shared.AnnotationTagFields;
import stroom.annotation.shared.AnnotationTagType;
import stroom.annotation.shared.MultiAnnotationChangeRequest;
import stroom.annotation.shared.SetTag;
import stroom.dispatch.client.DefaultErrorHandler;
import stroom.dispatch.client.RestErrorHandler;
import stroom.entity.shared.ExpressionCriteria;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.util.shared.NullSafe;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
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
    private final ChooserPresenter<AnnotationTag> annotationStatusPresenter;
    private AnnotationTag currentStatus;

    @Inject
    public ChangeStatusPresenter(final EventBus eventBus,
                                 final ChangeStatusView view,
                                 final AnnotationResourceClient annotationResourceClient,
                                 final ChooserPresenter<AnnotationTag> annotationStatusPresenter) {
        super(eventBus, view);
        this.annotationResourceClient = annotationResourceClient;
        this.annotationStatusPresenter = annotationStatusPresenter;
        this.annotationStatusPresenter.setDataSupplier((filter, consumer) -> {
            final ExpressionCriteria criteria = createCriteria(AnnotationTagType.STATUS, filter);
            annotationResourceClient.findAnnotationTags(criteria, values ->
                            consumer.accept(values.getValues()),
                    new DefaultErrorHandler(this, null), this);
        });
        annotationStatusPresenter.setDisplayValueFunction(at -> SafeHtmlUtils.fromString(at.getName()));

        getView().setUiHandlers(this);
    }

    private ExpressionCriteria createCriteria(final AnnotationTagType annotationTagType,
                                              final String filter) {
        final ExpressionOperator.Builder builder = ExpressionOperator.builder();
        builder.addTerm(ExpressionTerm.builder()
                .field(AnnotationTagFields.TYPE_ID)
                .condition(Condition.EQUALS)
                .value(annotationTagType.getDisplayValue())
                .build());
        if (!NullSafe.isBlankString(filter)) {
            builder.addTerm(ExpressionTerm.builder()
                    .field(AnnotationTagFields.NAME)
                    .condition(Condition.CONTAINS)
                    .value(filter)
                    .build());
        }
        return new ExpressionCriteria(builder.build());
    }

    @Override
    protected void onBind() {
        super.onBind();

        registerHandler(annotationStatusPresenter.addDataSelectionHandler(e -> {
            final AnnotationTag selected = annotationStatusPresenter.getSelected();
            if (!Objects.equals(currentStatus, selected)) {
                changeStatus(selected);
                HidePopupRequestEvent.builder(annotationStatusPresenter).fire();
            }
        }));
    }

    public void show(final List<Long> annotationIdList) {
        setStatus(null);

        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.resizableX(500))
                .caption("Change Status")
                .onShow(e -> getView().focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        final MultiAnnotationChangeRequest request = new MultiAnnotationChangeRequest(annotationIdList,
                                new SetTag(currentStatus));
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

    private void changeStatus(final AnnotationTag selected) {
        if (!Objects.equals(currentStatus, selected)) {
            setStatus(selected);
        }
    }

    private void setStatus(final AnnotationTag status) {
        currentStatus = status;
        getView().setStatus(status);
        annotationStatusPresenter.clearFilter();
        annotationStatusPresenter.setSelected(status);
    }

    @Override
    public void showStatusChooser(final Element element) {
        final PopupPosition popupPosition = new PopupPosition(element.getAbsoluteLeft() - 1,
                element.getAbsoluteTop() + element.getClientHeight() + 2);
        ShowPopupEvent.builder(annotationStatusPresenter)
                .popupType(PopupType.POPUP)
                .popupPosition(popupPosition)
                .addAutoHidePartner(element)
                .onShow(e -> annotationStatusPresenter.focus())
                .fire();
    }

    public interface ChangeStatusView extends View, Focus, HasUiHandlers<ChangeStatusUiHandlers> {

        void setStatus(AnnotationTag status);
    }
}
