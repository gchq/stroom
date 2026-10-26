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

package stroom.dashboard.client.query;

import stroom.dashboard.client.main.DashboardContext;
import stroom.dashboard.client.query.SelectionHandlerPresenter.SelectionHandlerView;
import stroom.dashboard.shared.ComponentSelectionHandler;
import stroom.query.api.ExpressionOperator;
import stroom.query.client.component.ExpressionEditorController;
import stroom.query.client.presenter.FieldSelectionListModel;
import stroom.util.shared.RandomId;
import stroom.widget.presenter.client.MyPresenterWidget;
import stroom.widget.presenter.client.View;

import com.google.gwt.user.client.ui.Focus;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

public class SelectionHandlerPresenter
        extends MyPresenterWidget<SelectionHandlerView>
        implements Focus {

    private final ExpressionEditorController expressionEditor;
    private final CurrentSelectionPresenter currentSelectionPresenter;
    private FieldSelectionListModel fieldSelectionListModel;
    private ComponentSelectionHandler originalHandler;

    @Inject
    public SelectionHandlerPresenter(final EventBus eventBus,
                                     final SelectionHandlerView view,
                                     final ExpressionEditorController expressionEditor,
                                     final CurrentSelectionPresenter currentSelectionPresenter) {
        super(eventBus, view);
        own(expressionEditor);
        own(currentSelectionPresenter);
        this.expressionEditor = expressionEditor;
        this.currentSelectionPresenter = currentSelectionPresenter;
        // Its slot frames the expression's editor itself
        expressionEditor.setFramed(false);
        view.setExpressionView(expressionEditor);
        view.setCurrentSelection(currentSelectionPresenter.getView());

        currentSelectionPresenter.setInsertHandler(expressionEditor::insertValue);
    }

    @Override
    public void focus() {
        getView().focus();
    }

    void read(final ComponentSelectionHandler componentSelectionHandler,
              final FieldSelectionListModel fieldSelectionListModel) {
        this.fieldSelectionListModel = fieldSelectionListModel;

        this.originalHandler = componentSelectionHandler;
        fieldSelectionListModel.setTaskMonitorFactory(this);
        expressionEditor.init(null, fieldSelectionListModel);
        this.originalHandler = componentSelectionHandler;
        if (componentSelectionHandler.getExpression() == null) {
            expressionEditor.read(ExpressionOperator.builder().build());
        } else {
            expressionEditor.read(componentSelectionHandler.getExpression());
        }
        getView().setEnabled(componentSelectionHandler.isEnabled());
    }

    void refreshSelection(final DashboardContext dashboardContext) {
        currentSelectionPresenter.refresh(dashboardContext, true);
    }

    ComponentSelectionHandler write() {
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
                .enabled(getView().isEnabled())
                .build();
    }

    public interface SelectionHandlerView extends View, Focus {

        void setExpressionView(IsWidget view);

        boolean isEnabled();

        void setEnabled(boolean enabled);

        void setCurrentSelection(final View view);
    }
}
