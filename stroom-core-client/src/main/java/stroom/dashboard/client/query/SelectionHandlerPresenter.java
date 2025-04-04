/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.dashboard.client.query;

import stroom.dashboard.client.main.DashboardContext;
import stroom.dashboard.client.query.SelectionHandlerPresenter.SelectionHandlerView;
import stroom.dashboard.client.table.cf.EditExpressionPresenter;
import stroom.dashboard.shared.ComponentSelectionHandler;
import stroom.data.client.presenter.CopyTextUtil;
import stroom.editor.client.presenter.HtmlPresenter;
import stroom.query.api.ExpressionOperator;
import stroom.query.client.presenter.FieldSelectionListModel;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.shared.RandomId;

import com.google.gwt.event.dom.client.MouseDownEvent;
import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

public class SelectionHandlerPresenter
        extends MyPresenterWidget<SelectionHandlerView>
        implements Focus {

    private final EditExpressionPresenter editExpressionPresenter;
    private final HtmlPresenter htmlPresenter;
    private FieldSelectionListModel fieldSelectionListModel;
    private ComponentSelectionHandler originalHandler;
    private DashboardContext dashboardContext;

    @Inject
    public SelectionHandlerPresenter(final EventBus eventBus,
                                     final SelectionHandlerView view,
                                     final EditExpressionPresenter editExpressionPresenter,
                                     final HtmlPresenter htmlPresenter) {
        super(eventBus, view);
        this.editExpressionPresenter = editExpressionPresenter;
        this.htmlPresenter = htmlPresenter;
        view.setExpressionView(editExpressionPresenter.getView());
        view.setCurrentSelection(htmlPresenter.getView());
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(htmlPresenter.getWidget().addDomHandler(e ->
                CopyTextUtil.onClick(e.getNativeEvent(), this), MouseDownEvent.getType()));
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
        htmlPresenter.getView().setHtml(dashboardContext.toSafeHtml().asString());
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

    public void setDashboardContext(final DashboardContext dashboardContext) {
        this.dashboardContext = dashboardContext;
    }

    public interface SelectionHandlerView extends View, Focus {

        void setExpressionView(View view);

        boolean isEnabled();

        void setEnabled(boolean enabled);

        void setCurrentSelection(final View view);
    }
}
