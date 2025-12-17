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

package stroom.dashboard.client.query;

import stroom.dashboard.client.main.DashboardContext;
import stroom.dashboard.client.query.SelectionHandlerPresenter.SelectionHandlerView;
import stroom.dashboard.client.table.cf.EditExpressionPresenter;
import stroom.dashboard.shared.ComponentSelectionHandler;
import stroom.query.api.ExpressionOperator;
import stroom.query.client.presenter.FieldSelectionListModel;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.shared.RandomId;

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

public class SelectionHandlerPresenter
        extends MyPresenterWidget<SelectionHandlerView>
        implements Focus {

    private final EditExpressionPresenter editExpressionPresenter;
    private final CurrentSelectionPresenter currentSelectionPresenter;
    private FieldSelectionListModel fieldSelectionListModel;
    private ComponentSelectionHandler originalHandler;

    @Inject
    public SelectionHandlerPresenter(final EventBus eventBus,
                                     final SelectionHandlerView view,
                                     final EditExpressionPresenter editExpressionPresenter,
                                     final CurrentSelectionPresenter currentSelectionPresenter) {
        super(eventBus, view);
        this.editExpressionPresenter = editExpressionPresenter;
        this.currentSelectionPresenter = currentSelectionPresenter;
        view.setExpressionView(editExpressionPresenter.getView());
        view.setCurrentSelection(currentSelectionPresenter.getView());

        currentSelectionPresenter.setInsertHandler(editExpressionPresenter::insertValue);
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
        editExpressionPresenter.init(null, null, fieldSelectionListModel);
        this.originalHandler = componentSelectionHandler;
        if (componentSelectionHandler.getExpression() == null) {
            editExpressionPresenter.read(ExpressionOperator.builder().build());
        } else {
            editExpressionPresenter.read(componentSelectionHandler.getExpression());
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

        final ExpressionOperator expression = editExpressionPresenter.write();
        return ComponentSelectionHandler
                .builder()
                .id(id)
                .expression(expression)
                .enabled(getView().isEnabled())
                .build();
    }

    @Override
    public synchronized void setTaskMonitorFactory(final TaskMonitorFactory taskMonitorFactory) {
        super.setTaskMonitorFactory(taskMonitorFactory);
        fieldSelectionListModel.setTaskMonitorFactory(taskMonitorFactory);
    }

    public interface SelectionHandlerView extends View, Focus {

        void setExpressionView(View view);

        boolean isEnabled();

        void setEnabled(boolean enabled);

        void setCurrentSelection(final View view);
    }
}
