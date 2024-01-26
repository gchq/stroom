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

import stroom.dashboard.client.HasSelection;
import stroom.dashboard.client.main.Component;
import stroom.dashboard.client.query.SelectionHandlerPresenter.SelectionHandlerView;
import stroom.dashboard.client.table.cf.EditExpressionPresenter;
import stroom.dashboard.shared.ComponentSelectionHandler;
import stroom.datasource.api.v2.QueryField;
import stroom.docref.DocRef;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.client.presenter.DynamicFieldSelectionListModel;
import stroom.util.shared.RandomId;

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.List;
import java.util.Optional;

public class SelectionHandlerPresenter
        extends MyPresenterWidget<SelectionHandlerView>
        implements SelectionHandlerUiHandlers, Focus {

    private final EditExpressionPresenter editExpressionPresenter;
    private final DynamicFieldSelectionListModel fieldSelectionBoxModel;
    private ComponentSelectionHandler originalHandler;

    @Inject
    public SelectionHandlerPresenter(final EventBus eventBus,
                                     final SelectionHandlerView view,
                                     final EditExpressionPresenter editExpressionPresenter,
                                     final DynamicFieldSelectionListModel fieldSelectionBoxModel) {
        super(eventBus, view);
        this.editExpressionPresenter = editExpressionPresenter;
        this.fieldSelectionBoxModel = fieldSelectionBoxModel;
        view.setExpressionView(editExpressionPresenter.getView());
        view.setUiHandlers(this);
    }

    @Override
    public void focus() {
        getView().focus();
    }

    void read(final ComponentSelectionHandler componentSelectionHandler,
              final List<Component> componentList,
              final DocRef dataSourceRef) {
        getView().setComponentList(componentList);
        final Optional<Component> optionalComponent = componentList
                .stream()
                .filter(c -> c.getId().equals(componentSelectionHandler.getComponentId()))
                .findAny();
        getView().setComponent(optionalComponent.orElse(null));

        this.originalHandler = componentSelectionHandler;
        fieldSelectionBoxModel.setDataSourceRef(dataSourceRef);
        editExpressionPresenter.init(null, null, fieldSelectionBoxModel);
        this.originalHandler = componentSelectionHandler;
        if (componentSelectionHandler.getExpression() == null) {
            editExpressionPresenter.read(ExpressionOperator.builder().build());
        } else {
            editExpressionPresenter.read(componentSelectionHandler.getExpression());
        }
        getView().setEnabled(componentSelectionHandler.isEnabled());
    }

    ComponentSelectionHandler write() {
        final String id;
        if (originalHandler != null && originalHandler.getId() != null) {
            id = originalHandler.getId();
        } else {
            id = RandomId.createId(5);
        }

        final Component component = getView().getComponent();
        final String componentId;
        if (component != null) {
            componentId = component.getId();
        } else {
            componentId = null;
        }

        final ExpressionOperator expression = editExpressionPresenter.write();
        return ComponentSelectionHandler
                .builder()
                .id(id)
                .componentId(componentId)
                .expression(expression)
                .enabled(getView().isEnabled())
                .build();
    }

    @Override
    public void onComponentChange() {
        updateFieldNames(getView().getComponent());
    }

    private void updateFieldNames(final Component component) {
//        if (!ignoreTableChange) {
//            allFields.clear();
//            if (component == null) {
//                if (componentList != null) {
//                    componentList.forEach(c -> addFieldNames(c, allFields));
//                }
//            } else {
//                addFieldNames(component, allFields);
//            }
//            editExpressionPresenter.init(null, null, allFields);
//        }
    }

    private void addFieldNames(final Component component, final List<QueryField> allFields) {
        if (component instanceof HasSelection) {
            final HasSelection hasSelection = (HasSelection) component;
            final List<QueryField> fields = hasSelection.getFields();
            if (fields != null && fields.size() > 0) {
                allFields.addAll(fields);
            }
        }
    }

    public interface SelectionHandlerView extends View, Focus, HasUiHandlers<SelectionHandlerUiHandlers> {

        void setComponentList(List<Component> componentList);

        Component getComponent();

        void setComponent(Component component);

        void setExpressionView(View view);

        boolean isEnabled();

        void setEnabled(boolean enabled);
    }
}
