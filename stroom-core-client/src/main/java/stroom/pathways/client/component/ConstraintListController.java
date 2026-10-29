/*
 * Copyright 2025 Crown Copyright
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

package stroom.pathways.client.component;

import stroom.alert.client.event.ConfirmEvent;
import stroom.pathways.shared.pathway.Constraint;
import stroom.pathways.shared.pathway.PathNode;
import stroom.widget.component.client.Controller;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/// A pathway node's constraints ([ConstraintList]): New and Edit open a constraint's editor, Delete
/// deletes those selected once confirmed, unless read only.
public class ConstraintListController extends Controller {

    private ConstraintList view;
    private final Provider<ConstraintEditorController> constraintEditorProvider;

    private PathNode pathNode;
    private boolean readOnly = true;

    /// Made by GIN; [#attach(ConstraintList)] gives it its list.
    ///
    /// @param eventBus                 The application's event bus.
    /// @param constraintEditorProvider Makes a constraint's editor.
    @Inject
    public ConstraintListController(final EventBus eventBus,
                                    final Provider<ConstraintEditorController> constraintEditorProvider) {
        super(eventBus);
        this.constraintEditorProvider = constraintEditorProvider;
    }

    /// Attaches this to a list, e.g. one a template declares. The list is then this controller's
    /// widget.
    ///
    /// @param view The list.
    /// @return This, for its owner to keep.
    public ConstraintListController attach(final ConstraintList view) {
        this.view = Objects.requireNonNull(view, "view");
        initWidget(view);

        view.setOnNew(() -> {
            if (!readOnly) {
                onAdd();
            }
        });
        view.setOnEdit(() -> {
            if (!readOnly) {
                onEdit();
            }
        });
        view.setOnRemove(() -> {
            if (!readOnly) {
                onRemove();
            }
        });
        view.updateActions(readOnly);

        view.setOnSelectionChange(event -> {
            if (!readOnly) {
                view.updateActions(readOnly);
                if (event.getSelectionType().isDoubleSelect()) {
                    onEdit();
                }
            }
        });
        return this;
    }

    private void onAdd() {
        constraintEditorProvider.get().edit(this, "New Constraint", new Constraint("New", null, false),
                constraint -> {
                    pathNode.getConstraints().put(constraint.getName(), constraint);
                    refresh();
                });
    }

    private void onEdit() {
        final Constraint existingConstraint = view.getSelectionModel().getSelected();
        if (existingConstraint != null) {
            constraintEditorProvider.get().edit(this, "Edit Constraint", existingConstraint, constraint -> {
                // A renamed constraint replaces the one it was
                pathNode.getConstraints().remove(existingConstraint.getName());
                pathNode.getConstraints().put(constraint.getName(), constraint);
                refresh();
            });
        }
    }

    private void onRemove() {
        final List<Constraint> list = view.getSelectionModel().getSelectedItems();
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

    /// Shows a node's constraints. The buttons are not brought up to date until a constraint is
    /// selected (a known bug, kept: see ISSUES.md, "The Pathways editor can't create a pathway or add
    /// a constraint").
    ///
    /// @param pathNode The node, or `null` for none.
    /// @param readOnly Whether its constraints are read only.
    public void setData(final PathNode pathNode,
                        final boolean readOnly) {
        this.pathNode = pathNode;
        this.readOnly = readOnly;
        refresh();
    }

    private void refresh() {
        view.setData(getConstraintList(pathNode));
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
            }
        }
        return list;
    }
}
