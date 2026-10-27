/*
 * Copyright 2024 Crown Copyright
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

package stroom.processor.client.component;

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.processor.shared.BulkProcessorFilterChangeRequest;
import stroom.processor.shared.ProcessorFilterChange;
import stroom.processor.shared.ProcessorFilterResource;
import stroom.query.api.ExpressionOperator;
import stroom.security.client.component.UserRefSelectionController;
import stroom.task.client.DefaultTaskMonitorFactory;
import stroom.task.client.TaskMonitorFactory;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.popup.client.presenter.Size;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Objects;

/// Changes every processor filter a filter matches ([BatchProcessorFilterEdit]), in a dialog made
/// for one use. OK asks the server how many filters will change, then asks first.
public class BatchProcessorFilterEditController extends Controller {

    private static final ProcessorFilterResource PROCESSOR_FILTER_RESOURCE = GWT.create(ProcessorFilterResource.class);

    private final BatchProcessorFilterEdit view;
    private final UserRefSelectionController userRefSelection;
    private final RestFactory restFactory;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus                 The application's event bus.
    /// @param view                     The content.
    /// @param userRefSelectionProvider Makes the Run As User picker.
    /// @param restFactory              Counts the filters and makes the change.
    @Inject
    public BatchProcessorFilterEditController(final EventBus eventBus,
                                              final BatchProcessorFilterEdit view,
                                              final Provider<UserRefSelectionController> userRefSelectionProvider,
                                              final RestFactory restFactory) {
        super(eventBus);
        this.view = view;
        this.restFactory = restFactory;
        initWidget(view);
        userRefSelection = make(userRefSelectionProvider);
        userRefSelection.attach(view.getUserRefBox());
    }

    /// Shows the dialog, owned by `opener` until it is closed.
    ///
    /// @param opener     What shows it.
    /// @param expression The filter whose processor filters change.
    /// @param onClose    Called once the change is made, or on Cancel.
    public void show(final HasControllerNode opener,
                     final ExpressionOperator expression,
                     final Runnable onClose) {
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
        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .modal()
                .popupSize(popupSize)
                .onShow(e -> view.focus())
                .caption("Batch Change All Filtered Processors")
                .onHideRequest(e -> onHideRequest(e, expression, onClose))
                .fire();
    }

    // Package-private for the tests
    void onHideRequest(final HidePopupRequestEvent event,
                       final ExpressionOperator expression,
                       final Runnable onClose) {
        if (event.isOk()) {
            apply(event, expression, onClose);
        } else {
            onClose.run();
            event.hide();
        }
    }

    private BulkProcessorFilterChangeRequest createRequest(final ExpressionOperator expression) {
        final ProcessorFilterChange change = view.getChange();
        Objects.requireNonNull(change, "Change is null");
        return new BulkProcessorFilterChangeRequest(expression, change, userRefSelection.getSelected());
    }

    private void apply(final HidePopupRequestEvent event,
                       final ExpressionOperator expression,
                       final Runnable onClose) {
        if (view.getChange() == null) {
            AlertEvent.fireWarn(this, "No change selected.", event::reset);
            return;
        }

        if (ProcessorFilterChange.SET_RUN_AS_USER.equals(view.getChange())
            && userRefSelection.getSelected() == null) {
            AlertEvent.fireWarn(this, "No user selected.", event::reset);
            return;
        }

        // Ask the server how many filters the change would apply to. The processor list's row total
        // can't be used: it counts each processor's row as well as its filters' rows.
        restFactory
                .create(PROCESSOR_FILTER_RESOURCE)
                .method(res -> res.getBulkChangeCount(createRequest(expression)))
                .onSuccess(count -> confirm(count, event, expression, onClose))
                .onFailure(RestErrorHandler.forPopup(this, event))
                .owner(this)
                .taskMonitorFactory(taskMonitor())
                .exec();
    }

    private void confirm(final Integer count,
                         final HidePopupRequestEvent event,
                         final ExpressionOperator expression,
                         final Runnable onClose) {
        final int filterCount = count == null
                ? 0
                : count;
        if (filterCount == 0) {
            AlertEvent.fireWarn(this, "No processors are included in the current filter.", event::reset);
            return;
        }

        String message = "Are you sure you want to change this processor?";
        if (filterCount > 1) {
            message = "Are you sure you want to change " + filterCount + " processors?";
        }
        ConfirmEvent.fire(this, message, ok -> {
            if (ok) {
                doApply(event, expression, onClose);
            } else {
                event.reset();
            }
        });
    }

    private void doApply(final HidePopupRequestEvent event,
                         final ExpressionOperator expression,
                         final Runnable onClose) {
        final BulkProcessorFilterChangeRequest request = createRequest(expression);
        restFactory
                .create(PROCESSOR_FILTER_RESOURCE)
                .method(res -> res.bulkChange(request))
                .onSuccess(result -> {
                    if (result) {
                        AlertEvent.fireInfo(this, "Successfully changed.", () -> {
                            event.hide();
                            onClose.run();
                        });
                    } else {
                        AlertEvent.fireError(this, "Failed to change.", event::reset);
                    }
                })
                // If the change fails, let the user try again or cancel
                .onFailure(RestErrorHandler.forPopup(this, event))
                .owner(this)
                .taskMonitorFactory(taskMonitor())
                .exec();
    }

    private TaskMonitorFactory taskMonitor() {
        final TaskMonitorFactory provided = getProvidedTaskMonitor();
        return provided != null ? provided : new DefaultTaskMonitorFactory(this);
    }
}
