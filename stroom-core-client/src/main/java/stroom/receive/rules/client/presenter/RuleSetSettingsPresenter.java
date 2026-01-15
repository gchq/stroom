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

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmCallback;
import stroom.alert.client.event.ConfirmEvent;
import stroom.docref.DocRef;
import stroom.entity.client.presenter.DocumentEditPresenter;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionUtil;
import stroom.query.api.datasource.ConditionSet;
import stroom.query.api.datasource.QueryField;
import stroom.query.client.ExpressionTreePresenter;
import stroom.query.client.presenter.SimpleFieldSelectionListModel;
import stroom.receive.rules.client.presenter.RuleSetSettingsPresenter.RuleSetSettingsView;
import stroom.receive.rules.shared.ReceiveAction;
import stroom.receive.rules.shared.ReceiveDataRule;
import stroom.receive.rules.shared.ReceiveDataRules;
import stroom.svg.client.SvgPresets;
import stroom.ui.config.client.UiConfigCache;
import stroom.ui.config.shared.ExtendedUiConfig;
import stroom.util.shared.NullSafe;
import stroom.widget.button.client.ButtonView;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.MultiSelectEvent;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.SafeHtmlUtil;

import com.google.gwt.dom.client.Style.BorderStyle;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class RuleSetSettingsPresenter
        extends DocumentEditPresenter<RuleSetSettingsView, ReceiveDataRules> {

    private final RuleSetListPresenter listPresenter;
    private final ExpressionTreePresenter expressionPresenter;
    private final Provider<RulePresenter> editRulePresenterProvider;
    private final SimpleFieldSelectionListModel fieldSelectionBoxModel = new SimpleFieldSelectionListModel();
    private final UiConfigCache uiConfigCache;
    private List<ReceiveDataRule> rules;
    private List<QueryField> fields;

    private final ButtonView addButton;
    private final ButtonView editButton;
    private final ButtonView copyButton;
    private final ButtonView disableButton;
    private final ButtonView deleteButton;
    private final ButtonView moveUpButton;
    private final ButtonView moveDownButton;

    @Inject
    public RuleSetSettingsPresenter(final EventBus eventBus,
                                    final RuleSetSettingsView view,
                                    final RuleSetListPresenter listPresenter,
                                    final ExpressionTreePresenter expressionPresenter,
                                    final Provider<RulePresenter> editRulePresenterProvider,
                                    final UiConfigCache uiConfigCache) {
        super(eventBus, view);
        this.listPresenter = listPresenter;
        this.expressionPresenter = expressionPresenter;
        this.editRulePresenterProvider = editRulePresenterProvider;
        this.uiConfigCache = uiConfigCache;

        getView().setTableView(listPresenter.getView());
        getView().setExpressionView(expressionPresenter.getView());

        // Stop users from selecting expression items.
        expressionPresenter.setSelectionModel(null);

        addButton = listPresenter.add(SvgPresets.ADD.title("Add new rule"));
        editButton = listPresenter.add(SvgPresets.EDIT.title("Edit selected rule"));
        copyButton = listPresenter.add(SvgPresets.COPY.title("Copy selected rule"));
        disableButton = listPresenter.add(SvgPresets.DISABLE.title("Disable/enable selected rule"));
        deleteButton = listPresenter.add(SvgPresets.DELETE.title("Delete selected rule"));
        moveUpButton = listPresenter.add(SvgPresets.UP.title("Move selected rule up"));
        moveDownButton = listPresenter.add(SvgPresets.DOWN.title("Move selected rule down"));

        listPresenter.getView().asWidget().getElement().getStyle().setBorderStyle(BorderStyle.NONE);

        updateButtons();
    }

    @Override
    protected void onBind() {
        registerHandler(addButton.addClickHandler(this::addRuleButtonClickHandler));
        registerHandler(editButton.addClickHandler(this::editButtonClickHandler));
        registerHandler(copyButton.addClickHandler(this::copyRuleButtonClickHandler));
        registerHandler(disableButton.addClickHandler(this::disableButtonClickHandler));
        registerHandler(deleteButton.addClickHandler(this::deleteButtonClickHandler));
        registerHandler(moveUpButton.addClickHandler(this::moveUpButtonClickHandler));
        registerHandler(moveDownButton.addClickHandler(this::moveDownButtonClickHandler));

        registerHandler(listPresenter.getSelectionModel().addSelectionHandler(this::listSelectionHandler));

        super.onBind();
    }

//    private void saveRuleButtonClickHandler(final ClickEvent event) {
//        if (!isReadOnly() && rules != null) {
//            SaveDocumentEvent.fire(RuleSetSettingsPresenter.this, this);
//            if (NullSafe.isEmptyCollection(fields)) {
//                AlertEvent.fireError(
//                        RuleSetSettingsPresenter.this,
//                        "You need to create one or more fields before you can add a rule.",
//                        null,
//                        null);
//            } else {
//                add();
//            }
//        }
//    }

    private void addRuleButtonClickHandler(final ClickEvent event) {
        if (!isReadOnly() && rules != null) {
            if (NullSafe.isEmptyCollection(fields)) {
                AlertEvent.fireError(
                        RuleSetSettingsPresenter.this,
                        "You need to create one or more fields before you can add a rule.",
                        null,
                        null);
            } else {
                add();
            }
        }
    }

    private void editButtonClickHandler(final ClickEvent event) {
        if (!isReadOnly() && rules != null) {
            final ReceiveDataRule selected = listPresenter.getSelectionModel().getSelected();
            if (selected != null) {
                edit(selected);
            }
        }
    }

    private void copyRuleButtonClickHandler(final ClickEvent event) {
        if (!isReadOnly() && rules != null) {
            final ReceiveDataRule selected = listPresenter.getSelectionModel().getSelected();
            if (selected != null) {
                final ReceiveDataRule newRule = new ReceiveDataRule(
                        selected.getRuleNumber() + 1,
                        System.currentTimeMillis(),
                        selected.getName(),
                        selected.isEnabled(),
                        selected.getExpression(),
                        selected.getAction());

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
    }

    private void disableButtonClickHandler(final ClickEvent event) {
        if (!isReadOnly() && rules != null) {
            final List<ReceiveDataRule> selectedItems = listPresenter.getSelectionModel().getSelectedItems();
            if (NullSafe.hasItems(selectedItems)) {
                final List<ReceiveDataRule> newSelection = new ArrayList<>(selectedItems.size());
                for (final ReceiveDataRule rule : selectedItems) {
                    final ReceiveDataRule newRule = rule.copy()
                            .withEnabled(!rule.isEnabled())
                            .build();
                    newSelection.add(newRule);
                    final int index = rules.indexOf(rule);
                    rules.remove(index);
                    rules.add(index, newRule);
                }
                listPresenter.getSelectionModel().setSelectedItems(newSelection);
                update();
                setDirty(true);
            }
        }
    }

    private void deleteButtonClickHandler(final ClickEvent event) {
        if (!isReadOnly() && rules != null) {
            final List<ReceiveDataRule> rules = listPresenter.getSelectionModel().getSelectedItems();
            final String msg = rules.size() > 1
                    ? "Are you sure you want to delete this rule?"
                    : "Are you sure you want to delete the selected rules?";
            ConfirmEvent.fire(this, msg, ok -> {
                if (ok) {
                    this.rules.removeAll(rules);
                    listPresenter.getSelectionModel().clear();
                    update();
                    setDirty(true);
                }
            });
        }
    }

    private void moveUpButtonClickHandler(final ClickEvent event) {
        if (!isReadOnly() && rules != null) {
            final ReceiveDataRule rule = listPresenter.getSelectionModel().getSelected();
            if (rule != null) {
                int index = rules.indexOf(rule);
                if (index > 0) {
                    index--;
                    moveRule(rule, index);
                }
            }
        }
    }

    private void moveDownButtonClickHandler(final ClickEvent event) {
        if (!isReadOnly() && rules != null) {
            final ReceiveDataRule rule = listPresenter.getSelectionModel().getSelected();
            if (rule != null) {
                int index = rules.indexOf(rule);
                if (index < rules.size() - 1) {
                    index++;
                    moveRule(rule, index);
                }
            }
        }
    }

    private void moveRule(final ReceiveDataRule rule, final int index) {
        rules.remove(rule);
        rules.add(index, rule);
        update();
        setDirty(true);

        // Re-select the rule.
        listPresenter.getSelectionModel().setSelected(rules.get(index));
    }

    private void listSelectionHandler(final MultiSelectEvent selectEvent) {
        if (!isReadOnly()) {
            final ReceiveDataRule rule = listPresenter.getSelectionModel().getSelected();
            if (rule != null) {
                expressionPresenter.read(rule.getExpression());
                if (selectEvent.getSelectionType().isDoubleSelect()) {
                    edit(rule);
                }
            } else {
                expressionPresenter.read(null);
            }
            updateButtons();
        }
    }


    private void add() {
        final ReceiveDataRule newRule = new ReceiveDataRule(
                0,
                System.currentTimeMillis(),
                "",
                true,
                ExpressionOperator.builder().build(),
                ReceiveAction.RECEIVE);
        final RulePresenter editRulePresenter = editRulePresenterProvider.get();
        editRulePresenter.read(newRule, fieldSelectionBoxModel);

        showRulePresenter(editRulePresenter, () -> {
            final ReceiveDataRule rule = editRulePresenter.write();
            rules.add(0, rule);
            update();
            listPresenter.getSelectionModel().setSelected(rule);
            setDirty(true);
        });
    }

    private void edit(final ReceiveDataRule existingRule) {
        final RulePresenter editRulePresenter = editRulePresenterProvider.get();
        editRulePresenter.read(existingRule, fieldSelectionBoxModel);

        showRulePresenter(editRulePresenter, () -> {
            final ReceiveDataRule rule = editRulePresenter.write();
            final int index = rules.indexOf(existingRule);
            rules.remove(index);
            rules.add(index, rule);

            update();
            listPresenter.getSelectionModel().setSelected(rule);

            // Only mark the policies as dirty if the rule was actually changed.
            if (!existingRule.equals(rule)) {
                setDirty(true);
            }
        });
    }


    private void showRulePresenter(final RulePresenter rulePresenter,
                                   final Runnable okHandler) {
        final PopupSize popupSize = PopupSize.resizable(1_000, 600);
        uiConfigCache.get(extendedUiConfig -> {
            ShowPopupEvent.builder(rulePresenter)
                    .popupType(PopupType.OK_CANCEL_DIALOG)
                    .popupSize(popupSize)
                    .caption("Edit Rule")
                    .onShow(e -> listPresenter.focus())
                    .onHideRequest(e -> {
                        if (e.isOk()) {
                            validateRules(rulePresenter, extendedUiConfig, isConfirmOk -> {
                                if (isConfirmOk) {
                                    okHandler.run();
                                    e.hide();
                                } else {
                                    e.reset();
                                }
                            });
                        } else {
                            e.hide();
                        }
                    })
                    .fire();
        });
    }

    private void validateRules(final RulePresenter rulePresenter,
                               final ExtendedUiConfig extendedUiConfig,
                               final ConfirmCallback confirmCallback) {
        final List<ExpressionTerm> unHashableTerms = getUnhashableTermsInExpression(
                rulePresenter,
                extendedUiConfig);

        if (unHashableTerms.isEmpty()) {
            confirmCallback.onResult(true);
        } else {
            final HtmlBuilder htmlBuilder = HtmlBuilder.builder()
                    .para("The following terms have obfuscated fields and conditions " +
                          "that do not support obfuscation:");

            for (final ExpressionTerm term : unHashableTerms) {
                htmlBuilder.para(termBuilder -> {
                    termBuilder.code(codeBuilder -> codeBuilder.append(term.getField()))
                            .append(SafeHtmlUtil.ENSP)
                            .append(term.getCondition().getDisplayValue())
                            .append(SafeHtmlUtil.ENSP)
                            .code(codeBuilder -> codeBuilder.append(term.getValue()));
                });
            }

            ConfirmEvent.fireWarn(
                    rulePresenter,
                    SafeHtmlUtil.toParagraphs(
                            "This rule contains conditions that are not compatible with obfuscated fields. " +
                            "Values in the effected terms will not be obfuscated on Stroom-Proxy.\n" +
                            "Do you wish to continue?"),
                    htmlBuilder.toSafeHtml(),
                    confirmCallback);
        }
    }

    private List<ExpressionTerm> getUnhashableTermsInExpression(final RulePresenter rulePresenter,
                                                                final ExtendedUiConfig extendedUiConfig) {
        final ReceiveDataRule receiveDataRule = rulePresenter.write();
        final ExpressionOperator expression = receiveDataRule.getExpression();
        if (expression != null) {
            final Set<String> obfuscatedFields = extendedUiConfig.getObfuscatedFields();
            final List<String> fieldsInExpr = ExpressionUtil.fields(expression);
            if (NullSafe.stream(fieldsInExpr).anyMatch(obfuscatedFields::contains)) {
                final List<ExpressionTerm> unhashableTerms = new ArrayList<>();
                // We have obfuscated fields so see if the conditions are ok
                ExpressionUtil.walkExpressionTree(expression, expressionItem -> {
                    if (expressionItem instanceof final ExpressionTerm term
                        && obfuscatedFields.contains(term.getField())
                        && term.getCondition() != null
                        && !ConditionSet.OBFUSCATABLE_CONDITIONS.supportsCondition(term.getCondition())) {
                        unhashableTerms.add(term);
                    }
                    return true;
                });
                return unhashableTerms;
            } else {
                return Collections.emptyList();
            }
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    protected void onRead(final DocRef docRef,
                          final ReceiveDataRules document,
                          final boolean readOnly) {
        updateButtons();

        if (document != null) {
            fieldSelectionBoxModel.clear();
            fieldSelectionBoxModel.addItems(document.getFields());
            rules = document.getRules();
            fields = document.getFields();
            listPresenter.getSelectionModel()
                    .clear();
            setDirty(false);
            update();
        }
    }

    @Override
    protected ReceiveDataRules onWrite(final ReceiveDataRules document) {
        document.setRules(this.rules);
        return document;
    }

    private void update() {
        if (rules != null) {
            // Set rule numbers on all of the rules for display purposes.
            for (int i = 0; i < rules.size(); i++) {
                final ReceiveDataRule rule = rules.get(i);
                final ReceiveDataRule newRule = new ReceiveDataRule(
                        i + 1,
                        rule.getCreationTime(),
                        rule.getName(),
                        rule.isEnabled(),
                        rule.getExpression(),
                        rule.getAction());
                rules.set(i, newRule);
            }
            listPresenter.setData(rules);
        }
        updateButtons();
    }

    private void updateButtons() {
//        GWT.log("isReadOnly: " + isReadOnly());
        final boolean loadedPolicy = rules != null;
        if (loadedPolicy) {
            final MultiSelectionModel<ReceiveDataRule> selectionModel = listPresenter.getSelectionModel();
            final Boolean areSelectedEnabled;
            addButton.setEnabled(!isReadOnly());

            if (selectionModel.getSelectedCount() == 0) {
                areSelectedEnabled = null;
            } else if (selectionModel.getSelectedCount() == 1) {
                final ReceiveDataRule selection = listPresenter.getSelectionModel().getSelected();
                final int index = rules.indexOf(selection);
                areSelectedEnabled = selection.isEnabled();
                editButton.setEnabled(!isReadOnly());
                copyButton.setEnabled(!isReadOnly());
                disableButton.setEnabled(!isReadOnly());
                deleteButton.setEnabled(!isReadOnly());
                moveUpButton.setEnabled(!isReadOnly() && index > 0);
                moveDownButton.setEnabled(!isReadOnly() && index >= 0 && index < rules.size() - 1);
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
                disableButton.setEnabled(enabledStates.size() == 1);
                deleteButton.setEnabled(true);
                moveUpButton.setEnabled(false);
                moveDownButton.setEnabled(false);
            }

            if (areSelectedEnabled != null) {
                if (areSelectedEnabled) {
                    disableButton.setTitle("Disable selected rules");
                } else {
                    disableButton.setTitle("Enable selected rules");
                }
            } else {
                disableButton.setTitle("Select one or more rules with the same enabled state to enable/disable them.");
            }
        } else {
            addButton.setEnabled(false);
            editButton.setEnabled(false);
            copyButton.setEnabled(false);
            disableButton.setEnabled(false);
            deleteButton.setEnabled(false);
            moveUpButton.setEnabled(false);
            moveDownButton.setEnabled(false);
        }
    }

    @Override
    public void setDirty(final boolean dirty) {
        super.setDirty(dirty);
        updateButtons();
    }

    // --------------------------------------------------------------------------------


    public interface RuleSetSettingsView extends View {

        void setTableView(View view);

        void setExpressionView(View view);
    }
}
