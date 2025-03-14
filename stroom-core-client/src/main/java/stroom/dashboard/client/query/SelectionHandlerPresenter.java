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

import stroom.dashboard.client.main.Component;
import stroom.dashboard.client.main.Components;
import stroom.dashboard.client.query.SelectionHandlerPresenter.SelectionHandlerView;
import stroom.dashboard.client.table.ComponentSelection;
import stroom.dashboard.client.table.HasComponentSelection;
import stroom.dashboard.client.table.cf.EditExpressionPresenter;
import stroom.dashboard.shared.ComponentSelectionHandler;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.client.presenter.FieldSelectionListModel;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.shared.RandomId;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;
import stroom.widget.util.client.TableBuilder;
import stroom.widget.util.client.TableCell;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public class SelectionHandlerPresenter
        extends MyPresenterWidget<SelectionHandlerView>
        implements SelectionHandlerUiHandlers, Focus {

    private final EditExpressionPresenter editExpressionPresenter;
    private FieldSelectionListModel fieldSelectionListModel;
    private ComponentSelectionHandler originalHandler;
    private Components components;

    @Inject
    public SelectionHandlerPresenter(final EventBus eventBus,
                                     final SelectionHandlerView view,
                                     final EditExpressionPresenter editExpressionPresenter) {
        super(eventBus, view);
        this.editExpressionPresenter = editExpressionPresenter;
        view.setExpressionView(editExpressionPresenter.getView());
        view.setUiHandlers(this);
    }

    @Override
    public void focus() {
        getView().focus();
    }

    void read(final ComponentSelectionHandler componentSelectionHandler,
              final List<Component> componentList,
              final FieldSelectionListModel fieldSelectionListModel) {
        this.fieldSelectionListModel = fieldSelectionListModel;
        getView().setComponentList(componentList);
        final Optional<Component> optionalComponent = componentList
                .stream()
                .filter(c -> c.getId().equals(componentSelectionHandler.getComponentId()))
                .findAny();
        getView().setComponent(optionalComponent.orElse(null));

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

        if (components != null && components.getComponents() != null) {
            getView().setCurrentSelection(getCurrentSelection(components.getComponents()));
        }
    }

    private SafeHtml getCurrentSelection(final Collection<Component> components) {
        final TableBuilder tb = new TableBuilder();
        for (final Component component : components) {
            appendComponentSelection(component, tb);
        }
        final HtmlBuilder htmlBuilder = new HtmlBuilder();
        htmlBuilder.div(tb::write, Attribute.className("infoTable"));
        return htmlBuilder.toSafeHtml();
    }

    private void appendComponentSelection(final Component component,
                                          final TableBuilder tb) {
        if (component instanceof HasComponentSelection) {
            final HasComponentSelection hasComponentSelection = (HasComponentSelection) component;
            final List<ComponentSelection> componentSelections = hasComponentSelection.getSelection();

            if (componentSelections != null) {
                boolean firstSelection = true;
                for (final ComponentSelection componentSelection : componentSelections) {
                    if (firstSelection) {
                        tb.row(TableCell.header(component.getDisplayValue()));
                    }
                    tb.row(componentSelection.asSafeHtml());
                    firstSelection = false;
                }
            }
        }
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

    @Override
    public synchronized void setTaskMonitorFactory(final TaskMonitorFactory taskMonitorFactory) {
        super.setTaskMonitorFactory(taskMonitorFactory);
        fieldSelectionListModel.setTaskMonitorFactory(taskMonitorFactory);
    }

    public void setComponents(final Components components) {
        this.components = components;
    }

    public interface SelectionHandlerView extends View, Focus, HasUiHandlers<SelectionHandlerUiHandlers> {

        void setComponentList(List<Component> componentList);

        Component getComponent();

        void setComponent(Component component);

        void setExpressionView(View view);

        boolean isEnabled();

        void setEnabled(boolean enabled);

        void setCurrentSelection(final SafeHtml selection);
    }
}
