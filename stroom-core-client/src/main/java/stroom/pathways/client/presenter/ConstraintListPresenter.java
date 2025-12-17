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

package stroom.pathways.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.cell.tickbox.client.TickBoxCell;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.dispatch.client.RestFactory;
import stroom.pathways.shared.otel.trace.NanoTime;
import stroom.pathways.shared.pathway.Constraint;
import stroom.pathways.shared.pathway.PathNode;
import stroom.svg.client.SvgPresets;
import stroom.util.client.DataGridUtil;
import stroom.widget.button.client.ButtonView;
import stroom.widget.util.client.MouseUtil;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.user.cellview.client.Column;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ConstraintListPresenter
        extends MyPresenterWidget<PagerView> {

//    private static final ConstraintsResource PATHWAYS_RESOURCE = GWT.create(ConstraintsResource.class);

    //    private final DateTimeFormatter dateTimeFormatter;
    private final PagerView pagerView;
    private final RestFactory restFactory;
    private final MyDataGrid<Constraint> dataGrid;
    private final MultiSelectionModelImpl<Constraint> selectionModel;
    private final ConstraintEditPresenter constraintEditPresenter;
    private final ButtonView newButton;
    private final ButtonView editButton;
    private final ButtonView removeButton;


    private PathNode pathNode;

    //    private String filter;
    private boolean readOnly = true;

    @Inject
    public ConstraintListPresenter(final EventBus eventBus,
                                   final PagerView view,
                                   final RestFactory restFactory,
//                                   final DateTimeFormatter dateTimeFormatter,
                                   final ConstraintEditPresenter constraintEditPresenter) {
        super(eventBus, view);
        this.pagerView = view;
        this.restFactory = restFactory;
//        this.dateTimeFormatter = dateTimeFormatter;
//        view.setDataView(pagerView);
//        view.setUiHandlers(this);

        dataGrid = new MyDataGrid<>(this);
        selectionModel = dataGrid.addDefaultSelectionModel(true);
        pagerView.setDataWidget(dataGrid);

        this.constraintEditPresenter = constraintEditPresenter;

        newButton = pagerView.addButton(SvgPresets.NEW_ITEM);
        editButton = pagerView.addButton(SvgPresets.EDIT);
        removeButton = pagerView.addButton(SvgPresets.DELETE);

        addColumns();
        enableButtons();
    }

    @Override
    protected void onBind() {
        super.onBind();

        registerHandler(newButton.addClickHandler(event -> {
            if (!readOnly) {
                if (MouseUtil.isPrimary(event)) {
                    onAdd();
                }
            }
        }));
        registerHandler(editButton.addClickHandler(event -> {
            if (!readOnly) {
                if (MouseUtil.isPrimary(event)) {
                    onEdit();
                }
            }
        }));
        registerHandler(removeButton.addClickHandler(event -> {
            if (!readOnly) {
                if (MouseUtil.isPrimary(event)) {
                    onRemove();
                }
            }
        }));
        registerHandler(selectionModel.addSelectionHandler(event -> {
            if (!readOnly) {
                enableButtons();
                if (event.getSelectionType().isDoubleSelect()) {
                    onEdit();
                }
            }
        }));
//        registerHandler(dataGrid.addColumnSortHandler(event -> refresh()));
    }

//    @Override
//    public void onFilterChange(final String text) {
//        filter = text;
////        refresh();
//    }

    private void enableButtons() {
        newButton.setEnabled(!readOnly);
        if (!readOnly) {
            final Constraint selectedElement = selectionModel.getSelected();
            final boolean enabled = selectedElement != null;
            editButton.setEnabled(enabled);
            removeButton.setEnabled(enabled);
        } else {
            editButton.setEnabled(false);
            removeButton.setEnabled(false);
        }

        if (readOnly) {
            newButton.setTitle("New constraint disabled as read only");
            editButton.setTitle("Edit constraint disabled as read only");
            removeButton.setTitle("Remove constraint disabled as read only");
        } else {
            newButton.setTitle("New Constraint");
            editButton.setTitle("Edit Constraint");
            removeButton.setTitle("Remove Constraint");
        }
    }

    private void addColumns() {
        addNameColumn();
        addTypeColumn();
        addValueColumn();
        addOptionalColumn();
        dataGrid.addEndColumn(new EndColumn<>());
    }

    private void addNameColumn() {
        addTextColumn("Name", 300, Constraint::getName);
    }

    private void addTextColumn(final String name, final int width, final Function<Constraint, String> function) {
        final Column<Constraint, String> column = DataGridUtil.textColumnBuilder(function)
                .withSorting(name)
                .build();
        dataGrid.addResizableColumn(column,
                name,
                width);
//        dataGrid.sort(column);
    }

    private void addTypeColumn() {
        addTextColumn("Type", 100, constraint -> {
            if (constraint == null || constraint.getValue() == null) {
                return null;
            }
            return constraint.getValue().valueType().getDisplayValue();
        });
    }

    private void addValueColumn() {
        addTextColumn("Type", 400, constraint -> {
            if (constraint == null || constraint.getValue() == null) {
                return null;
            }
            return constraint.getValue().toString();
        });
    }

    private void addOptionalColumn() {
        final boolean updateable = false;
        final TickBoxCell.Appearance appearance = updateable
                ? new TickBoxCell.DefaultAppearance()
                : new TickBoxCell.NoBorderAppearance();

        final Function<Constraint, TickBoxState> valueExtractor = constraint -> {
            if (constraint == null || constraint.getValue() == null) {
                return null;
            }
            return TickBoxState.fromBoolean(constraint.isOptional());
        };

        final Column<Constraint, TickBoxState> selectionColumn = DataGridUtil.columnBuilder(
                        valueExtractor, () -> TickBoxCell.create(
                                appearance, true, true, updateable))
                .centerAligned()
                .build();
        dataGrid.addColumn(selectionColumn,
                "Optional",
                100);
    }

    private void onAdd() {
        final NanoTime now = NanoTime.ofMillis(System.currentTimeMillis());
        constraintEditPresenter.read(new Constraint("New", null, false));
        constraintEditPresenter.show("New Constraint", e -> {
            try {
                if (e.isOk()) {
                    final Constraint constraint = constraintEditPresenter.write();
                    pathNode.getConstraints().remove(constraint.getName());
                    pathNode.getConstraints().put(constraint.getName(), constraint);
                    refresh();
                }
            } catch (final RuntimeException ex) {
                AlertEvent.fireErrorFromException(this, "Error", ex, null);
            } finally {
                e.hide();
            }
        });
    }

    private void onEdit() {
        final Constraint existingConstraint = selectionModel.getSelected();
        if (existingConstraint != null) {
            constraintEditPresenter.read(existingConstraint);
            constraintEditPresenter.show("Edit Constraint", e -> {
                try {
                    if (e.isOk()) {
                        final Constraint constraint = constraintEditPresenter.write();
                        pathNode.getConstraints().remove(constraint.getName());
                        pathNode.getConstraints().put(constraint.getName(), constraint);
                        refresh();

                    }
                } catch (final RuntimeException ex) {
                    AlertEvent.fireErrorFromException(this, "Error", ex, null);
                } finally {
                    e.hide();
                }
            });
        }
    }

    private void onRemove() {
        final List<Constraint> list = selectionModel.getSelectedItems();
        if (list != null && !list.isEmpty()) {
            String message = "Are you sure you want to delete the selected constraint?";
            if (list.size() > 1) {
                message = "Are you sure you want to delete the selected constraints?";
            }

            ConfirmEvent.fire(this, message, result -> {
                if (result) {
                    for (final Constraint constraint : list) {
                        pathNode.getConstraints().remove(constraint.getName());
                    }
                    refresh();
                }
            });
        }
    }

    public void setData(final PathNode pathNode,
                        final boolean readOnly) {
        this.pathNode = pathNode;
        this.readOnly = readOnly;
        refresh();
    }

    private void refresh() {
        final List<Constraint> constraints = getConstraintList(pathNode);
        dataGrid.setRowData(constraints);
        dataGrid.setRowCount(constraints.size());
    }

    private List<Constraint> getConstraintList(final PathNode pathNode) {
        final List<Constraint> list = new ArrayList<>();

        if (pathNode != null) {
            final Map<String, Constraint> constraints = pathNode.getConstraints();
            if (constraints != null) {
                list.addAll(constraints
                        .values()
                        .stream()
                        .sorted(Comparator.comparing(Constraint::getName))
                        .collect(Collectors.toList()));

//
//
//            if (constraints.getDuration() != null) {
//                list.add(new Constraint("duration", constraints.getDuration(), false));
//            }
//            if (constraints.getFlags() != null) {
//                list.add(new Constraint("flags", constraints.getFlags(), false));
//            }
//            if (constraints.getKind() != null) {
//                list.add(new Constraint("kind", constraints.getKind(), false));
//            }
//
//            final Map<String, ConstraintValue> requiredAttributes = NullSafe.map(constraints.getRequiredAttributes());
//            final Map<String, ConstraintValue> optionalAttributes = NullSafe.map(constraints.getOptionalAttributes());
//            final Set<String> keys = new HashSet<>(requiredAttributes.keySet());
//            keys.addAll(optionalAttributes.keySet());
//            final List<String> sortedKeys = keys.stream().sorted().collect(Collectors.toList());
//            for (final String key : sortedKeys) {
//                final ConstraintValue required = requiredAttributes.get(key);
//                if (required != null) {
//                    list.add(new Constraint("attribute." + key, required, false));
//                }
//                final ConstraintValue optional = optionalAttributes.get(key);
//                if (optional != null) {
//                    list.add(new Constraint("attribute." + key, optional, true));
//                }
//            }
            }
        }
        return list;
    }

//
//    private void refresh() {
//        if (dataProvider == null) {
//            dataProvider = new RestDataProvider<Constraint, ResultPage<Constraint>>(getEventBus()) {
//                @Override
//                protected void exec(final Range range,
//                                    final Consumer<ResultPage<Constraint>> dataConsumer,
//                                    final RestErrorHandler errorHandler) {
//                    final FindConstraintCriteria criteria = new FindConstraintCriteria(
//                            CriteriaUtil.createPageRequest(range),
//                            CriteriaUtil.createSortList(dataGrid.getColumnSortList()),
//                            docRef,
//                            filter,
//                            null);
//
//                    restFactory
//                            .create(PATHWAYS_RESOURCE)
//                            .method(res -> res.findConstraints(criteria))
//                            .onSuccess(dataConsumer)
//                            .onFailure(errorHandler)
//                            .taskMonitorFactory(pagerView)
//                            .exec();
//                }
//            };
//            dataProvider.addDataDisplay(dataGrid);
//
//        } else {
//            dataProvider.refresh();
//        }
//    }
}
