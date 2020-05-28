/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.receive.rules.client.presenter;

import stroom.alert.client.event.ConfirmEvent;
import stroom.content.client.event.RefreshContentTabEvent;
import stroom.content.client.presenter.ContentTabPresenter;
import stroom.data.retention.shared.DataRetentionRule;
import stroom.data.retention.shared.DataRetentionRules;
import stroom.data.retention.shared.DataRetentionRulesResource;
import stroom.data.retention.shared.TimeUnit;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.document.client.event.DirtyEvent;
import stroom.document.client.event.DirtyEvent.DirtyHandler;
import stroom.document.client.event.HasDirtyHandlers;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.client.ExpressionTreePresenter;
import stroom.svg.client.Icon;
import stroom.svg.client.SvgPresets;
import stroom.widget.button.client.ButtonView;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Style.BorderStyle;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.View;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DataRetentionPolicyPresenter
        extends ContentTabPresenter<DataRetentionPolicyPresenter.DataRetentionPolicyView>
        implements HasDirtyHandlers {

    private static final DataRetentionRulesResource DATA_RETENTION_RULES_RESOURCE = GWT.create(
            DataRetentionRulesResource.class);

    // This rule exist in the UI only and is never passed to the back end
    // The back end retains all data by default unless a rule specifies otherwise.
    // This rule just makes it clear to the users what is happening.
    private static final DataRetentionRule DEFAULT_UI_ONLY_RETAIN_ALL_RULE = DataRetentionRule.foreverRule(
            Integer.MAX_VALUE,
            0,
            "Default Retain All Rule",
            true,
            new ExpressionOperator.Builder(Op.AND).build());

    private final DataRetentionPolicyListPresenter listPresenter;
    private final ExpressionTreePresenter expressionPresenter;
    private final Provider<DataRetentionRulePresenter> editRulePresenterProvider;
    private final RestFactory restFactory;

    private DataRetentionRules policy;
    private List<DataRetentionRule> visibleRules;

    private ButtonView saveButton;
    private ButtonView addButton;
    private ButtonView editButton;
    private ButtonView copyButton;
    private ButtonView disableButton;
    private ButtonView deleteButton;
    private ButtonView moveUpButton;
    private ButtonView moveDownButton;

    private boolean dirty;
    private String lastLabel;

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

        saveButton = listPresenter.add(SvgPresets.SAVE);
        addButton = listPresenter.add(SvgPresets.ADD);
        editButton = listPresenter.add(SvgPresets.EDIT);
        copyButton = listPresenter.add(SvgPresets.COPY);
        disableButton = listPresenter.add(SvgPresets.DISABLE);
        deleteButton = listPresenter.add(SvgPresets.DELETE);
        moveUpButton = listPresenter.add(SvgPresets.UP);
        moveDownButton = listPresenter.add(SvgPresets.DOWN);

        listPresenter.getView().asWidget().getElement().getStyle().setBorderStyle(BorderStyle.NONE);

        updateButtons();

        final Rest<DataRetentionRules> rest = restFactory.create();
        rest
                .onSuccess(result -> {
                    policy = result;

                    if (policy.getRules() == null) {
                        policy.setRules(new ArrayList<>());
                    }

                    setVisibleRules(policy.getRules());

                    update();
                })
                .call(DATA_RETENTION_RULES_RESOURCE)
                .read();
    }

    private void setVisibleRules(final List<DataRetentionRule> rules) {
        List<DataRetentionRule> allRules = new ArrayList<>(rules);
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

    private boolean isDefaultRule(final DataRetentionRule rule) {
        if (rule == null || visibleRules == null || visibleRules.size() < 1) {
            return false;
        } else {
            return rule.getRuleNumber() == visibleRules.get(visibleRules.size() - 1).getRuleNumber();
        }
    }

    @Override
    protected void onBind() {
        registerHandler(saveButton.addClickHandler(event -> {
            // Get the user's rules without our default one
            policy.setRules(getUserRules());

            final Rest<DataRetentionRules> rest = restFactory.create();
            rest
                    .onSuccess(result -> {
                        policy = result;
//                        this.rules = policy.getRules();
                        setVisibleRules(policy.getRules());
                        listPresenter.getSelectionModel().clear();

                        update();
                        setDirty(false);
                    })
                    .call(DATA_RETENTION_RULES_RESOURCE)
                    .update(policy);
        }));
        registerHandler(addButton.addClickHandler(event -> {
            if (visibleRules != null) {
                add();
            }
        }));
        registerHandler(editButton.addClickHandler(event -> {
            if (visibleRules != null) {
                final DataRetentionRule selected = listPresenter.getSelectionModel().getSelected();
                if (selected != null && !isDefaultRule(selected)) {
                    edit(selected);
                }
            }
        }));
        registerHandler(copyButton.addClickHandler(event -> {
            if (visibleRules != null) {
                final DataRetentionRule selected = listPresenter.getSelectionModel().getSelected();
                if (selected != null) {
                    final DataRetentionRule newRule = new DataRetentionRule(
                            selected.getRuleNumber(),
                            System.currentTimeMillis(),
                            selected.getName(),
                            selected.isEnabled(),
                            selected.getExpression(),
                            selected.getAge(),
                            selected.getTimeUnit(),
                            selected.isForever());

                    int index = visibleRules.indexOf(selected);
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
        }));
        registerHandler(disableButton.addClickHandler(event -> {
            if (visibleRules != null) {
                final DataRetentionRule selected = listPresenter.getSelectionModel().getSelected();
                if (selected != null && !isDefaultRule(selected)) {
                    final DataRetentionRule newRule = new DataRetentionRule(
                            selected.getRuleNumber(),
                            selected.getCreationTime(),
                            selected.getName(),
                            !selected.isEnabled(),
                            selected.getExpression(),
                            selected.getAge(),
                            selected.getTimeUnit(),
                            selected.isForever());

                    int index = visibleRules.indexOf(selected);
                    visibleRules.remove(index);
                    visibleRules.add(index, newRule);
                    index = visibleRules.indexOf(newRule);

                    update();
                    setDirty(true);

                    listPresenter.getSelectionModel().setSelected(visibleRules.get(index));
                }
            }
        }));
        registerHandler(deleteButton.addClickHandler(event -> {
            if (visibleRules != null) {
                ConfirmEvent.fire(this, "Are you sure you want to delete this item?", ok -> {
                    if (ok) {
                        final DataRetentionRule rule = listPresenter.getSelectionModel().getSelected();
                        if (rule != null && !isDefaultRule(rule)) {
                            int index = visibleRules.indexOf(rule);
                            visibleRules.remove(rule);

                            update();
                            setDirty(true);

                            // Select the next rule.
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
        }));
        registerHandler(moveUpButton.addClickHandler(event -> {
            if (visibleRules != null) {
                final DataRetentionRule rule = listPresenter.getSelectionModel().getSelected();
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
        }));
        registerHandler(moveDownButton.addClickHandler(event -> {
            if (visibleRules != null) {
                final DataRetentionRule rule = listPresenter.getSelectionModel().getSelected();
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
        }));
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

        super.onBind();
    }

    private void add() {
        final DataRetentionRule newRule = new DataRetentionRule(0,
                System.currentTimeMillis(),
                "",
                true,
                new ExpressionOperator.Builder(Op.AND).build(),
                1,
                TimeUnit.YEARS,
                true);

        final DataRetentionRulePresenter editRulePresenter = editRulePresenterProvider.get();
        editRulePresenter.read(newRule);

        final PopupSize popupSize = new PopupSize(
                800,
                400,
                300,
                300,
                2000,
                2000,
                true);

        ShowPopupEvent.fire(
                DataRetentionPolicyPresenter.this,
                editRulePresenter,
                PopupType.OK_CANCEL_DIALOG,
                popupSize,
                "Add New Rule",
                new PopupUiHandlers() {
                    @Override
                    public void onHideRequest(final boolean autoClose, final boolean ok) {
                        if (ok) {
                            final DataRetentionRule rule = editRulePresenter.write();
                            visibleRules.add(0, rule);

                            update();
                            setDirty(true);

                            listPresenter.getSelectionModel().setSelected(visibleRules.get(0));
                        }

                        HidePopupEvent.fire(DataRetentionPolicyPresenter.this, editRulePresenter);
                    }

                    @Override
                    public void onHide(final boolean autoClose, final boolean ok) {
                        // Do nothing.
                    }
                });
    }

    private void edit(final DataRetentionRule existingRule) {
        final DataRetentionRulePresenter editRulePresenter = editRulePresenterProvider.get();
        editRulePresenter.read(existingRule);

        final PopupSize popupSize = new PopupSize(
                800,
                400,
                300,
                300,
                2000,
                2000,
                true);

        ShowPopupEvent.fire(
                DataRetentionPolicyPresenter.this,
                editRulePresenter,
                PopupType.OK_CANCEL_DIALOG,
                popupSize,
                "Edit Rule",
                new PopupUiHandlers() {
                    @Override
                    public void onHideRequest(final boolean autoClose, final boolean ok) {
                        if (ok) {
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

                        HidePopupEvent.fire(DataRetentionPolicyPresenter.this, editRulePresenter);
                    }

                    @Override
                    public void onHide(final boolean autoClose, final boolean ok) {
                        // Do nothing.
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
            listPresenter.setData(visibleRules);
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

        if (selection != null && selection.isEnabled()) {
            disableButton.setTitle("Disable");
        } else {
            disableButton.setTitle("Enable");
        }

        saveButton.setEnabled(loadedPolicy && dirty);
        addButton.setEnabled(loadedPolicy);
        editButton.setEnabled(selected && !isDefaultRule);
        copyButton.setEnabled(selected);
        disableButton.setEnabled(selected && !isDefaultRule);
        deleteButton.setEnabled(selected && !isDefaultRule);
        moveUpButton.setEnabled(selected && !isDefaultRule && index > 0);
        moveDownButton.setEnabled(selected && !isDefaultRule && index >= 0 && index < visibleRules.size() - 2);
    }

    @Override
    public Icon getIcon() {
        return SvgPresets.HISTORY;
    }

    @Override
    public String getLabel() {
        if (isDirty()) {
            return "* " + "Data Retention";
        }

        return "Data Retention";
    }

    private boolean isDirty() {
        return dirty;
    }

    private void setDirty(final boolean dirty) {
        if (this.dirty != dirty) {
            this.dirty = dirty;
            DirtyEvent.fire(this, dirty);
            saveButton.setEnabled(dirty);

            // Only fire tab refresh if the tab has changed.
            if (lastLabel == null || !lastLabel.equals(getLabel())) {
                lastLabel = getLabel();
                RefreshContentTabEvent.fire(this, this);
            }
        }
    }

    @Override
    public HandlerRegistration addDirtyHandler(final DirtyHandler handler) {
        return addHandlerToSource(DirtyEvent.getType(), handler);
    }

    public interface DataRetentionPolicyView extends View {
        void setTableView(View view);

        void setExpressionView(View view);
    }
}
