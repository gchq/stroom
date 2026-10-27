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
 */

package stroom.receive.rules.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.cell.info.client.CellIcon;
import stroom.data.retention.shared.DataRetentionRule;
import stroom.data.retention.shared.DataRetentionRules;
import stroom.data.retention.shared.DataRetentionRulesResource;
import stroom.dispatch.client.RestFactory;
import stroom.query.api.ExpressionOperator;
import stroom.query.client.component.ExpressionTreeController;
import stroom.receive.rules.client.component.DataRetentionPolicyListController;
import stroom.receive.rules.client.component.DataRetentionRuleEditorController;
import stroom.receive.rules.client.presenter.DataRetentionPolicyPresenter.DataRetentionPolicyView;
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.ButtonView;
import stroom.widget.menu.client.presenter.Item;
import stroom.widget.menu.client.presenter.MenuBuilder;
import stroom.widget.presenter.client.MyPresenterWidget;
import stroom.widget.presenter.client.View;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DataRetentionPolicyPresenter extends MyPresenterWidget<DataRetentionPolicyView> {

    private static final DataRetentionRulesResource DATA_RETENTION_RULES_RESOURCE = GWT.create(
            DataRetentionRulesResource.class);

    // This rule exist in the UI only and is never passed to the back end
    // The back end retains all data by default unless a rule specifies otherwise.
    // This rule just makes it clear to the users what is happening.
    public static final DataRetentionRule DEFAULT_UI_ONLY_RETAIN_ALL_RULE = DataRetentionRule.foreverRule(
            Integer.MAX_VALUE,
            0,
            "Default Retain All Forever Rule",
            true,
            ExpressionOperator.builder().build());
    private static final CellIcon DELETE_RULE_ICON = CellIcon.of(SvgImage.DELETE, "Delete rule", false);
    protected static final CellIcon ADD_ABOVE_ICON = CellIcon.of(SvgImage.ADD_ABOVE,
            "Add new rule above the selected one");
    protected static final CellIcon COPY_RULE_ICON = CellIcon.of(SvgImage.COPY, "Copy rule", false);
    protected static final CellIcon EDIT_RULE_ICON = CellIcon.of(SvgImage.EDIT, "Edit rule", false);
    protected static final CellIcon MOVE_RULE_UP_ICON = CellIcon.of(SvgImage.UP, "Move rule up", false);
    protected static final CellIcon MOVE_RULE_DOWN_ICON = CellIcon.of(SvgImage.DOWN, "Move rule down", false);

    private final DataRetentionPolicyListController dataRetentionPolicyList;
    private final ExpressionTreeController expressionTree;
    private final Provider<DataRetentionRuleEditorController> ruleEditorProvider;
    private final RestFactory restFactory;

    private DataRetentionRules policy;
    private List<DataRetentionRule> visibleRules;

    private final ButtonView saveButton;
    private final ButtonView addButton;
    private final ButtonView editButton;
    private final ButtonView copyButton;
    private final ButtonView deleteButton;
    private final ButtonView moveUpButton;
    private final ButtonView moveDownButton;

    private boolean dirty;
    private DataRetentionPresenter dataRetentionPresenter;

    @Inject
    public DataRetentionPolicyPresenter(final EventBus eventBus,
                                        final DataRetentionPolicyView view,
                                        final DataRetentionPolicyListController dataRetentionPolicyList,
                                        final ExpressionTreeController expressionTree,
                                        final Provider<DataRetentionRuleEditorController> ruleEditorProvider,
                                        final RestFactory restFactory) {
        super(eventBus, view);
        own(dataRetentionPolicyList);
        own(expressionTree);
        this.dataRetentionPolicyList = dataRetentionPolicyList;
        this.expressionTree = expressionTree;
        this.ruleEditorProvider = ruleEditorProvider;
        this.restFactory = restFactory;

        getView().setTableView(dataRetentionPolicyList);
        getView().setExpressionView(expressionTree);

        // Stop users from selecting expression items.
        expressionTree.setSelectionModel(null);

        dataRetentionPolicyList.getToolbar().setName("Retention rule actions");
        saveButton = dataRetentionPolicyList.getToolbar().button(SvgImage.SAVE, "Save Rules", this::onSave);
        saveButton.setEnabled(false);
        addButton = dataRetentionPolicyList.getToolbar().button(SvgImage.ADD_ABOVE, "Add Rule Above",
                this::onAdd);
        editButton = dataRetentionPolicyList.getToolbar().button(SvgImage.EDIT, "Edit Selected Rule", this::onEdit);
        editButton.setEnabled(false);
        copyButton = dataRetentionPolicyList.getToolbar().button(SvgImage.COPY, "Duplicate Selected Rule",
                this::onCopy);
        copyButton.setEnabled(false);
        deleteButton = dataRetentionPolicyList.getToolbar().button(SvgImage.DELETE, "Delete Selected Rule",
                this::onDelete);
        deleteButton.setEnabled(false);
        moveUpButton = dataRetentionPolicyList.getToolbar().button(SvgImage.UP, "Move Rule Up", this::onMoveUp);
        moveUpButton.setEnabled(false);
        moveDownButton = dataRetentionPolicyList.getToolbar().button(SvgImage.DOWN, "Move Rule Down", this::onMoveDown);
        moveDownButton.setEnabled(false);

        updateButtons();

        initialiseRules(restFactory);

        addSaveButtonHandler();
        addAddButtonHandler();
        addEditButtonHandler();
        addCopyButtonHandler();
        addDeleteButtonHandler();
        addMoveUpButtonHandler();
        addMoveDownButtonHandler();
        addListSelectionHandler();
        dataRetentionPolicyList.setDefaultRule(DEFAULT_UI_ONLY_RETAIN_ALL_RULE);
        addEnabledClickHandler();

        dataRetentionPolicyList.setActionMenuItemProvider(this::buildActionMenuItems);
    }

    private void initialiseRules(final RestFactory restFactory) {
        restFactory
                .create(DATA_RETENTION_RULES_RESOURCE)
                .method(DataRetentionRulesResource::fetch)
                .onSuccess(result -> {
                    policy = result;
                    if (policy.getRules() == null) {
                        policy = policy.copy().rules(new ArrayList<>()).build();
                    }
                    setVisibleRules(policy.getRules());
                    update();
                })
                .taskMonitorFactory(this)
                .exec();
    }

    private void setVisibleRules(final List<DataRetentionRule> rules) {
        final List<DataRetentionRule> allRules = new ArrayList<>();
        if (rules != null) {
            allRules.addAll(rules);
        }
        // Add in our special UI only rule
        allRules.add(DEFAULT_UI_ONLY_RETAIN_ALL_RULE);
        this.visibleRules = allRules;
    }

    /**
     * @return The rules created by the users, ignoring the default retain all rule
     */
    private List<DataRetentionRule> getUserRules() {
        if (visibleRules == null || visibleRules.size() <= 1) {
            return Collections.emptyList();
        } else {
            return visibleRules.subList(0, visibleRules.size() - 1);
        }
    }

    DataRetentionRules getPolicy() {
        return policy;
    }

    private boolean isDefaultRule(final DataRetentionRule rule) {
        if (rule == null || visibleRules == null || visibleRules.size() < 1) {
            return false;
        } else {
            // Default rule is always the last one
            return rule.getRuleNumber() == visibleRules.get(visibleRules.size() - 1)
                    .getRuleNumber();
        }
    }

    private void onSave() {
        // Get the user's rules without our default one
        policy = policy.copy().rules(getUserRules()).build();

        restFactory
                .create(DATA_RETENTION_RULES_RESOURCE)
                .method(res -> res.update(policy))
                .onSuccess(result -> {
                    policy = result;
                    setVisibleRules(policy.getRules());
                    dataRetentionPolicyList.getSelectionModel().clear();

                    update();
                    setDirty(false);
                })
                .taskMonitorFactory(this)
                .exec();
    }

    private void onAdd() {
        if (visibleRules != null) {
            addNewRule();
        }
    }

    private void onEdit() {
        if (visibleRules != null) {
            final DataRetentionRule selected = dataRetentionPolicyList.getSelectionModel().getSelected();
            editRule(selected);
        }
    }

    private void onCopy() {
        if (visibleRules != null) {
            final DataRetentionRule selected = dataRetentionPolicyList.getSelectionModel().getSelected();
            copyRule(selected);
        }
    }

    private void onDelete() {
        if (visibleRules != null) {

            final DataRetentionRule rule = dataRetentionPolicyList.getSelectionModel().getSelected();
            deleteRule(rule);
        }
    }

    private void onMoveUp() {
        if (visibleRules != null) {
            final DataRetentionRule rule = dataRetentionPolicyList.getSelectionModel().getSelected();
            moveRuleUp(rule);
        }
    }

    private void onMoveDown() {
        if (visibleRules != null) {
            final DataRetentionRule rule = dataRetentionPolicyList.getSelectionModel().getSelected();
            moveRuleDown(rule);
        }
    }

    private List<Item> buildActionMenuItems(final DataRetentionRule rule) {

        final boolean isDefaultRule = isDefaultRule(rule);
        return MenuBuilder.builder()
//                .withSimpleMenuItem(itemBuilder ->
//                        itemBuilder.withText(rule.getRuleNumber() + ". " + rule.getName()))
//                .withSeparator()
                .withIconMenuItem(itemBuilder ->
                        itemBuilder
                                .icon(SvgImage.ADD_ABOVE)
                                .text("Add Rule Above")
                                .command(() ->
                                        addNewRule(rule.getRuleNumber() - 1)))
                .withIconMenuItemIf(!isDefaultRule, itemBuilder ->
                        itemBuilder
                                .icon(SvgImage.ADD_BELOW)
                                .text("Add Rule Below")
                                .command(() ->
                                        addNewRule(rule.getRuleNumber())))
                .withIconMenuItemIf(!isDefaultRule, itemBuilder ->
                        itemBuilder
                                .icon(SvgImage.EDIT)
                                .text("Edit Rule")
                                .command(() ->
                                        editRule(rule)))
                .withIconMenuItem(itemBuilder ->
                        itemBuilder
                                .icon(SvgImage.COPY)
                                .text("Duplicate Rule")
                                .command(() ->
                                        copyRule(rule)))
                .withIconMenuItemIf(!isDefaultRule, itemBuilder ->
                        itemBuilder
                                .icon(SvgImage.DELETE)
                                .text("Delete Rule")
                                .command(() ->
                                        deleteRule(rule)))
                .withIconMenuItemIf(!isDefaultRule && rule.getRuleNumber() > 1, itemBuilder ->
                        itemBuilder
                                .icon(SvgImage.UP)
                                .text("Move Rule Up")
                                .command(() ->
                                        moveRuleUp(rule)))
                .withIconMenuItemIf(rule.getRuleNumber() < visibleRules.size() - 1, itemBuilder ->
                        itemBuilder
                                .icon(SvgImage.DOWN)
                                .text("Move Rule Down")
                                .command(() ->
                                        moveRuleDown(rule)))
                .build();
    }

    private void addEnabledClickHandler() {
        dataRetentionPolicyList.setOnEnabledChange((rule, isEnabled) -> {
            if (visibleRules != null) {
                if (rule != null && !isDefaultRule(rule)) {
                    final DataRetentionRule newRule = new DataRetentionRule(
                            rule.getRuleNumber(),
                            rule.getCreationTime(),
                            rule.getName(),
                            isEnabled,
                            rule.getExpression(),
                            rule.getAge(),
                            rule.getTimeUnit(),
                            rule.isForever());

                    int index = visibleRules.indexOf(rule);
                    visibleRules.remove(index);
                    visibleRules.add(index, newRule);
                    index = visibleRules.indexOf(newRule);

                    update();
                    setDirty(true);

                    dataRetentionPolicyList.getSelectionModel().setSelected(visibleRules.get(index));
                }
            }
        });
    }

    private void addListSelectionHandler() {
        own(dataRetentionPolicyList.getSelectionModel().addSelectionHandler(event -> {
            final DataRetentionRule rule = dataRetentionPolicyList.getSelectionModel().getSelected();
            if (rule != null) {
                expressionTree.read(rule.getExpression());
                if (event.getSelectionType().isDoubleSelect() && !isDefaultRule(rule)) {
                    edit(rule);
                }
            } else {
                expressionTree.read(null);
            }
            updateButtons();
        }));
    }

    private void addMoveDownButtonHandler() {
    }

    private void moveRuleDown(final DataRetentionRule rule) {
        if (rule != null && !isDefaultRule(rule)) {
            int index = visibleRules.indexOf(rule);
            if (index < visibleRules.size() - 2) {
                index++;

                visibleRules.remove(rule);
                visibleRules.add(index, rule);

                update();
                setDirty(true);

                // Re-select the rule.
                dataRetentionPolicyList.getSelectionModel().setSelected(visibleRules.get(index));
            }
        }
    }

    private void addMoveUpButtonHandler() {
    }

    private void moveRuleUp(final DataRetentionRule rule) {
        if (rule != null && !isDefaultRule(rule)) {
            int index = visibleRules.indexOf(rule);
            if (index > 0) {
                index--;

                visibleRules.remove(rule);
                visibleRules.add(index, rule);

                update();
                setDirty(true);

                // Re-select the rule.
                dataRetentionPolicyList.getSelectionModel().setSelected(visibleRules.get(index));
            }
        }
    }

    private void addDeleteButtonHandler() {
    }

    private void deleteRule(final DataRetentionRule rule) {
        if (rule != null) {
            final String nameStr = rule.getName() != null && !rule.getName().isEmpty()
                    ? " \"" + rule.getName() + "\""
                    : "";
            ConfirmEvent.fire(
                    this,
                    "Are you sure you want to delete rule "
                    + rule.getRuleNumber()
                    + nameStr + "?",
                    ok -> {
                        if (ok) {
                            if (!isDefaultRule(rule)) {
                                visibleRules.remove(rule);

                                update();
                                setDirty(true);

                                // Select the next rule.
                                int index = visibleRules.indexOf(rule);
                                if (index > 0) {
                                    index--;
                                }
                                if (index < visibleRules.size()) {
                                    dataRetentionPolicyList.getSelectionModel().setSelected(visibleRules.get(index));
                                } else {
                                    dataRetentionPolicyList.getSelectionModel().clear();
                                }
                            }
                        }
                    });
        }
    }

    private void addCopyButtonHandler() {
    }

    private void copyRule(final DataRetentionRule rule) {
        if (rule != null) {
            final DataRetentionRule newRule = new DataRetentionRule(
                    rule.getRuleNumber(),
                    System.currentTimeMillis(),
                    rule.getName(),
                    rule.isEnabled(),
                    rule.getExpression(),
                    rule.getAge(),
                    rule.getTimeUnit(),
                    rule.isForever());

            int index = visibleRules.indexOf(rule);
            if (index < visibleRules.size() - 1) {
                visibleRules.add(index + 1, newRule);
            } else {
                visibleRules.add(newRule);
            }
            index = visibleRules.indexOf(newRule);

            update();
            setDirty(true);

            dataRetentionPolicyList.getSelectionModel().setSelected(visibleRules.get(index));
        }
    }

    private void addEditButtonHandler() {
    }

    private void editRule(final DataRetentionRule rule) {
        if (rule != null && !isDefaultRule(rule)) {
            edit(rule);
        }
    }

    private void addAddButtonHandler() {
    }

    private void addSaveButtonHandler() {
    }

    private void addNewRule() {
        final DataRetentionRule selected = dataRetentionPolicyList.getSelectionModel().getSelected();
        if (selected != null) {
            // adding above the selected
            addNewRule(selected.getRuleNumber() - 1);
        } else {
            // Nowt selected so add at the top
            addNewRule(0);
        }
    }

    private void addNewRule(final int ruleNumber) {
        final DataRetentionRule newRule = DataRetentionRule.foreverRule(0,
                System.currentTimeMillis(),
                "",
                true,
                ExpressionOperator.builder().build());

        // Made for one use, and disposed once it is closed
        ruleEditorProvider.get().edit(this, "Add New Rule", newRule, rule -> {
            visibleRules.add(ruleNumber, rule);

            update();
            setDirty(true);
            dataRetentionPolicyList.getSelectionModel().setSelected(visibleRules.get(0));
        });
    }

    private void edit(final DataRetentionRule existingRule) {
        // Made for one use, and disposed once it is closed
        ruleEditorProvider.get().edit(this, "Edit Rule", existingRule, rule -> {
            final int index = visibleRules.indexOf(existingRule);
            if (index < 0) {
                // Deleted behind the dialog, which isn't modal
                AlertEvent.fireWarn(this, "The rule was deleted while it was being edited, "
                                          + "so the changes can't be kept.", null);
            } else {
                visibleRules.remove(index);
                visibleRules.add(index, rule);

                update();
                // Only mark the policies as dirty if the rule was actually changed.
                if (!existingRule.equals(rule)) {
                    setDirty(true);
                }

                dataRetentionPolicyList.getSelectionModel().setSelected(visibleRules.get(index));
            }
        });
    }

    private void update() {
        if (visibleRules != null) {
            // Set rule numbers on all of the rules for display purposes.
            for (int i = 0; i < visibleRules.size(); i++) {
                final DataRetentionRule rule = visibleRules.get(i);
                final DataRetentionRule newRule = new DataRetentionRule(
                        i + 1,
                        rule.getCreationTime(),
                        rule.getName(),
                        rule.isEnabled(),
                        rule.getExpression(),
                        rule.getAge(),
                        rule.getTimeUnit(),
                        rule.isForever());
                visibleRules.set(i, newRule);
            }
            dataRetentionPolicyList.setData(visibleRules);
            // Update the policy so the impact tab can see the unsaved changes
            policy = policy.copy().rules(getUserRules()).build();
        }
        updateButtons();
    }

    private void updateButtons() {
        final boolean loadedPolicy = visibleRules != null;
        final DataRetentionRule selection = dataRetentionPolicyList.getSelectionModel().getSelected();
        final boolean selected = loadedPolicy && selection != null;
        final boolean isDefaultRule = isDefaultRule(selection);
        int index = -1;
        if (selected) {
            index = visibleRules.indexOf(selection);
        }

        saveButton.setEnabled(loadedPolicy
                              && dirty);
        addButton.setEnabled(loadedPolicy);
        editButton.setEnabled(selected
                              && !isDefaultRule);
        copyButton.setEnabled(selected);
        deleteButton.setEnabled(selected
                                && !isDefaultRule);
        moveUpButton.setEnabled(selected
                                && !isDefaultRule
                                && index > 0);
        moveDownButton.setEnabled(selected
                                  && !isDefaultRule
                                  && index >= 0
                                  && index < visibleRules.size() - 2);
    }

    private void setDirty(final boolean dirty) {
        if (this.dirty != dirty) {
            this.dirty = dirty;
            dataRetentionPresenter.setDirty(dirty);
            saveButton.setEnabled(dirty);
        }
    }

    void setParentPresenter(final DataRetentionPresenter dataRetentionPresenter) {
        this.dataRetentionPresenter = dataRetentionPresenter;
    }


    // --------------------------------------------------------------------------------


    public interface DataRetentionPolicyView extends View {

        void setTableView(IsWidget view);

        void setExpressionView(IsWidget view);
    }
}
