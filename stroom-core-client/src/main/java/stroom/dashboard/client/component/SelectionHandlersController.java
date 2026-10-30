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

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.dashboard.client.component.SelectionHandlers.SelectionHandlersAction;
import stroom.dashboard.client.main.DashboardPane;
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
import stroom.query.api.Column;
import stroom.query.api.Format;
import stroom.query.api.datasource.ConditionSet;
import stroom.query.api.datasource.FieldType;
import stroom.query.api.datasource.QueryField;
import stroom.query.client.component.QueryResultTableController;
import stroom.query.client.model.DynamicFieldSelectionListModel;
import stroom.query.client.model.FieldSelectionListModel;
import stroom.query.client.model.SimpleFieldSelectionListModel;
import stroom.util.shared.NullSafe;
import stroom.util.shared.RandomId;

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/// A pane's Selection Query or Selection Filter tab ([SelectionHandlers]): the handlers that change
/// the pane's query or filter when a row of another pane is selected. It adds, edits, copies,
/// enables or disables, deletes and moves them.
public class SelectionHandlersController extends SettingsTabController implements Focus {

    private final SelectionHandlers view;
    private final SelectionHandlerList selectionHandlerList;
    private final Provider<SelectionHandlerEditorController> handlerEditorProvider;
    private final DynamicFieldSelectionListModel dynamicFieldSelectionListModel;
    private List<ComponentSelectionHandler> selectionHandlers = new ArrayList<>();

    private boolean dirty;
    private FieldSelectionListModel fieldSelectionListModel;
    // Determine if we are using this to set a filter or query selection handler.
    private boolean useForFilter;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus                       The application's event bus.
    /// @param view                           The content.
    /// @param handlerEditorProvider          Makes the dialog that adds or edits a handler.
    /// @param dynamicFieldSelectionListModel Offers the fields of the pane's data source.
    @Inject
    public SelectionHandlersController(final EventBus eventBus,
                                       final SelectionHandlers view,
                                       final Provider<SelectionHandlerEditorController> handlerEditorProvider,
                                       final DynamicFieldSelectionListModel dynamicFieldSelectionListModel) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        this.dynamicFieldSelectionListModel = dynamicFieldSelectionListModel;
        this.selectionHandlerList = view.getHandlerList();
        this.handlerEditorProvider = handlerEditorProvider;
        view.setOnAction(this::onAction);

        updateButtons();

