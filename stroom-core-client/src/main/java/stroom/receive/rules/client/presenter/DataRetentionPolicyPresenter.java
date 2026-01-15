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

package stroom.receive.rules.client.presenter;

import stroom.alert.client.event.ConfirmEvent;
import stroom.data.retention.shared.DataRetentionRule;
import stroom.data.retention.shared.DataRetentionRules;
import stroom.data.retention.shared.DataRetentionRulesResource;
import stroom.dispatch.client.RestFactory;
import stroom.query.api.ExpressionOperator;
import stroom.query.client.ExpressionTreePresenter;
import stroom.receive.rules.client.presenter.DataRetentionPolicyPresenter.DataRetentionPolicyView;
import stroom.svg.client.Preset;
import stroom.svg.client.SvgPresets;
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.ButtonView;
import stroom.widget.menu.client.presenter.Item;
import stroom.widget.menu.client.presenter.MenuBuilder;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Style.BorderStyle;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

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
    private static final Preset DELETE_RULE_SVG_PRESET = SvgPresets.DELETE.title("Delete rule");
    protected static final Preset ADD_ABOVE_SVG_PRESET = SvgPresets.ADD_ABOVE.title(
            "Add new rule above the selected one");
    protected static final Preset COPY_RULE_SVG_PRESET = SvgPresets.COPY.title("Copy rule");
    protected static final Preset EDIT_RULE_SVG_PRESET = SvgPresets.EDIT.title("Edit rule");
    protected static final Preset MOVE_RULE_UP_SVG_PRESET = SvgPresets.UP.title("Move rule up");
    protected static final Preset MOVE_RULE_DOWN_SVG_PRESET = SvgPresets.DOWN.title("Move rule down");

    private final DataRetentionPolicyListPresenter listPresenter;
    private final ExpressionTreePresenter expressionPresenter;
    private final Provider<DataRetentionRulePresenter> editRulePresenterProvider;
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
                                        final DataRetentionPolicyListPresenter listPresenter,
                                        final ExpressionTreePresenter expressionPresenter,
                                        final Provider<DataRetentionRulePresenter> editRulePresenterProvider,
                                        final RestFactory restFactory) {
        super(eventBus, view);
        this.listPresenter = listPresenter;
        this.expressionPresenter = expressionPresenter;
        this.editRulePresenterProvider = editRulePresenterProvider;
        this.restFactory = restFactory;

        getView().setTableView(listPresenter.getView());
        getView().setExpressionView(expressionPresenter.getView());

        // Stop users from selecting expression items.
        expressionPresenter.setSelectionModel(null);

        saveButton = listPresenter.add(SvgPresets.SAVE.title("Save rules"));
        addButton = listPresenter.add(ADD_ABOVE_SVG_PRESET);
        editButton = listPresenter.add(EDIT_RULE_SVG_PRESET);
        copyButton = listPresenter.add(COPY_RULE_SVG_PRESET);
        deleteButton = listPresenter.add(DELETE_RULE_SVG_PRESET);
        moveUpButton = listPresenter.add(MOVE_RULE_UP_SVG_PRESET);
        moveDownButton = listPresenter.add(MOVE_RULE_DOWN_SVG_PRESET);

        listPresenter.getView()
                .asWidget()
                .getElement()
                .getStyle()
                .setBorderStyle(BorderStyle.NONE);

        updateButtons();

        initialiseRules(restFactory);
    }

    private void initialiseRules(final RestFactory restFactory) {
        restFactory
                .create(DATA_RETENTION_RULES_RESOURCE)
                .method(DataRetentionRulesResource::fetch)
                .onSuccess(result -> {
                    policy = result;
                    if (policy.getRules() == null) {
                        policy.setRules(new ArrayList<>());
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

    @Override
    protected void onBind() {
        addSaveButtonHandler();
        addAddButtonHandler();
        addEditButtonHandler();
        addCopyButtonHandler();
        addDeleteButtonHandler();
        addMoveUpButtonHandler();
        addMoveDownButtonHandler();
        addListSelectionHandler();
        addEnabledClickHandler();

        listPresenter.setActionMenuItemProvider(this::buildActionMenuItems);

        super.onBind();
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
                                .text("Add new rule above")
                                .command(() ->
                                        addNewRule(rule.getRuleNumber() - 1)))
                .withIconMenuItemIf(!isDefaultRule, itemBuilder ->
                        itemBuilder
                                .icon(SvgImage.ADD_BELOW)
                                .text("Add new rule below")
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
                                .text("Copy Rule")
                                .command(() ->
                                        copyRule(rule)))
                .withIconMenuItemIf(!isDefaultRule, itemBuilder ->
                        itemBuilder
                                .icon(SvgImage.DELETE)
                                .text("Delete Rule")
                                .command(() ->
                                        deleteRule(rule)))
                .withIconMenuItemIf(!isDefaultRule && rule.getRuleNumber() > 0, itemBuilder ->
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
        listPresenter.setEnabledStateHandler((rule, isEnabled) -> {
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

                    listPresenter.getSelectionModel().setSelected(visibleRules.get(index));
                }
            }
        });
    }

    private void addListSelectionHandler() {
        registerHandler(listPresenter.getSelectionModel().addSelectionHandler(event -> {
            final DataRetentionRule rule = listPresenter.getSelectionModel().getSelected();
            if (rule != null) {
                expressionPresenter.read(rule.getExpression());
                if (event.getSelectionType().isDoubleSelect() && !isDefaultRule(rule)) {
                    edit(rule);
                }
            } else {
                expressionPresenter.read(null);
            }
            updateButtons();
        }));
    }

    private void addMoveDownButtonHandler() {
        registerHandler(moveDownButton.addClickHandler(event -> {
            if (visibleRules != null) {
                final DataRetentionRule rule = listPresenter.getSelectionModel().getSelected();
                moveRuleDown(rule);
            }
        }));
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
                listPresenter.getSelectionModel().setSelected(visibleRules.get(index));
            }
        }
    }

    private void addMoveUpButtonHandler() {
        registerHandler(moveUpButton.addClickHandler(event -> {
            if (visibleRules != null) {
                final DataRetentionRule rule = listPresenter.getSelectionModel().getSelected();
                moveRuleUp(rule);
            }
        }));
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
                listPresenter.getSelectionModel().setSelected(visibleRules.get(index));
            }
        }
    }

    private void addDeleteButtonHandler() {
        registerHandler(deleteButton.addClickHandler(event -> {
            if (visibleRules != null) {

                final DataRetentionRule rule = listPresenter.getSelectionModel().getSelected();
                deleteRule(rule);
            }
        }));
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
                                    listPresenter.getSelectionModel().setSelected(visibleRules.get(index));
                                } else {
                                    listPresenter.getSelectionModel().clear();
                                }
                            }
                        }
                    });
        }
    }

    private void addCopyButtonHandler() {
        registerHandler(copyButton.addClickHandler(event -> {
            if (visibleRules != null) {
                final DataRetentionRule selected = listPresenter.getSelectionModel().getSelected();
                copyRule(selected);
            }
        }));
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

            listPresenter.getSelectionModel().setSelected(visibleRules.get(index));
        }
    }

    private void addEditButtonHandler() {
        registerHandler(editButton.addClickHandler(event -> {
            if (visibleRules != null) {
                final DataRetentionRule selected = listPresenter.getSelectionModel().getSelected();
                editRule(selected);
            }
        }));
    }

    private void editRule(final DataRetentionRule rule) {
        if (rule != null && !isDefaultRule(rule)) {
            edit(rule);
        }
    }

    private void addAddButtonHandler() {
        registerHandler(addButton.addClickHandler(event -> {
            if (visibleRules != null) {
                addNewRule();
            }
        }));
    }

    private void addSaveButtonHandler() {
        registerHandler(saveButton.addClickHandler(event -> {
            // Get the user's rules without our default one
            policy.setRules(getUserRules());

            restFactory
                    .create(DATA_RETENTION_RULES_RESOURCE)
                    .method(res -> res.update(policy))
                    .onSuccess(result -> {
                        policy = result;
                        setVisibleRules(policy.getRules());
                        listPresenter.getSelectionModel().clear();

                        update();
                        setDirty(false);
                    })
                    .taskMonitorFactory(this)
                    .exec();
        }));
    }

    private void addNewRule() {
        final DataRetentionRule selected = listPresenter.getSelectionModel().getSelected();
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

        final DataRetentionRulePresenter editRulePresenter = editRulePresenterProvider.get();
        editRulePresenter.read(newRule);

        final PopupSize popupSize = PopupSize.resizable(800, 600);
        ShowPopupEvent.builder(editRulePresenter)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(popupSize)
                .caption("Add New Rule")
                .onShow(e -> editRulePresenter.focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        final DataRetentionRule rule = editRulePresenter.write();
                        visibleRules.add(ruleNumber, rule);

                        update();
                        setDirty(true);
                        listPresenter.getSelectionModel().setSelected(visibleRules.get(0));
                    }
                    e.hide();
                })
                .fire();
    }

    private void edit(final DataRetentionRule existingRule) {
        final DataRetentionRulePresenter editRulePresenter = editRulePresenterProvider.get();
        editRulePresenter.read(existingRule);

        final PopupSize popupSize = PopupSize.resizable(800, 600);
        ShowPopupEvent.builder(editRulePresenter)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(popupSize)
                .caption("Edit Rule")
                .onShow(e -> listPresenter.focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        final DataRetentionRule rule = editRulePresenter.write();
                        final int index = visibleRules.indexOf(existingRule);
                        visibleRules.remove(index);
                        visibleRules.add(index, rule);

                        update();
                        // Only mark the policies as dirty if the rule was actually changed.
                        if (!existingRule.equals(rule)) {
                            setDirty(true);
                        }

                        listPresenter.getSelectionModel().setSelected(visibleRules.get(index));
                    }
                    e.hide();
                })
                .fire();
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
            listPresenter.setData(visibleRules);
            // Update the policy so the impact tab can see the unsaved changes
            policy.setRules(getUserRules());
        }
        updateButtons();
    }

    private void updateButtons() {
        final boolean loadedPolicy = visibleRules != null;
        final DataRetentionRule selection = listPresenter.getSelectionModel().getSelected();
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

    boolean isDirty() {
        return dirty;
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

        void setTableView(View view);

        void setExpressionView(View view);
    }
}
