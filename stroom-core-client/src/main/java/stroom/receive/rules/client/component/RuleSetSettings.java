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
import stroom.query.client.ExpressionTreePresenter;
import stroom.query.client.presenter.SimpleFieldSelectionListModel;
import stroom.receive.rules.client.presenter.RuleSetListPresenter;
import stroom.receive.rules.shared.ReceiveAction;
import stroom.receive.rules.shared.ReceiveDataRule;
import stroom.receive.rules.shared.ReceiveDataRules;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.NullSafe;
import stroom.widget.button.client.ButtonView;
import stroom.widget.button.client.Toolbar;
import stroom.widget.component.client.Contexts;
import stroom.widget.component.client.Controller;
import stroom.widget.util.client.MultiSelectEvent;
import stroom.widget.util.client.MultiSelectionModel;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Style.BorderStyle;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/// The receipt rules' Rules tab: the rules, in the order they are tried, which can be added,
/// edited, copied, enabled or disabled, deleted and moved unless the rules are read only
/// ([Contexts#READ_ONLY]), and the selected rule's expression.
public class RuleSetSettings extends Controller {

    private static final Binder BINDER = GWT.create(Binder.class);

    private static final String ENABLE_DISABLE_TITLE = "Enable or Disable Selected Rules";

    private final RuleSetListPresenter listPresenter;
    private final ExpressionTreePresenter expressionPresenter;
    private final Provider<RuleEditorController> ruleEditorProvider;
    private final SimpleFieldSelectionListModel fieldSelectionBoxModel = new SimpleFieldSelectionListModel();
    private final List<ReceiveDataRule> rules = new ArrayList<>();

    private final ButtonView addButton;
    private final ButtonView editButton;
    private final ButtonView copyButton;
    private final ButtonView disableButton;
    private final ButtonView deleteButton;
    private final ButtonView moveUpButton;
    private final ButtonView moveDownButton;

    private boolean readOnly = true;
    private Supplier<List<QueryField>> fields = List::of;
    private Runnable onChange = () -> {
    };

    @UiField
    SimplePanel table;
    @UiField
    SimplePanel expression;

    @Inject
    public RuleSetSettings(final EventBus eventBus,
                           final RuleSetListPresenter listPresenter,
                           final ExpressionTreePresenter expressionPresenter,
                           final Provider<RuleEditorController> ruleEditorProvider) {
        super(eventBus);
        this.listPresenter = own(listPresenter);
        this.expressionPresenter = own(expressionPresenter);
        this.ruleEditorProvider = ruleEditorProvider;

        initWidget(BINDER.createAndBindUi(this));
        table.setWidget(listPresenter.getWidget());
        expression.setWidget(expressionPresenter.getWidget());

        // Stop users from selecting expression items.
        expressionPresenter.setSelectionModel(null);

        final Toolbar ruleActions = listPresenter.getToolbar();
        ruleActions.setName("Receipt rule actions");
        addButton = ruleActions.button(SvgImage.ADD, "New Rule", this::addRule);
        editButton = ruleActions.button(SvgImage.EDIT, "Edit Selected Rule", this::editSelected);
        copyButton = ruleActions.button(SvgImage.COPY, "Duplicate Selected Rule", this::copySelected);
        disableButton = ruleActions.button(SvgImage.DISABLE, ENABLE_DISABLE_TITLE, this::toggleSelected);
        deleteButton = ruleActions.button(SvgImage.DELETE, "Delete Selected Rules", this::deleteSelected);
        moveUpButton = ruleActions.button(SvgImage.UP, "Move Rule Up", () -> moveSelected(-1));
        moveDownButton = ruleActions.button(SvgImage.DOWN, "Move Rule Down", () -> moveSelected(1));

        listPresenter.getView().asWidget().getElement().getStyle().setBorderStyle(BorderStyle.NONE);
        own(getSelectionModel().addSelectionHandler(this::onSelection));

        watch(Contexts.READ_ONLY, readOnly -> {
            this.readOnly = readOnly;
            updateButtons();
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
        return listPresenter.getSelectionModel();
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
            expressionPresenter.read(rule.getExpression());
            if (selectEvent.getSelectionType().isDoubleSelect() && !readOnly) {
                edit(rule);
            }
        } else {
            expressionPresenter.read(null);
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
        listPresenter.setData(rules);
        updateButtons();
    }

    private void updateButtons() {
        final MultiSelectionModel<ReceiveDataRule> selectionModel = getSelectionModel();
        final Boolean areSelectedEnabled;
        addButton.setEnabled(!readOnly);

        if (selectionModel.getSelectedCount() == 0) {
            areSelectedEnabled = null;
            editButton.setEnabled(false);
            copyButton.setEnabled(false);
            disableButton.setEnabled(false);
            deleteButton.setEnabled(false);
            moveUpButton.setEnabled(false);
            moveDownButton.setEnabled(false);
        } else if (selectionModel.getSelectedCount() == 1) {
            final ReceiveDataRule selection = selectionModel.getSelected();
            final int index = rules.indexOf(selection);
            areSelectedEnabled = selection.isEnabled();
            editButton.setEnabled(!readOnly);
            copyButton.setEnabled(!readOnly);
            disableButton.setEnabled(!readOnly);
            deleteButton.setEnabled(!readOnly);
            moveUpButton.setEnabled(!readOnly && index > 0);
            moveDownButton.setEnabled(!readOnly && index >= 0 && index < rules.size() - 1);
        } else {
            // Multi-select
            final Set<Boolean> enabledStates = selectionModel.getSelectedItems()
                    .stream()
                    .map(ReceiveDataRule::isEnabled)
                    .collect(Collectors.toSet());
            areSelectedEnabled = enabledStates.size() == 1
                    ? enabledStates.iterator().next()
                    : null;

            editButton.setEnabled(false);
            copyButton.setEnabled(false);
            disableButton.setEnabled(!readOnly && enabledStates.size() == 1);
            deleteButton.setEnabled(!readOnly);
            moveUpButton.setEnabled(false);
            moveDownButton.setEnabled(false);
        }

        if (areSelectedEnabled != null) {
            if (areSelectedEnabled) {
                disableButton.setTitle("Disable Selected Rules");
            } else {
                disableButton.setTitle("Enable Selected Rules");
            }
        } else if (selectionModel.getSelectedCount() > 1) {
            disableButton.setTitle(ENABLE_DISABLE_TITLE + " (selected rules must share the same enabled state)");
        } else {
            disableButton.setTitle(ENABLE_DISABLE_TITLE);
        }
    }

    private void change() {
        onChange.run();
        updateButtons();
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, RuleSetSettings> {

    }
}