        own(selectionHandlerList.getSelectionModel().addSelectionHandler(event -> {
            final ComponentSelectionHandler rule = selectionHandlerList.getSelectionModel().getSelected();
            if (rule != null && event.getSelectionType().isDoubleSelect()) {
                edit(rule);
            }
            updateButtons();
        }));
    }

    private void onAction(final SelectionHandlersAction action) {
        switch (action) {
            case ADD -> add();
            case EDIT -> onEdit();
            case COPY -> onCopy();
            case TOGGLE -> onDisable();
            case DELETE -> confirmDelete();
            case MOVE_UP -> onMoveUp();
            case MOVE_DOWN -> onMoveDown();
        }
    }

    @Override
    public void focus() {
        view.focusAdd();
    }

    /// @param dataSourceRefConsumer Gives the pane's data source, whose fields the handlers' expressions
    ///                              can use.
    public void setDataSourceRefConsumer(final Consumer<Consumer<DocRef>> dataSourceRefConsumer) {
        dynamicFieldSelectionListModel.setDataSourceRefConsumer(dataSourceRefConsumer);
    }

    private void onEdit() {
        final ComponentSelectionHandler selected = selectionHandlerList.getSelectionModel().getSelected();
        if (selected != null) {
            edit(selected);
        }
    }

    /// A copy of a selection handler with an id of its own, as a new one gets; a copy that kept its
    /// original's id could be taken for it by anything that finds a handler by id.
    ///
    /// @param handler The handler to copy.
    /// @return The copy.
    static ComponentSelectionHandler duplicate(final ComponentSelectionHandler handler) {
        return handler
                .copy()
                .id(RandomId.createId(5))
                .build();
    }

    private void onCopy() {
        final ComponentSelectionHandler selected = selectionHandlerList.getSelectionModel().getSelected();
        if (selected != null) {
            final ComponentSelectionHandler listener = duplicate(selected);

            final int index = selectionHandlers.indexOf(selected);

            if (index < selectionHandlers.size() - 1) {
                selectionHandlers.add(index + 1, listener);
            } else {
                selectionHandlers.add(listener);
            }

            update();
            selectionHandlerList.getSelectionModel().setSelected(listener);
        }
    }

    private void onDisable() {
        final ComponentSelectionHandler selected = selectionHandlerList.getSelectionModel().getSelected();
        if (selected != null) {
            final ComponentSelectionHandler newRule = selected
                    .copy()
                    .enabled(!selected.isEnabled())
                    .build();
            final int index = selectionHandlers.indexOf(selected);
            selectionHandlers.remove(index);
            selectionHandlers.add(index, newRule);
            selectionHandlerList.getSelectionModel().setSelected(newRule);
            update();
            setDirty(true);
        }
    }

    private void onMoveUp() {
        final ComponentSelectionHandler rule = selectionHandlerList.getSelectionModel().getSelected();
        if (rule != null) {
            final int index = selectionHandlers.indexOf(rule);
            if (index > 0) {
                selectionHandlers.remove(rule);
                selectionHandlers.add(index - 1, rule);
                update();
                setDirty(true);
            }
        }
    }

    private void onMoveDown() {
        final ComponentSelectionHandler rule = selectionHandlerList.getSelectionModel().getSelected();
        if (rule != null) {
            final int index = selectionHandlers.indexOf(rule);
            if (index < selectionHandlers.size() - 1) {
                selectionHandlers.remove(rule);
                selectionHandlers.add(index + 1, rule);
                update();
                setDirty(true);
            }
        }
    }

    private void confirmDelete() {
        ConfirmEvent.fire(this, "Are you sure you want to delete this item?", ok -> {
            if (ok) {
                final ComponentSelectionHandler rule = selectionHandlerList.getSelectionModel().getSelected();
                selectionHandlers.remove(rule);
                selectionHandlerList.getSelectionModel().clear();
                update();
                setDirty(true);
            }
        });
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
            selectionHandlerList.getSelectionModel().setSelected(rule);
            setDirty(true);
        });
    }

    private void edit(final ComponentSelectionHandler existingRule) {
        edit(existingRule, "Edit Selection Handler", rule -> {
            final int index = selectionHandlers.indexOf(existingRule);
            if (index < 0) {
                // Deleted behind the dialog, which isn't modal
                AlertEvent.fireWarn(this, "The selection handler was deleted while it was being edited, "
                                          + "so the changes can't be kept.", null);
                return;
            }
            selectionHandlers.remove(index);
            selectionHandlers.add(index, rule);

            update();
            selectionHandlerList.getSelectionModel().setSelected(rule);

            // Only mark the policies as dirty if the rule was actually changed.
            if (!existingRule.equals(rule)) {
                setDirty(true);
            }
        });
    }

    private void edit(final ComponentSelectionHandler existingRule,
                      final String caption,
                      final Consumer<ComponentSelectionHandler> consumer) {
        // Made for one use, and disposed once it is closed
        handlerEditorProvider.get().edit(this,
                caption,
                existingRule,
                fieldSelectionListModel,
                getDashboardContext(),
                consumer);
    }

    @Override
    public void read(final ComponentConfig componentConfig) {

        // Get field list model.
        fieldSelectionListModel = dynamicFieldSelectionListModel;
        if (componentConfig.getSettings() instanceof final TableComponentSettings settings) {
            fieldSelectionListModel = createSelectionListModelFromColumns(settings.getColumns());
        } else if (useForFilter) {
            final DashboardPane component = getDashboardContext().getComponents().get(componentConfig.getId());
            if (component instanceof final EmbeddedQueryController embeddedQuery) {
                List<Column> columns = Collections.emptyList();
                final QueryResultTableController queryResultTableController =
                        embeddedQuery.getCurrentTableController();
                if (queryResultTableController != null) {
                    columns = NullSafe.list(queryResultTableController.getCurrentColumns());
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

        selectionHandlerList.getSelectionModel().clear();
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
        selectionHandlerList.setData(selectionHandlers);
        updateButtons();
    }

    private void updateButtons() {
        final boolean loadedPolicy = selectionHandlers != null;
        final ComponentSelectionHandler selection = selectionHandlerList.getSelectionModel().getSelected();
        final boolean selected = loadedPolicy && selection != null;
        int index = -1;
        if (selected) {
            index = selectionHandlers.indexOf(selection);
        }

        view.setSelectedEnabled(selection != null && selection.isEnabled());

        final Set<SelectionHandlersAction> actions = EnumSet.noneOf(SelectionHandlersAction.class);
        if (loadedPolicy) {
            actions.add(SelectionHandlersAction.ADD);
        }
        if (selected) {
            actions.add(SelectionHandlersAction.EDIT);
            actions.add(SelectionHandlersAction.COPY);
            actions.add(SelectionHandlersAction.TOGGLE);
            actions.add(SelectionHandlersAction.DELETE);
        }
        if (selected && index > 0) {
            actions.add(SelectionHandlersAction.MOVE_UP);
        }
        if (selected && index >= 0 && index < selectionHandlers.size() - 1) {
            actions.add(SelectionHandlersAction.MOVE_DOWN);
        }
        view.setAvailableActions(actions);
    }

    private void setDirty(final boolean dirty) {
        if (this.dirty != dirty) {
            this.dirty = dirty;
        }
    }

    /// @param useForFilter Whether the handlers set the pane's Selection Filter, rather than its
    ///                     Selection Query.
    public void setUseForFilter(final boolean useForFilter) {
        this.useForFilter = useForFilter;
    }
}
