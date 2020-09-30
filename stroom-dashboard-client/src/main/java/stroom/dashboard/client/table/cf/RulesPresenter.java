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
 *
 */

package stroom.dashboard.client.table.cf;

import com.google.gwt.dom.client.Style.BorderStyle;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.View;
import stroom.alert.client.event.ConfirmEvent;
import stroom.dashboard.client.main.AbstractSettingsTabPresenter;
import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.ConditionalFormattingRule;
import stroom.dashboard.shared.Field;
import stroom.dashboard.shared.TableComponentSettings;
import stroom.datasource.api.v2.DataSourceField;
import stroom.document.client.event.DirtyEvent;
import stroom.document.client.event.DirtyEvent.DirtyHandler;
import stroom.document.client.event.HasDirtyHandlers;
import stroom.svg.client.SvgPresets;
import stroom.widget.button.client.ButtonView;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class RulesPresenter
        extends AbstractSettingsTabPresenter<RulesPresenter.RulesView>
        implements HasDirtyHandlers {
    private final RuleListPresenter listPresenter;
//    private final ExpressionTreePresenter expressionPresenter;
    private final Provider<RulePresenter> editRulePresenterProvider;

    private List<DataSourceField> fields;
//    private Map<String, String> fieldNameToIdMap; // column name => column id
//    private Map<String, String> fieldIdToNameMap; // column id => column name
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
//        this.expressionPresenter = expressionPresenter;
        this.editRulePresenterProvider = editRulePresenterProvider;

        getView().setTableView(listPresenter.getView());
//        getView().setExpressionView(expressionPresenter.getView());

        // Stop users from selecting expression items.
//        expressionPresenter.setSelectionModel(null);

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
    protected void onBind() {
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
                final ConditionalFormattingRule newRule = new ConditionalFormattingRule.Builder(selected)
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
                final ConditionalFormattingRule newRule = new ConditionalFormattingRule.Builder(selected)
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
                int index = rules.indexOf(rule);
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
                int index = rules.indexOf(rule);
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
        final ConditionalFormattingRule newRule = new ConditionalFormattingRule.Builder().enabled(true).build();
        final RulePresenter editRulePresenter = editRulePresenterProvider.get();
        editRulePresenter.read(newRule, fields);

        final PopupSize popupSize = new PopupSize(800, 400, 300, 300, 2000, 2000, true);
        ShowPopupEvent.fire(RulesPresenter.this, editRulePresenter, PopupType.OK_CANCEL_DIALOG, popupSize, "Add New Rule", new PopupUiHandlers() {
            @Override
            public void onHideRequest(final boolean autoClose, final boolean ok) {
                if (ok) {
                    final ConditionalFormattingRule rule = editRulePresenter.write();
                    rules.add(rule);
                    update();
                    listPresenter.getSelectionModel().setSelected(rule);
                    setDirty(true);
                }

                HidePopupEvent.fire(RulesPresenter.this, editRulePresenter);
            }

            @Override
            public void onHide(final boolean autoClose, final boolean ok) {
                // Do nothing.
            }
        });
    }

    private void edit(final ConditionalFormattingRule existingRule) {
        final RulePresenter editRulePresenter = editRulePresenterProvider.get();
        editRulePresenter.read(existingRule, fields);

        final PopupSize popupSize = new PopupSize(
                800,
                400,
                300,
                300,
                2000,
                2000,
                true);

        ShowPopupEvent.fire(
                RulesPresenter.this,
                editRulePresenter,
                PopupType.OK_CANCEL_DIALOG,
                popupSize,
                "Edit Rule",
                new PopupUiHandlers() {

            @Override
            public void onHideRequest(final boolean autoClose, final boolean ok) {
                if (ok) {
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

                HidePopupEvent.fire(RulesPresenter.this, editRulePresenter);
            }

            @Override
            public void onHide(final boolean autoClose, final boolean ok) {
                // Do nothing.
            }
        });
    }


    @Override
    public void read(final ComponentConfig componentConfig) {
        final TableComponentSettings settings = (TableComponentSettings) componentConfig.getSettings();

        final Predicate<Field> nonSpecialFieldsPredicate = field -> !field.isSpecial();

        // We have to deal in field names (aka column names) here as all the
        // exp tree code only has a single field/term name so can't cope with working with
        // ids and mapping to col name for the ui.
        this.fields = settings
                .getFields()
                .stream()
                .filter(nonSpecialFieldsPredicate) // ignore the special EventId/StreamId
                .map(field -> new DataSourceField.Builder()
                        .type(DataSourceField.DataSourceFieldType.INTEGER_FIELD)
                        .name(field.getName())
                        .build())
                .collect(Collectors.toList());

        if (settings.getConditionalFormattingRules() != null) {
            this.rules = settings.getConditionalFormattingRules();
        } else {
            this.rules.clear();
        }

        listPresenter.getSelectionModel().clear();
        setDirty(false);
        update();
    }

    @Override
    public void write(final ComponentConfig componentConfig) {
        final TableComponentSettings settings = (TableComponentSettings) componentConfig.getSettings();
        settings.setConditionalFormattingRules(rules);
    }

    @Override
    public boolean validate() {
        return true;
    }

    @Override
    public boolean isDirty(final ComponentConfig componentData) {
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

    public interface RulesView extends View {
        void setTableView(View view);

//        void setExpressionView(View view);
    }
}
