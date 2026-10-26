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

package stroom.dashboard.client.table.cf;

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.dashboard.client.component.RuleList;
import stroom.dashboard.client.main.AbstractSettingsTabPresenter;
import stroom.dashboard.client.table.TablePresenter;
import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.EmbeddedQueryComponentSettings;
import stroom.dashboard.shared.TableComponentSettings;
import stroom.query.api.ConditionalFormattingRule;
import stroom.query.api.ConditionalFormattingType;
import stroom.query.api.datasource.QueryField;
import stroom.query.client.presenter.SimpleFieldSelectionListModel;
import stroom.query.shared.QueryTablePreferences;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.NullSafe;
import stroom.util.shared.RandomId;
import stroom.widget.button.client.ButtonView;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.presenter.client.View;

import com.google.gwt.dom.client.Style.BorderStyle;
import com.google.gwt.user.client.ui.Focus;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class RulesPresenter
        extends AbstractSettingsTabPresenter<RulesPresenter.RulesView>
        implements Focus {

    private final RuleList listPresenter;
    private final Provider<RulePresenter> editRulePresenterProvider;

    private List<QueryField> fields;
    private List<ConditionalFormattingRule> rules = new ArrayList<>();

    private final ButtonView addButton;
    private final ButtonView editButton;
    private final ButtonView copyButton;
    private final ButtonView disableButton;
    private final ButtonView deleteButton;
    private final ButtonView moveUpButton;
    private final ButtonView moveDownButton;

    private boolean dirty;
    private Runnable onDirtyChange = () -> {
    };

    @Inject
    public RulesPresenter(final EventBus eventBus,
                          final RulesView view,
                          final RuleList listPresenter,
                          final Provider<RulePresenter> editRulePresenterProvider) {
        super(eventBus, view);
        this.listPresenter = listPresenter;
        this.editRulePresenterProvider = editRulePresenterProvider;

        getView().setTableView(listPresenter);

        listPresenter.getToolbar().setName("Conditional formatting rule actions");
        addButton = listPresenter.getToolbar().button(SvgImage.ADD, "New Formatting Rule", this::add);
        editButton = listPresenter.getToolbar().button(SvgImage.EDIT, "Edit Selected Formatting Rule",
                this::onEdit);
        editButton.setEnabled(false);
        copyButton = listPresenter.getToolbar().button(SvgImage.COPY, "Duplicate Selected Formatting Rule",
                this::onCopy);
        copyButton.setEnabled(false);
        disableButton = listPresenter.getToolbar().button(SvgImage.DISABLE, "Disable Selected Formatting Rule",
                this::onDisable);
        disableButton.setEnabled(false);
        deleteButton = listPresenter.getToolbar().button(SvgImage.DELETE, "Delete Selected Formatting Rule",
                this::onDelete);
        deleteButton.setEnabled(false);
        moveUpButton = listPresenter.getToolbar().button(SvgImage.UP, "Move Formatting Rule Up",
                this::onMoveUp);
        moveUpButton.setEnabled(false);
        moveDownButton = listPresenter.getToolbar().button(SvgImage.DOWN, "Move Formatting Rule Down",
                this::onMoveDown);
        moveDownButton.setEnabled(false);

        listPresenter.getElement().getStyle().setBorderStyle(BorderStyle.NONE);

        updateButtons();

        listPresenter.setOnChange(() -> setDirty(true));
        own(listPresenter.getSelectionModel().addSelectionHandler(event -> {
            final ConditionalFormattingRule rule = listPresenter.getSelectionModel().getSelected();
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
    }

    @Override
    public void focus() {
        addButton.focus();
    }

    private void onEdit() {
        final ConditionalFormattingRule selected = listPresenter.getSelectionModel().getSelected();
        if (selected != null) {
            edit(selected);
        }
    }

    private void onCopy() {
        final ConditionalFormattingRule selected = listPresenter.getSelectionModel().getSelected();
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
            listPresenter.getSelectionModel().setSelected(newRule);
        }
    }

    private void onDisable() {
        final ConditionalFormattingRule selected = listPresenter.getSelectionModel().getSelected();
        if (selected != null) {
            final ConditionalFormattingRule newRule = selected
                    .copy()
                    .enabled(!selected.isEnabled())
                    .build();
            final int index = rules.indexOf(selected);
            rules.remove(index);
            rules.add(index, newRule);
            listPresenter.getSelectionModel().setSelected(newRule);
            update();
            setDirty(true);
        }
    }

    private void onDelete() {
        ConfirmEvent.fire(this, "Are you sure you want to delete this item?", ok -> {
            if (ok) {
                final ConditionalFormattingRule rule = listPresenter.getSelectionModel().getSelected();
                rules.remove(rule);
                listPresenter.getSelectionModel().clear();
                update();
                setDirty(true);
            }
        });
    }

    private void onMoveUp() {
        final ConditionalFormattingRule rule = listPresenter.getSelectionModel().getSelected();
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
        final ConditionalFormattingRule rule = listPresenter.getSelectionModel().getSelected();
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
        final RulePresenter editRulePresenter = makeDialog(editRulePresenterProvider);
        final SimpleFieldSelectionListModel selectionBoxModel = new SimpleFieldSelectionListModel();
        selectionBoxModel.addItems(fields);
        editRulePresenter.read(newRule, selectionBoxModel);

        final PopupSize popupSize = PopupSize.resizable(1000, 700);
        ShowPopupEvent.builder(editRulePresenter)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(popupSize)
                .caption("Add New Rule")
                .onShow(e -> editRulePresenter.focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        final ConditionalFormattingRule rule = editRulePresenter.write();
                        rules.add(rule);
                        update();
                        listPresenter.getSelectionModel().setSelected(rule);
                        setDirty(true);
                    }
                    e.hide();
                    // Return focus to the add button
                    addButton.focus();
                })
                .fire();
    }

    private void edit(final ConditionalFormattingRule existingRule) {
        final RulePresenter editRulePresenter = makeDialog(editRulePresenterProvider);
        final SimpleFieldSelectionListModel selectionBoxModel = new SimpleFieldSelectionListModel();
        selectionBoxModel.addItems(fields);
        editRulePresenter.read(existingRule, selectionBoxModel);

        final PopupSize popupSize = PopupSize.resizable(1000, 700);
        ShowPopupEvent.builder(editRulePresenter)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(popupSize)
                .caption("Edit Rule")
                .onShow(e -> editRulePresenter.focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        final ConditionalFormattingRule rule = editRulePresenter.write();
                        final int index = rules.indexOf(existingRule);
                        if (index < 0) {
                            // Deleted behind the dialog, which isn't modal
                            AlertEvent.fireWarn(this, "The rule was deleted while it was being edited, "
                                                      + "so the changes can't be kept.", null);
                        } else {
                            rules.remove(index);
                            rules.add(index, rule);

                            update();
                            listPresenter.getSelectionModel().setSelected(rule);

                            // Only mark the policies as dirty if the rule was actually changed.
                            if (!existingRule.equals(rule)) {
                                setDirty(true);
                            }
                        }
                    }
                    e.hide();
                    // Return focus to the add button
                    addButton.focus();
                })
                .fire();
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
                .map(TablePresenter::buildDsField)
                .collect(Collectors.toList()), settings.getConditionalFormattingRules());
    }

    public void read(final List<QueryField> fields,
                     final List<ConditionalFormattingRule> rules) {
        this.fields = fields;
        if (rules != null) {
            this.rules = rules;
        } else {
            this.rules.clear();
        }
        listPresenter.getSelectionModel().clear();
        setDirty(false);
        update();
    }

    public void read(final QueryTablePreferences queryTablePreferences) {
        if (queryTablePreferences != null) {
            // We have to deal in field names (aka column names) here as all the
            // exp tree code only has a single field/term name so can't cope with working with
            // ids and mapping to col name for the ui.
            this.fields = NullSafe.list(queryTablePreferences.getColumns())
                    .stream()
                    .map(TablePresenter::buildDsField)
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

        listPresenter.getSelectionModel().clear();
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

    public QueryTablePreferences write(final QueryTablePreferences queryTablePreferences) {
        return QueryTablePreferences
                .copy(queryTablePreferences)
                .conditionalFormattingRules(rules)
                .build();
    }

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
        listPresenter.setData(rules);
        updateButtons();
    }

    private void updateButtons() {
        final boolean loadedPolicy = rules != null;
        final ConditionalFormattingRule selection = listPresenter.getSelectionModel().getSelected();
        final boolean selected = loadedPolicy && selection != null;
        int index = -1;
        if (selected) {
            index = rules.indexOf(selection);
        }

        if (selection != null && selection.isEnabled()) {
            disableButton.setTitle("Disable Selected Formatting Rule");
        } else {
            disableButton.setTitle("Enable Selected Formatting Rule");
        }

        addButton.setEnabled(loadedPolicy);
        editButton.setEnabled(selected);
        copyButton.setEnabled(selected);
        disableButton.setEnabled(selected);
        deleteButton.setEnabled(selected);
        moveUpButton.setEnabled(selected && index > 0);
        moveDownButton.setEnabled(selected && index >= 0 && index < rules.size() - 1);
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



    // --------------------------------------------------------------------------------


    public interface RulesView extends View {

        void setTableView(IsWidget view);
    }
}
