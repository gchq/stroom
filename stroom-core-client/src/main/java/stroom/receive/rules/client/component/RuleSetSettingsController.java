/*
 * Copyright 2026 Crown Copyright
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

package stroom.receive.rules.client.component;

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.datasource.QueryField;
import stroom.query.client.SimpleFieldSelectionListModel;
import stroom.query.client.component.ExpressionTreeController;
import stroom.receive.rules.client.component.RuleSetSettings.RuleAction;
import stroom.receive.rules.shared.ReceiveAction;
import stroom.receive.rules.shared.ReceiveDataRule;
import stroom.receive.rules.shared.ReceiveDataRules;
import stroom.util.shared.NullSafe;
import stroom.widget.component.client.Contexts;
import stroom.widget.component.client.Controller;
import stroom.widget.util.client.MultiSelectEvent;
import stroom.widget.util.client.MultiSelectionModel;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/// Fills the receipt rules' Rules tab ([RuleSetSettings]) and does its actions: the rules, in the
/// order they are tried, can be added, edited, copied, enabled or disabled, deleted and moved unless
/// the rules are read only ([Contexts#READ_ONLY]), and the selected rule's expression is shown.
public class RuleSetSettingsController extends Controller {

    private final RuleSetSettings view;
    private final RuleSetListController ruleSetList;
    private final ExpressionTreeController expressionTree;
    private final Provider<RuleEditorController> ruleEditorProvider;
    private final SimpleFieldSelectionListModel fieldSelectionBoxModel = new SimpleFieldSelectionListModel();
    private final List<ReceiveDataRule> rules = new ArrayList<>();

    private boolean readOnly = true;
    private Supplier<List<QueryField>> fields = List::of;
    private Runnable onChange = () -> {
    };

    /// Makes the controller.
    ///
    /// @param eventBus           The app's event bus.
    /// @param view               The screen.
    /// @param ruleSetList        The list of rules.
    /// @param expressionTree     Shows the rule chosen's expression.
    /// @param ruleEditorProvider Makes the dialog that edits a rule.
    @Inject
    public RuleSetSettingsController(final EventBus eventBus,
                                     final RuleSetSettings view,
                                     final RuleSetListController ruleSetList,
                                     final ExpressionTreeController expressionTree,
                                     final Provider<RuleEditorController> ruleEditorProvider) {
        super(eventBus);
        this.view = view;
        this.ruleSetList = own(ruleSetList).attach(view.getRuleList());
        this.expressionTree = own(expressionTree).attach(view.getExpressionTree());
        this.ruleEditorProvider = ruleEditorProvider;

        initWidget(view);
        view.setOnAction(this::onAction);

        // Stop users from selecting expression items.
        expressionTree.setSelectionModel(null);

        own(getSelectionModel().addSelectionHandler(this::onSelection));

        watch(Contexts.READ_ONLY, readOnly -> {
            this.readOnly = readOnly;
            view.setReadOnly(readOnly);
        });
    }

    /// @param fields The fields as they are now, perhaps changed on the Fields tab since the rules
    ///               were read: a rule can only be added once there is one.
    public void setFields(final Supplier<List<QueryField>> fields) {
        this.fields = fields;
    }

    /// @param onChange What to run when the user changes the rules.
    public void setOnChange(final Runnable onChange) {
        this.onChange = onChange;
    }

    /// Shows the rules, with none selected.
    ///
    /// @param document The rules' document.
    public void read(final ReceiveDataRules document) {
        fieldSelectionBoxModel.clear();
        NullSafe.consume(document.getFields(), fieldSelectionBoxModel::addItems);
        rules.clear();
        NullSafe.consume(document.getRules(), rules::addAll);
        getSelectionModel().clear();
        update();
    }

    /// @param document The rules' document.
    /// @return A copy of it with the rules shown.
    public ReceiveDataRules write(final ReceiveDataRules document) {
        return document.copy().rules(new ArrayList<>(rules)).build();
    }

    private MultiSelectionModel<ReceiveDataRule> getSelectionModel() {
        return ruleSetList.getSelectionModel();
    }

    private void onAction(final RuleAction action) {
        switch (action) {
            case ADD -> addRule();
            case EDIT -> editSelected();
            case COPY -> copySelected();
            case TOGGLE -> toggleSelected();
            case DELETE -> deleteSelected();
            case MOVE_UP -> moveSelected(-1);
            case MOVE_DOWN -> moveSelected(1);
        }
    }

    private void addRule() {
        if (!readOnly) {
            if (NullSafe.isEmptyCollection(fields.get())) {
                AlertEvent.fireError(this,
                        "You need to create one or more fields before you can add a rule.",
                        null,
                        null);
            } else {
                final ReceiveDataRule newRule = new ReceiveDataRule(
                        0,
                        System.currentTimeMillis(),
                        "",
                        true,
                        ExpressionOperator.builder().build(),
                        ReceiveAction.RECEIVE);
                ruleEditorProvider.get().edit(this, newRule, fieldSelectionBoxModel, rule -> {
                    rules.add(0, rule);
                    update();
                    getSelectionModel().setSelected(rule);
                    change();
                });
            }
        }
    }

    private void editSelected() {
        final ReceiveDataRule selected = getSelectionModel().getSelected();
        if (!readOnly && selected != null) {
            edit(selected);
        }
    }

    private void edit(final ReceiveDataRule existingRule) {
        ruleEditorProvider.get().edit(this, existingRule, fieldSelectionBoxModel, rule -> {
            final int index = rules.indexOf(existingRule);
            if (index < 0) {
                // Deleted behind the dialog, which isn't modal
                AlertEvent.fireWarn(this, "The rule was deleted while it was being edited, "
                                          + "so the changes can't be kept.", null);
                return;
            }
            rules.set(index, rule);
            update();
            getSelectionModel().setSelected(rule);
            // Only mark the rules as changed if the rule was actually changed.
            if (!existingRule.equals(rule)) {
                change();
            }
        });
    }

    private void copySelected() {
        final ReceiveDataRule selected = getSelectionModel().getSelected();
        if (!readOnly && selected != null) {
            final ReceiveDataRule newRule = new ReceiveDataRule(
                    selected.getRuleNumber() + 1,
                    System.currentTimeMillis(),
                    selected.getName(),
                    selected.isEnabled(),
                    selected.getExpression(),
                    selected.getAction());
            rules.add(rules.indexOf(selected) + 1, newRule);
            update();
            getSelectionModel().setSelected(newRule);
            change();
        }
    }

    private void toggleSelected() {
        final List<ReceiveDataRule> selectedItems = getSelectionModel().getSelectedItems();
        if (!readOnly && NullSafe.hasItems(selectedItems)) {
            final List<ReceiveDataRule> newSelection = new ArrayList<>(selectedItems.size());
            for (final ReceiveDataRule rule : selectedItems) {
                final ReceiveDataRule newRule = rule.copy()
                        .withEnabled(!rule.isEnabled())
                        .build();
                newSelection.add(newRule);
                rules.set(rules.indexOf(rule), newRule);
            }
            getSelectionModel().setSelectedItems(newSelection);
            update();
            change();
        }
    }

    private void deleteSelected() {
        if (!readOnly) {
            final List<ReceiveDataRule> selected = getSelectionModel().getSelectedItems();
            final String msg = selected.size() == 1
                    ? "Are you sure you want to delete this rule?"
                    : "Are you sure you want to delete the selected rules?";
            ConfirmEvent.fire(this, msg, ok -> {
                if (ok) {
                    rules.removeAll(selected);
                    getSelectionModel().clear();
                    update();
                    change();
                }
            });
        }
    }

    // Moves the selected rule up (-1) or down (1) one place, if it can go
    private void moveSelected(final int by) {
        final ReceiveDataRule rule = getSelectionModel().getSelected();
        if (!readOnly && rule != null) {
            final int index = rules.indexOf(rule) + by;
            if (index >= 0 && index < rules.size()) {
                rules.remove(rule);
                rules.add(index, rule);
                update();
                change();
                // Re-select the rule.
                getSelectionModel().setSelected(rules.get(index));
            }
        }
    }

    private void onSelection(final MultiSelectEvent selectEvent) {
        final ReceiveDataRule rule = getSelectionModel().getSelected();
        if (rule != null) {
            expressionTree.read(rule.getExpression());
            if (selectEvent.getSelectionType().isDoubleSelect() && !readOnly) {
                edit(rule);
            }
        } else {
            expressionTree.read(null);
        }
        updateButtons();
    }

    private void update() {
        // Set rule numbers on all of the rules for display purposes.
        for (int i = 0; i < rules.size(); i++) {
            final ReceiveDataRule rule = rules.get(i);
            rules.set(i, new ReceiveDataRule(
                    i + 1,
                    rule.getCreationTime(),
                    rule.getName(),
                    rule.isEnabled(),
                    rule.getExpression(),
                    rule.getAction()));
        }
        ruleSetList.setData(rules);
        updateButtons();
    }

    // The actions that apply to the rules selected, and the name of enable or disable for them
    private void updateButtons() {
        final MultiSelectionModel<ReceiveDataRule> selectionModel = getSelectionModel();
        final Set<RuleAction> actions = EnumSet.of(RuleAction.ADD);
        final Boolean areSelectedEnabled;

        if (selectionModel.getSelectedCount() == 0) {
            areSelectedEnabled = null;
        } else if (selectionModel.getSelectedCount() == 1) {
            final ReceiveDataRule selection = selectionModel.getSelected();
            final int index = rules.indexOf(selection);
            areSelectedEnabled = selection.isEnabled();
            actions.addAll(EnumSet.of(RuleAction.EDIT, RuleAction.COPY, RuleAction.TOGGLE, RuleAction.DELETE));
            if (index > 0) {
                actions.add(RuleAction.MOVE_UP);
            }
            if (index >= 0 && index < rules.size() - 1) {
                actions.add(RuleAction.MOVE_DOWN);
            }
        } else {
            // Multi-select
            final Set<Boolean> enabledStates = selectionModel.getSelectedItems()
                    .stream()
                    .map(ReceiveDataRule::isEnabled)
                    .collect(Collectors.toSet());
            areSelectedEnabled = enabledStates.size() == 1
                    ? enabledStates.iterator().next()
                    : null;
            actions.add(RuleAction.DELETE);
            if (enabledStates.size() == 1) {
                actions.add(RuleAction.TOGGLE);
            }
        }

        view.setAvailableActions(actions);
        view.setSelectedEnabled(areSelectedEnabled, selectionModel.getSelectedCount() > 1);
    }

    private void change() {
        onChange.run();
        updateButtons();
    }
}
