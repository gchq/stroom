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

package stroom.processor.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.config.global.client.presenter.ErrorEvent;
import stroom.dispatch.client.RestFactory;
import stroom.processor.client.presenter.BatchProcessorFilterEditPresenter.BatchProcessorFilterEditView;
import stroom.processor.shared.BulkProcessorFilterChangeRequest;
import stroom.processor.shared.ProcessorFilterChange;
import stroom.processor.shared.ProcessorFilterResource;
import stroom.query.api.ExpressionOperator;
import stroom.security.client.presenter.UserRefSelectionBoxPresenter;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.shared.PageResponse;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.popup.client.presenter.Size;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Focus;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.Objects;
import javax.inject.Inject;

public class BatchProcessorFilterEditPresenter
        extends MyPresenterWidget<BatchProcessorFilterEditView> {

    private static final ProcessorFilterResource PROCESSOR_FILTER_RESOURCE = GWT.create(ProcessorFilterResource.class);

    private final UserRefSelectionBoxPresenter userRefSelectionBoxPresenter;
    private final RestFactory restFactory;
    private ExpressionOperator expression;
    private PageResponse currentResultPageResponse;

    @Inject
    public BatchProcessorFilterEditPresenter(final EventBus eventBus,
                                             final BatchProcessorFilterEditView view,
                                             final UserRefSelectionBoxPresenter userRefSelectionBoxPresenter,
                                             final RestFactory restFactory) {
        super(eventBus, view);
        this.restFactory = restFactory;
        this.userRefSelectionBoxPresenter = userRefSelectionBoxPresenter;
        getView().setUserRefSelection(userRefSelectionBoxPresenter.getView());
    }

    public void show(final ExpressionOperator expression,
                     final PageResponse currentResultPageResponse,
                     final Runnable onClose) {
        this.expression = expression;
        this.currentResultPageResponse = currentResultPageResponse;

        final PopupSize popupSize = PopupSize.builder()
                .width(Size
                        .builder()
                        .initial(500)
                        .min(500)
                        .resizable(true)
                        .build())
                .height(Size
                        .builder()
                        .initial(300)
                        .min(300)
                        .resizable(true)
                        .build())
                .build();
        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(popupSize)
                .onShow(e -> getView().focus())
                .caption("Batch Change All Filtered Processors")
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        apply(this, e, onClose);
                    } else {
                        onClose.run();
                        e.hide();
                    }
                })
                .fire();
    }

    private BulkProcessorFilterChangeRequest createRequest() {
        final ProcessorFilterChange change = getView().getChange();
        Objects.requireNonNull(change, "Change is null");

        return new BulkProcessorFilterChangeRequest(
                expression,
                getView().getChange(),
                userRefSelectionBoxPresenter.getSelected());
    }

    private void apply(final TaskMonitorFactory taskMonitorFactory,
                       final HidePopupRequestEvent event,
                       final Runnable onClose) {

        if (getView().getChange() == null) {
            ErrorEvent.fire(
                    this,
                    "No change selected.");
            event.reset();
            return;
        }

        if (ProcessorFilterChange.SET_RUN_AS_USER.equals(getView().getChange())) {
            if (userRefSelectionBoxPresenter.getSelected() == null) {
                ErrorEvent.fire(
                        this,
                        "No user selected.");
                event.reset();
                return;
            }
        }

        final long docCount;
        if (currentResultPageResponse != null &&
            currentResultPageResponse.getTotal() != null) {
            docCount = currentResultPageResponse.getTotal();
        } else {
            docCount = 0;
        }

        if (docCount == 0) {
            ErrorEvent.fire(
                    this,
                    "No processors are included in the current filter.");
            event.reset();
            return;
        }

        String message = "Are you sure you want to change this processor?";
        if (docCount > 1) {
            message = "Are you sure you want to change " + docCount + " processors?";
        }
        ConfirmEvent.fire(
                this,
                message,
                ok -> {
                    if (ok) {
                        doApply(taskMonitorFactory, event, onClose);
                    } else {
                        event.reset();
                    }
                }
        );
    }

    private void doApply(final TaskMonitorFactory taskMonitorFactory,
                         final HidePopupRequestEvent event,
                         final Runnable onClose) {
        final BulkProcessorFilterChangeRequest request = createRequest();
        restFactory
                .create(PROCESSOR_FILTER_RESOURCE)
                .method(res -> res.bulkChange(request))
                .onSuccess(result -> {
                    if (result) {
                        AlertEvent.fireInfo(
                                this,
                                "Successfully changed.",
                                () -> {
                                    event.hide();
                                    onClose.run();
                                });
                    } else {
                        AlertEvent.fireError(
                                this,
                                "Failed to change.",
                                event::reset);
                    }
                })
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    public UserRefSelectionBoxPresenter getUserRefSelectionBoxPresenter() {
        return userRefSelectionBoxPresenter;
    }

    public interface BatchProcessorFilterEditView
            extends View, Focus {

        ProcessorFilterChange getChange();

        void setUserRefSelection(View view);
    }
}
