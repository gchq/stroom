/*
 * Copyright 2021 Crown Copyright
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

package stroom.dashboard.client.component;

import stroom.dashboard.client.main.DashboardContext;
import stroom.dashboard.shared.ComponentSelectionHandler;
import stroom.query.api.ExpressionOperator;
import stroom.query.client.component.ExpressionEditorController;
import stroom.query.client.presenter.FieldSelectionListModel;
import stroom.util.shared.RandomId;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.PopupSize;
import stroom.widget.popup.client.PopupType;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;

import java.util.function.Consumer;

/// Adds or edits a dashboard query's selection handler in a dialog ([SelectionHandlerEditor]),
/// made for one use. The selection shown follows the dashboard's while the dialog is open.
public class SelectionHandlerEditorController extends Controller {

    private final SelectionHandlerEditor view;
    private final ExpressionEditorController expressionEditor;
    private final CurrentSelectionController currentSelection;
    private ComponentSelectionHandler originalHandler;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus         The application's event bus.
    /// @param view             The content.
    /// @param expressionEditor Edits the handler's expression.
    /// @param currentSelection Shows the dashboard's selection, to insert into the expression.
    @Inject
    public SelectionHandlerEditorController(final EventBus eventBus,
                                            final SelectionHandlerEditor view,
                                            final ExpressionEditorController expressionEditor,
                                            final CurrentSelectionController currentSelection) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        this.expressionEditor = own(expressionEditor);
        this.currentSelection = own(currentSelection);
        // Its slot frames the expression's editor itself
        expressionEditor.setFramed(false);
        view.setExpressionEditor(expressionEditor);
        view.setCurrentSelection(currentSelection);

        currentSelection.setOnInsert(expressionEditor::insertValue);
    }

    /// Shows a selection handler as a dialog, owned by `opener` until it is closed.
    ///
    /// @param opener           What shows it.
    /// @param caption          The dialog's caption.
    /// @param handler          The selection handler to edit, or a new one.
    /// @param fields           The fields the expression may use.
    /// @param dashboardContext The dashboard, whose selection is shown.
    /// @param onOk             Given the selection handler as edited when OK is pressed.
    public void edit(final HasControllerNode opener,
                     final String caption,
                     final ComponentSelectionHandler handler,
                     final FieldSelectionListModel fields,
                     final DashboardContext dashboardContext,
                     final Consumer<ComponentSelectionHandler> onOk) {
        read(handler, fields);

        currentSelection.refresh(dashboardContext, true);
        final HandlerRegistration registration = dashboardContext.addContextChangeListener(() ->
                currentSelection.refresh(dashboardContext, true));
        onDispose(registration::removeHandler);

        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.resizable(1200, 1000, 800, 600))
                .caption(caption)
                .onShow(e -> view.focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        onOk.accept(write());
                    }
                    e.hide();
                })
                .fire();
    }

    private void read(final ComponentSelectionHandler componentSelectionHandler,
                      final FieldSelectionListModel fieldSelectionListModel) {
        this.originalHandler = componentSelectionHandler;
        // Shows its requests' progress as this controller's context does
        fieldSelectionListModel.setTaskMonitorFactory(() -> taskMonitor().createTaskMonitor());
        expressionEditor.init(null, fieldSelectionListModel);
        if (componentSelectionHandler.getExpression() == null) {
            expressionEditor.read(ExpressionOperator.builder().build());
        } else {
            expressionEditor.read(componentSelectionHandler.getExpression());
        }
        view.setEnabled(componentSelectionHandler.isEnabled());
    }

    private ComponentSelectionHandler write() {
        final String id;
        if (originalHandler != null && originalHandler.getId() != null) {
            id = originalHandler.getId();
        } else {
            id = RandomId.createId(5);
        }

        final ExpressionOperator expression = expressionEditor.write();
        return ComponentSelectionHandler
                .builder()
                .id(id)
                .expression(expression)
                .enabled(view.isEnabled())
                .build();
    }

}
