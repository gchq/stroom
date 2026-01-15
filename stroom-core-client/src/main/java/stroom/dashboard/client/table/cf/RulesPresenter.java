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

package stroom.dashboard.client.table.cf;

import stroom.alert.client.event.ConfirmEvent;
import stroom.dashboard.client.main.AbstractSettingsTabPresenter;
import stroom.dashboard.client.table.TablePresenter;
import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.EmbeddedQueryComponentSettings;
import stroom.dashboard.shared.TableComponentSettings;
import stroom.document.client.event.DirtyEvent;
import stroom.document.client.event.DirtyEvent.DirtyHandler;
import stroom.document.client.event.HasDirtyHandlers;
import stroom.query.api.ConditionalFormattingRule;
import stroom.query.api.ConditionalFormattingType;
import stroom.query.api.datasource.QueryField;
import stroom.query.client.presenter.SimpleFieldSelectionListModel;
import stroom.query.shared.QueryTablePreferences;
import stroom.svg.client.SvgPresets;
import stroom.util.shared.NullSafe;
import stroom.util.shared.RandomId;
import stroom.widget.button.client.ButtonView;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.dom.client.Style.BorderStyle;
import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.View;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class RulesPresenter
        extends AbstractSettingsTabPresenter<RulesPresenter.RulesView>
        implements HasDirtyHandlers, Focus {

    private final RuleListPresenter listPresenter;
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

    @Inject
    public RulesPresenter(final EventBus eventBus,
                          final RulesView view,
                          final RuleListPresenter listPresenter,
                          final Provider<RulePresenter> editRulePresenterProvider) {
        super(eventBus, view);
        this.listPresenter = listPresenter;
        this.editRulePresenterProvider = editRulePresenterProvider;

        getView().setTableView(listPresenter.getView());

        addButton = listPresenter.add(SvgPresets.ADD);
        editButton = listPresenter.add(SvgPresets.EDIT);
        copyButton = listPresenter.add(SvgPresets.COPY);
        disableButton = listPresenter.add(SvgPresets.DISABLE);
        deleteButton = listPresenter.add(SvgPresets.DELETE);
        moveUpButton = listPresenter.add(SvgPresets.UP);
        moveDownButton = listPresenter.add(SvgPresets.DOWN);

        listPresenter.getView().asWidget().getElement().getStyle().setBorderStyle(BorderStyle.NONE);

        updateButtons();
    }

    @Override
    public void focus() {
        addButton.focus();
    }

    @Override
    protected void onBind() {
        registerHandler(listPresenter.addDirtyHandler(event -> setDirty(true)));
        registerHandler(addButton.addClickHandler(event -> {
            add();
        }));
        registerHandler(editButton.addClickHandler(event -> {
            final ConditionalFormattingRule selected = listPresenter.getSelectionModel().getSelected();
            if (selected != null) {
                edit(selected);
            }
        }));
        registerHandler(copyButton.addClickHandler(event -> {
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
        }));
        registerHandler(disableButton.addClickHandler(event -> {
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
        }));
        registerHandler(deleteButton.addClickHandler(event -> {
            ConfirmEvent.fire(this, "Are you sure you want to delete this item?", ok -> {
                if (ok) {
                    final ConditionalFormattingRule rule = listPresenter.getSelectionModel().getSelected();
                    rules.remove(rule);
                    listPresenter.getSelectionModel().clear();
                    update();
                    setDirty(true);
                }
            });
        }));
        registerHandler(moveUpButton.addClickHandler(event -> {
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
        }));
        registerHandler(moveDownButton.addClickHandler(event -> {
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
        }));
        registerHandler(listPresenter.getSelectionModel().addSelectionHandler(event -> {
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

        super.onBind();
    }

    private void add() {
        final ConditionalFormattingRule newRule = ConditionalFormattingRule
                .builder()
                .id(RandomId.createId(5))
                .formattingType(ConditionalFormattingType.BACKGROUND)
                .enabled(true)
                .build();
        final RulePresenter editRulePresenter = editRulePresenterProvider.get();
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
        final RulePresenter editRulePresenter = editRulePresenterProvider.get();
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
                        rules.remove(index);
                        rules.add(index, rule);

                        update();
                        listPresenter.getSelectionModel().setSelected(rule);

                        // Only mark the policies as dirty if the rule was actually changed.
                        if (!existingRule.equals(rule)) {
                            setDirty(true);
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
        if (componentConfig.getSettings() instanceof TableComponentSettings) {
            final TableComponentSettings tableComponentSettings =
                    (TableComponentSettings) componentConfig.getSettings();
            read(tableComponentSettings);
        } else if (componentConfig.getSettings() instanceof EmbeddedQueryComponentSettings) {
            final EmbeddedQueryComponentSettings embeddedQueryComponentSettings =
                    (EmbeddedQueryComponentSettings) componentConfig.getSettings();
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
        if (componentConfig.getSettings() instanceof TableComponentSettings) {
            final TableComponentSettings oldSettings =
                    (TableComponentSettings) componentConfig.getSettings();
            final TableComponentSettings newSettings = oldSettings
                    .copy()
                    .conditionalFormattingRules(rules)
                    .build();
            return componentConfig.copy().settings(newSettings).build();
        } else if (componentConfig.getSettings() instanceof EmbeddedQueryComponentSettings) {
            final EmbeddedQueryComponentSettings oldSettings =
                    (EmbeddedQueryComponentSettings) componentConfig.getSettings();
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
            disableButton.setTitle("Disable");
        } else {
            disableButton.setTitle("Enable");
        }

        addButton.setEnabled(loadedPolicy);
        editButton.setEnabled(selected);
        copyButton.setEnabled(selected);
        disableButton.setEnabled(selected);
        deleteButton.setEnabled(selected);
        moveUpButton.setEnabled(selected && index > 0);
        moveDownButton.setEnabled(selected && index >= 0 && index < rules.size() - 1);
    }

    private void setDirty(final boolean dirty) {
        if (this.dirty != dirty) {
            this.dirty = dirty;
            DirtyEvent.fire(this, dirty);
        }
    }

    @Override
    public HandlerRegistration addDirtyHandler(final DirtyHandler handler) {
        return addHandlerToSource(DirtyEvent.getType(), handler);
    }


    // --------------------------------------------------------------------------------


    public interface RulesView extends View {

        void setTableView(View view);
    }
}
