/*
 * Copyright 2020 Crown Copyright
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
import stroom.dashboard.client.component.Rules.RulesAction;
import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.EmbeddedQueryComponentSettings;
import stroom.dashboard.shared.TableComponentSettings;
import stroom.query.api.ConditionalFormattingRule;
import stroom.query.api.ConditionalFormattingType;
import stroom.query.api.datasource.QueryField;
import stroom.query.client.SimpleFieldSelectionListModel;
import stroom.query.shared.QueryTablePreferences;
import stroom.util.shared.NullSafe;
import stroom.util.shared.RandomId;

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/// A table's conditional formatting rules ([Rules]): a tab of a Table's or Embedded Query's
/// settings, a dialog of a query's results table, or one column's rules on a Table Filter. It adds,
/// edits, copies, enables or disables, deletes and moves the rules.
public class RulesController extends SettingsTabController implements Focus {

    private final Rules view;
    private final RuleList ruleList;
    private final Provider<ConditionalFormattingRuleEditorController> ruleEditorProvider;

    private List<QueryField> fields;
    private List<ConditionalFormattingRule> rules = new ArrayList<>();

    private boolean dirty;
    private Runnable onDirtyChange = () -> {
    };

    /// Made by GIN, with its content.
    ///
    /// @param eventBus           The application's event bus.
    /// @param view               The content.
    /// @param ruleEditorProvider Makes the dialog that adds or edits a rule.
    @Inject
    public RulesController(final EventBus eventBus,
                           final Rules view,
                           final Provider<ConditionalFormattingRuleEditorController> ruleEditorProvider) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        this.ruleList = view.getRuleList();
        this.ruleEditorProvider = ruleEditorProvider;
        view.setOnAction(this::onAction);

        updateButtons();

        ruleList.setOnChange(() -> setDirty(true));
        own(ruleList.getSelectionModel().addSelectionHandler(event -> {
            final ConditionalFormattingRule rule = ruleList.getSelectionModel().getSelected();
            if (rule != null && event.getSelectionType().isDoubleSelect()) {
                edit(rule);
            }
            updateButtons();
        }));
    }

    private void onAction(final RulesAction action) {
        switch (action) {
            case ADD -> add();
            case EDIT -> onEdit();
            case COPY -> onCopy();
            case TOGGLE -> onDisable();
            case DELETE -> onDelete();
            case MOVE_UP -> onMoveUp();
            case MOVE_DOWN -> onMoveDown();
        }
    }

    @Override
    public void focus() {
        view.focusAdd();
    }

    private void onEdit() {
        final ConditionalFormattingRule selected = ruleList.getSelectionModel().getSelected();
        if (selected != null) {
            edit(selected);
        }
    }

    private void onCopy() {
        final ConditionalFormattingRule selected = ruleList.getSelectionModel().getSelected();
        if (selected != null) {
            final ConditionalFormattingRule newRule = selected
                    .copy()
                    .id(RandomId.createId(5))
                    .build();

            final int index = rules.indexOf(selected);

            if (index < rules.size() - 1) {
                rules.add(index + 1, newRule);
            } else {
                rules.add(newRule);
            }

            update();
            ruleList.getSelectionModel().setSelected(newRule);
        }
    }

    private void onDisable() {
        final ConditionalFormattingRule selected = ruleList.getSelectionModel().getSelected();
        if (selected != null) {
            final ConditionalFormattingRule newRule = selected
                    .copy()
                    .enabled(!selected.isEnabled())
                    .build();
            final int index = rules.indexOf(selected);
            rules.remove(index);
            rules.add(index, newRule);
            ruleList.getSelectionModel().setSelected(newRule);
            update();
            setDirty(true);
        }
    }

    private void onDelete() {
        ConfirmEvent.fire(this, "Are you sure you want to delete this item?", ok -> {
            if (ok) {
                final ConditionalFormattingRule rule = ruleList.getSelectionModel().getSelected();
                rules.remove(rule);
                ruleList.getSelectionModel().clear();
                update();
                setDirty(true);
            }
        });
    }

    private void onMoveUp() {
        final ConditionalFormattingRule rule = ruleList.getSelectionModel().getSelected();
        if (rule != null) {
            final int index = rules.indexOf(rule);
            if (index > 0) {
                rules.remove(rule);
                rules.add(index - 1, rule);
                update();
                setDirty(true);
            }
        }
    }

    private void onMoveDown() {
        final ConditionalFormattingRule rule = ruleList.getSelectionModel().getSelected();
        if (rule != null) {
            final int index = rules.indexOf(rule);
            if (index < rules.size() - 1) {
                rules.remove(rule);
                rules.add(index + 1, rule);
                update();
                setDirty(true);
            }
        }
    }

    private void add() {
        final ConditionalFormattingRule newRule = ConditionalFormattingRule
                .builder()
                .id(RandomId.createId(5))
                .formattingType(ConditionalFormattingType.BACKGROUND)
                .enabled(true)
                .build();
        final SimpleFieldSelectionListModel selectionBoxModel = new SimpleFieldSelectionListModel();
        selectionBoxModel.addItems(fields);
        // Made for one use, and disposed once it is closed
        ruleEditorProvider.get().edit(this, "Add New Rule", newRule, selectionBoxModel, rule -> {
            rules.add(rule);
            update();
            ruleList.getSelectionModel().setSelected(rule);
            setDirty(true);
        }, view::focusAdd);
    }

    private void edit(final ConditionalFormattingRule existingRule) {
        final SimpleFieldSelectionListModel selectionBoxModel = new SimpleFieldSelectionListModel();
        selectionBoxModel.addItems(fields);
        // Made for one use, and disposed once it is closed
        ruleEditorProvider.get().edit(this, "Edit Rule", existingRule, selectionBoxModel, rule -> {
            final int index = rules.indexOf(existingRule);
            if (index < 0) {
                // Deleted behind the dialog, which isn't modal
                AlertEvent.fireWarn(this, "The rule was deleted while it was being edited, "
                                          + "so the changes can't be kept.", null);
            } else {
                rules.remove(index);
                rules.add(index, rule);

                update();
                ruleList.getSelectionModel().setSelected(rule);

                // Only mark the policies as dirty if the rule was actually changed.
                if (!existingRule.equals(rule)) {
                    setDirty(true);
                }
            }
        }, view::focusAdd);
    }

    @Override
    public void read(final ComponentConfig componentConfig) {
        if (componentConfig.getSettings() instanceof final TableComponentSettings tableComponentSettings) {
            read(tableComponentSettings);
        } else if (componentConfig.getSettings() instanceof
                final EmbeddedQueryComponentSettings embeddedQueryComponentSettings) {
            read(embeddedQueryComponentSettings.getQueryTablePreferences());
        }
    }

    private void read(final TableComponentSettings settings) {
        // We have to deal in field names (aka column names) here as all the
        // exp tree code only has a single field/term name so can't cope with working with
        // ids and mapping to col name for the ui.
        read(settings
                .getColumns()
                .stream()
                .map(TableController::buildDsField)
                .collect(Collectors.toList()), settings.getConditionalFormattingRules());
    }

    /// Shows rules for the fields given, e.g. one column of a Table Filter.
    ///
    /// @param fields The fields the rules' expressions can use.
    /// @param rules  The rules.
    public void read(final List<QueryField> fields,
                     final List<ConditionalFormattingRule> rules) {
        this.fields = fields;
        if (rules != null) {
            this.rules = rules;
        } else {
            this.rules.clear();
        }
        ruleList.getSelectionModel().clear();
        setDirty(false);
        update();
    }

    /// Shows a query's results table's rules, for its columns.
    ///
    /// @param queryTablePreferences The table's preferences, or `null` for none.
    public void read(final QueryTablePreferences queryTablePreferences) {
        if (queryTablePreferences != null) {
            // We have to deal in field names (aka column names) here as all the
            // exp tree code only has a single field/term name so can't cope with working with
            // ids and mapping to col name for the ui.
            this.fields = NullSafe.list(queryTablePreferences.getColumns())
                    .stream()
                    .map(TableController::buildDsField)
                    .collect(Collectors.toList());

            if (queryTablePreferences.getConditionalFormattingRules() != null) {
                this.rules = new ArrayList<>(queryTablePreferences.getConditionalFormattingRules());
            } else {
                this.rules = new ArrayList<>();
            }
        } else {
            this.fields = new ArrayList<>();
            this.rules = new ArrayList<>();
        }

        ruleList.getSelectionModel().clear();
        setDirty(false);
        update();
    }

    @Override
    public ComponentConfig write(final ComponentConfig componentConfig) {
        if (componentConfig.getSettings() instanceof final TableComponentSettings oldSettings) {
            final TableComponentSettings newSettings = oldSettings
                    .copy()
                    .conditionalFormattingRules(rules)
                    .build();
            return componentConfig.copy().settings(newSettings).build();
        } else if (componentConfig.getSettings() instanceof final EmbeddedQueryComponentSettings oldSettings) {
            final QueryTablePreferences queryTablePreferences =
                    write(oldSettings.getQueryTablePreferences());
            final EmbeddedQueryComponentSettings newSettings = oldSettings
                    .copy()
                    .queryTablePreferences(queryTablePreferences)
                    .build();
            return componentConfig
                    .copy()
                    .settings(newSettings)
                    .build();
        }
        throw new RuntimeException("Unexpected type");
    }

    /// @param queryTablePreferences A query's results table's preferences.
    /// @return A copy with the rules shown.
    public QueryTablePreferences write(final QueryTablePreferences queryTablePreferences) {
        return QueryTablePreferences
                .copy(queryTablePreferences)
                .conditionalFormattingRules(rules)
                .build();
    }

    /// @return The rules shown.
    public List<ConditionalFormattingRule> write() {
        return rules;
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
        ruleList.setData(rules);
        updateButtons();
    }

    private void updateButtons() {
        final boolean loadedPolicy = rules != null;
        final ConditionalFormattingRule selection = ruleList.getSelectionModel().getSelected();
        final boolean selected = loadedPolicy && selection != null;
        int index = -1;
        if (selected) {
            index = rules.indexOf(selection);
        }

        view.setSelectedEnabled(selection != null && selection.isEnabled());

        final Set<RulesAction> actions = EnumSet.noneOf(RulesAction.class);
        if (loadedPolicy) {
            actions.add(RulesAction.ADD);
        }
        if (selected) {
            actions.add(RulesAction.EDIT);
            actions.add(RulesAction.COPY);
            actions.add(RulesAction.TOGGLE);
            actions.add(RulesAction.DELETE);
        }
        if (selected && index > 0) {
            actions.add(RulesAction.MOVE_UP);
        }
        if (selected && index >= 0 && index < rules.size() - 1) {
            actions.add(RulesAction.MOVE_DOWN);
        }
        view.setAvailableActions(actions);
    }

    /// @param onDirtyChange What to run when the rules become changed or unchanged.
    public void setOnDirtyChange(final Runnable onDirtyChange) {
        this.onDirtyChange = onDirtyChange;
    }

    private void setDirty(final boolean dirty) {
        if (this.dirty != dirty) {
            this.dirty = dirty;
            onDirtyChange.run();
        }
    }
}
