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

import stroom.alert.client.event.ConfirmEvent;
import stroom.dashboard.client.embeddedquery.EmbeddedQueryPresenter;
import stroom.dashboard.client.main.AbstractSettingsTabPresenter;
import stroom.dashboard.client.main.Component;
import stroom.dashboard.client.main.DashboardContext;
import stroom.dashboard.client.query.SelectionHandlersPresenter.SelectionHandlersView;
import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.ComponentSelectionHandler;
import stroom.dashboard.shared.ComponentSettings;
import stroom.dashboard.shared.ComponentSettings.AbstractBuilder;
import stroom.dashboard.shared.HasSelectionFilter;
import stroom.dashboard.shared.HasSelectionFilterBuilder;
import stroom.dashboard.shared.HasSelectionQuery;
import stroom.dashboard.shared.HasSelectionQueryBuilder;
import stroom.dashboard.shared.TableComponentSettings;
import stroom.docref.DocRef;
import stroom.document.client.event.DirtyEvent;
import stroom.document.client.event.DirtyEvent.DirtyHandler;
import stroom.document.client.event.HasDirtyHandlers;
import stroom.query.api.Column;
import stroom.query.api.Format;
import stroom.query.api.datasource.ConditionSet;
import stroom.query.api.datasource.FieldType;
import stroom.query.api.datasource.QueryField;
import stroom.query.client.presenter.DynamicFieldSelectionListModel;
import stroom.query.client.presenter.FieldSelectionListModel;
import stroom.query.client.presenter.QueryResultTablePresenter;
import stroom.query.client.presenter.SimpleFieldSelectionListModel;
import stroom.svg.client.SvgPresets;
import stroom.util.shared.NullSafe;
import stroom.util.shared.RandomId;
import stroom.widget.button.client.ButtonView;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.dom.client.Style.BorderStyle;
import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.View;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class SelectionHandlersPresenter
        extends AbstractSettingsTabPresenter<SelectionHandlersView>
        implements HasDirtyHandlers, Focus {

    private final SelectionHandlerListPresenter listPresenter;
    private final Provider<SelectionHandlerPresenter> editRulePresenterProvider;
    private final DynamicFieldSelectionListModel dynamicFieldSelectionListModel;
    private List<ComponentSelectionHandler> selectionHandlers = new ArrayList<>();

    private final ButtonView addButton;
    private final ButtonView editButton;
    private final ButtonView copyButton;
    private final ButtonView disableButton;
    private final ButtonView deleteButton;
    private final ButtonView moveUpButton;
    private final ButtonView moveDownButton;

    private boolean dirty;
    private FieldSelectionListModel fieldSelectionListModel;
    // Determine if we are using this to set a filter or query selection handler.
    private boolean useForFilter;

    @Inject
    public SelectionHandlersPresenter(final EventBus eventBus,
                                      final SelectionHandlersView view,
                                      final SelectionHandlerListPresenter listPresenter,
                                      final Provider<SelectionHandlerPresenter> editRulePresenterProvider,
                                      final DynamicFieldSelectionListModel dynamicFieldSelectionListModel) {
        super(eventBus, view);
        this.dynamicFieldSelectionListModel = dynamicFieldSelectionListModel;
        this.listPresenter = listPresenter;
//        this.expressionPresenter = expressionPresenter;
        this.editRulePresenterProvider = editRulePresenterProvider;

        getView().setTableView(listPresenter.getView());
//        getView().setExpressionView(expressionPresenter.getView());

        // Stop users from selecting expression items.
//        expressionPresenter.setSelectionModel(null);

        addButton = listPresenter.add(SvgPresets.ADD);
        editButton = listPresenter.add(SvgPresets.EDIT);
        copyButton = listPresenter.add(SvgPresets.COPY);
        disableButton = listPresenter.add(SvgPresets.DISABLE);
        deleteButton = listPresenter.add(SvgPresets.DELETE);
        moveUpButton = listPresenter.add(SvgPresets.UP);
        moveDownButton = listPresenter.add(SvgPresets.DOWN);

        listPresenter.getView().asWidget().getElement().getStyle().setBorderStyle(BorderStyle.NONE);

        updateButtons();
    }

    @Override
    public void focus() {
        addButton.focus();
    }

    public void setDataSourceRefConsumer(final Consumer<Consumer<DocRef>> dataSourceRefConsumer) {
        dynamicFieldSelectionListModel.setDataSourceRefConsumer(dataSourceRefConsumer);
    }

    @Override
    protected void onBind() {
        registerHandler(addButton.addClickHandler(event ->
                add()));
        registerHandler(editButton.addClickHandler(event -> {
            final ComponentSelectionHandler selected = listPresenter.getSelectionModel().getSelected();
            if (selected != null) {
                edit(selected);
            }
        }));
        registerHandler(copyButton.addClickHandler(event -> {
            final ComponentSelectionHandler selected = listPresenter.getSelectionModel().getSelected();
            if (selected != null) {
                final ComponentSelectionHandler listener = selected
                        .copy()
                        .build();

                final int index = selectionHandlers.indexOf(selected);

                if (index < selectionHandlers.size() - 1) {
                    selectionHandlers.add(index + 1, listener);
                } else {
                    selectionHandlers.add(listener);
                }

                update();
                listPresenter.getSelectionModel().setSelected(listener);
            }
        }));
        registerHandler(disableButton.addClickHandler(event -> {
            final ComponentSelectionHandler selected = listPresenter.getSelectionModel().getSelected();
            if (selected != null) {
                final ComponentSelectionHandler newRule = selected
                        .copy()
                        .enabled(!selected.isEnabled())
                        .build();
                final int index = selectionHandlers.indexOf(selected);
                selectionHandlers.remove(index);
                selectionHandlers.add(index, newRule);
                listPresenter.getSelectionModel().setSelected(newRule);
                update();
                setDirty(true);
            }
        }));
        registerHandler(deleteButton.addClickHandler(event ->
                ConfirmEvent.fire(this, "Are you sure you want to delete this item?", ok -> {
                    if (ok) {
                        final ComponentSelectionHandler rule = listPresenter.getSelectionModel().getSelected();
                        selectionHandlers.remove(rule);
                        listPresenter.getSelectionModel().clear();
                        update();
                        setDirty(true);
                    }
                })));
        registerHandler(moveUpButton.addClickHandler(event -> {
            final ComponentSelectionHandler rule = listPresenter.getSelectionModel().getSelected();
            if (rule != null) {
                final int index = selectionHandlers.indexOf(rule);
                if (index > 0) {
                    selectionHandlers.remove(rule);
                    selectionHandlers.add(index - 1, rule);
                    update();
                    setDirty(true);
                }
            }
        }));
        registerHandler(moveDownButton.addClickHandler(event -> {
            final ComponentSelectionHandler rule = listPresenter.getSelectionModel().getSelected();
            if (rule != null) {
                final int index = selectionHandlers.indexOf(rule);
                if (index < selectionHandlers.size() - 1) {
                    selectionHandlers.remove(rule);
                    selectionHandlers.add(index + 1, rule);
                    update();
                    setDirty(true);
                }
            }
        }));
        registerHandler(listPresenter.getSelectionModel().addSelectionHandler(event -> {
            final ComponentSelectionHandler rule = listPresenter.getSelectionModel().getSelected();
            if (rule != null) {
//                expressionPresenter.read(rule.getExpression());
                if (event.getSelectionType().isDoubleSelect()) {
                    edit(rule);
                }
            } else {
//                expressionPresenter.read(null);
            }
            updateButtons();
        }));

        super.onBind();
    }

    private void add() {
        final ComponentSelectionHandler newRule = ComponentSelectionHandler
                .builder()
                .id(RandomId.createId(5))
                .enabled(true)
                .build();
        edit(newRule, "Add New Selection Handler", rule -> {
            selectionHandlers.add(rule);
            update();
            listPresenter.getSelectionModel().setSelected(rule);
            setDirty(true);
        });
    }

    private void edit(final ComponentSelectionHandler existingRule) {
        edit(existingRule, "Edit Selection Handler", rule -> {
            final int index = selectionHandlers.indexOf(existingRule);
            selectionHandlers.remove(index);
            selectionHandlers.add(index, rule);

            update();
            listPresenter.getSelectionModel().setSelected(rule);

            // Only mark the policies as dirty if the rule was actually changed.
            if (!existingRule.equals(rule)) {
                setDirty(true);
            }
        });
    }

    private void edit(final ComponentSelectionHandler existingRule,
                      final String caption,
                      final Consumer<ComponentSelectionHandler> consumer) {
        final SelectionHandlerPresenter editSelectionHandlerPresenter = editRulePresenterProvider.get();
        editSelectionHandlerPresenter.read(existingRule, fieldSelectionListModel);

        final DashboardContext dashboardContext = getDashboardContext();
        editSelectionHandlerPresenter.refreshSelection(dashboardContext);
        final HandlerRegistration handlerRegistration = dashboardContext
                .addContextChangeHandler(e -> editSelectionHandlerPresenter.refreshSelection(dashboardContext));

        final PopupSize popupSize = PopupSize.resizable(1200, 1000, 800, 600);
        ShowPopupEvent.builder(editSelectionHandlerPresenter)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(popupSize)
                .caption(caption)
                .onShow(e -> editSelectionHandlerPresenter.focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        final ComponentSelectionHandler rule = editSelectionHandlerPresenter.write();
                        consumer.accept(rule);
                    }
                    e.hide();
                })
                .onHide(e -> handlerRegistration.removeHandler())
                .fire();
    }

    @Override
    public void read(final ComponentConfig componentConfig) {

        // Get field list model.
        fieldSelectionListModel = dynamicFieldSelectionListModel;
        if (componentConfig.getSettings() instanceof final TableComponentSettings settings) {
            fieldSelectionListModel = createSelectionListModelFromColumns(settings.getColumns());
        } else if (useForFilter) {
            final Component component = getDashboardContext().getComponents().get(componentConfig.getId());
            if (component instanceof final EmbeddedQueryPresenter embeddedQueryPresenter) {
                List<Column> columns = Collections.emptyList();
                final QueryResultTablePresenter queryResultTablePresenter =
                        embeddedQueryPresenter.getCurrentTablePresenter();
                if (queryResultTablePresenter != null) {
                    columns = NullSafe.list(queryResultTablePresenter.getCurrentColumns());
                }
                fieldSelectionListModel = createSelectionListModelFromColumns(columns);
            }
        }

        // Read selection handlers.
        if (useForFilter) {
            if (componentConfig.getSettings() instanceof final HasSelectionFilter hasSelectionFilter) {
                if (hasSelectionFilter.getSelectionFilter() != null) {
                    this.selectionHandlers = hasSelectionFilter.getSelectionFilter();
                } else {
                    this.selectionHandlers.clear();
                }
            }
        } else if (componentConfig.getSettings() instanceof final HasSelectionQuery hasSelectionQuery) {
            if (hasSelectionQuery.getSelectionQuery() != null) {
                this.selectionHandlers = hasSelectionQuery.getSelectionQuery();
            } else {
                this.selectionHandlers.clear();
            }
        }

        listPresenter.getSelectionModel().clear();
        setDirty(false);
        update();
    }

    private FieldSelectionListModel createSelectionListModelFromColumns(final List<Column> columns) {
        final List<QueryField> fields = columns
                .stream()
                .map(column -> {
                    final FieldType fieldType = getFieldType(column);
                    final ConditionSet conditionSet = switch (fieldType) {
                        case LONG -> ConditionSet.ALL_UI_NUMERIC;
                        case DATE -> ConditionSet.ALL_UI_DATE;
                        default -> ConditionSet.ALL_UI_TEXT;
                    };

                    return QueryField
                            .builder()
                            .fldName(column.getName())
                            .fldType(getFieldType(column))
                            .queryable(true)
                            .conditionSet(conditionSet)
                            .build();
                })
                .collect(Collectors.toList());
        final SimpleFieldSelectionListModel simpleFieldSelectionListModel = new SimpleFieldSelectionListModel();
        simpleFieldSelectionListModel.addItems(fields);
        return simpleFieldSelectionListModel;
    }

    private FieldType getFieldType(final Column column) {
        final Format format = column.getFormat();
        if (format != null && format.getType() != null) {
            switch (format.getType()) {
                case NUMBER: {
                    return FieldType.LONG;
                }
                case DATE_TIME: {
                    return FieldType.DATE;
                }
            }
        }
        return FieldType.TEXT;
    }

    @Override
    public ComponentConfig write(final ComponentConfig componentConfig) {
        final AbstractBuilder<?, ?> builder = componentConfig.getSettings().copy();

        if (useForFilter) {
            if (builder instanceof final HasSelectionFilterBuilder<?, ?> hasSelectionFilter) {
                hasSelectionFilter.selectionFilter(selectionHandlers);
            }
        } else {
            if (builder instanceof final HasSelectionQueryBuilder<?, ?> hasSelectionQuery) {
                hasSelectionQuery.selectionQuery(selectionHandlers);
            }
        }

        final ComponentSettings newSettings = builder.build();
        return componentConfig.copy().settings(newSettings).build();
    }

    @Override
    public boolean validate() {
        return true;
    }

    @Override
    public boolean isDirty(final ComponentConfig componentConfig) {
        return dirty;
    }

    private void update() {
        listPresenter.setData(selectionHandlers);
        updateButtons();
    }

    @Override
    public void setDashboardContext(final DashboardContext dashboardContext) {
        listPresenter.setComponents(dashboardContext.getComponents());
        super.setDashboardContext(dashboardContext);
    }

    private void updateButtons() {
        final boolean loadedPolicy = selectionHandlers != null;
        final ComponentSelectionHandler selection = listPresenter.getSelectionModel().getSelected();
        final boolean selected = loadedPolicy && selection != null;
        int index = -1;
        if (selected) {
            index = selectionHandlers.indexOf(selection);
        }

        if (selection != null && selection.isEnabled()) {
            disableButton.setTitle("Disable");
        } else {
            disableButton.setTitle("Enable");
        }

        addButton.setEnabled(loadedPolicy);
        editButton.setEnabled(selected);
        copyButton.setEnabled(selected);
        disableButton.setEnabled(selected);
        deleteButton.setEnabled(selected);
        moveUpButton.setEnabled(selected && index > 0);
        moveDownButton.setEnabled(selected && index >= 0 && index < selectionHandlers.size() - 1);
    }

    private void setDirty(final boolean dirty) {
        if (this.dirty != dirty) {
            this.dirty = dirty;
            DirtyEvent.fire(this, dirty);
        }
    }

    @Override
    public HandlerRegistration addDirtyHandler(final DirtyHandler handler) {
        return addHandlerToSource(DirtyEvent.getType(), handler);
    }

    public void setUseForFilter(final boolean useForFilter) {
        this.useForFilter = useForFilter;
    }

    public interface SelectionHandlersView extends View {

        void setTableView(View view);
    }
}
