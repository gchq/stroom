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

package stroom.policy.client.presenter;

import com.google.gwt.dom.client.Style.BorderStyle;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import stroom.alert.client.event.ConfirmEvent;
import stroom.entity.client.event.DirtyEvent;
import stroom.entity.client.event.DirtyEvent.DirtyHandler;
import stroom.entity.client.event.HasDirtyHandlers;
import stroom.entity.client.presenter.HasRead;
import stroom.entity.client.presenter.HasWrite;
import stroom.query.client.ExpressionTreePresenter;
import stroom.query.shared.ExpressionBuilder;
import stroom.query.shared.ExpressionOperator.Op;
import stroom.query.shared.IndexField;
import stroom.policy.client.presenter.DataReceiptPolicySettingsPresenter.DataReceiptPolicySettingsView;
import stroom.policy.shared.DataReceiptAction;
import stroom.policy.shared.DataReceiptPolicy;
import stroom.policy.shared.DataReceiptRule;
import stroom.svg.client.SvgPresets;
import stroom.widget.button.client.ButtonView;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

import java.util.List;

public class DataReceiptPolicySettingsPresenter extends MyPresenterWidget<DataReceiptPolicySettingsView> implements HasRead<DataReceiptPolicy>, HasWrite<DataReceiptPolicy>, HasDirtyHandlers {
    private final DataReceiptPolicyListPresenter listPresenter;
    private final ExpressionTreePresenter expressionPresenter;
    private final Provider<DataReceiptRulePresenter> editRulePresenterProvider;

    private List<IndexField> indexFields;
    private List<DataReceiptRule> rules;

    private ButtonView addButton;
    private ButtonView editButton;
    private ButtonView copyButton;
    private ButtonView disableButton;
    private ButtonView deleteButton;
    private ButtonView moveUpButton;
    private ButtonView moveDownButton;

    private boolean dirty;

    @Inject
    public DataReceiptPolicySettingsPresenter(final EventBus eventBus,
                                              final DataReceiptPolicySettingsView view,
                                              final DataReceiptPolicyListPresenter listPresenter,
                                              final ExpressionTreePresenter expressionPresenter,
                                              final Provider<DataReceiptRulePresenter> editRulePresenterProvider) {
        super(eventBus, view);
        this.listPresenter = listPresenter;
        this.expressionPresenter = expressionPresenter;
        this.editRulePresenterProvider = editRulePresenterProvider;

        getView().setTableView(listPresenter.getView());
        getView().setExpressionView(expressionPresenter.getView());

        // Stop users from selecting expression items.
        expressionPresenter.setSelectionModel(null);

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
            if (rules != null) {
                add();
            }
        }));
        registerHandler(editButton.addClickHandler(event -> {
            if (rules != null) {
                final DataReceiptRule selected = listPresenter.getSelectionModel().getSelected();
                if (selected != null) {
                    edit(selected);
                }
            }
        }));
        registerHandler(copyButton.addClickHandler(event -> {
            if (rules != null) {
                final DataReceiptRule selected = listPresenter.getSelectionModel().getSelected();
                if (selected != null) {
                    final DataReceiptRule newRule = new DataReceiptRule(selected.getRuleNumber() + 1, System.currentTimeMillis(), selected.getName(), selected.isEnabled(), selected.getExpression(), selected.getAction());

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
        }));
        registerHandler(disableButton.addClickHandler(event -> {
            if (rules != null) {
                final DataReceiptRule selected = listPresenter.getSelectionModel().getSelected();
                if (selected != null) {
                    final DataReceiptRule newRule = new DataReceiptRule(selected.getRuleNumber(), selected.getCreationTime(), selected.getName(), !selected.isEnabled(), selected.getExpression(), selected.getAction());
                    final int index = rules.indexOf(selected);
                    rules.remove(index);
                    rules.add(index, newRule);
                    listPresenter.getSelectionModel().setSelected(newRule);
                    update();
                    setDirty(true);
                }
            }
        }));
        registerHandler(deleteButton.addClickHandler(event -> {
            if (rules != null) {
                ConfirmEvent.fire(this, "Are you sure you want to delete this item?", ok -> {
                    if (ok) {
                        final DataReceiptRule rule = listPresenter.getSelectionModel().getSelected();
                        rules.remove(rule);
                        listPresenter.getSelectionModel().clear();
                        update();
                        setDirty(true);
                    }
                });
            }
        }));
        registerHandler(moveUpButton.addClickHandler(event -> {
            if (rules != null) {
                final DataReceiptRule rule = listPresenter.getSelectionModel().getSelected();
                if (rule != null) {
                    int index = rules.indexOf(rule);
                    if (index > 0) {
                        rules.remove(rule);
                        rules.add(index - 1, rule);
                        update();
                        setDirty(true);
                    }
                }
            }
        }));
        registerHandler(moveDownButton.addClickHandler(event -> {
            if (rules != null) {
                final DataReceiptRule rule = listPresenter.getSelectionModel().getSelected();
                if (rule != null) {
                    int index = rules.indexOf(rule);
                    if (index < rules.size() - 1) {
                        rules.remove(rule);
                        rules.add(index + 1, rule);
                        update();
                        setDirty(true);
                    }
                }
            }
        }));
        registerHandler(listPresenter.getSelectionModel().addSelectionHandler(event -> {
            final DataReceiptRule rule = listPresenter.getSelectionModel().getSelected();
            if (rule != null) {
                expressionPresenter.read(rule.getExpression());
                if (event.getSelectionType().isDoubleSelect()) {
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
        final DataReceiptRule newRule = new DataReceiptRule(0, System.currentTimeMillis(), "", true, new ExpressionBuilder(Op.AND).build(), DataReceiptAction.RECEIVE);
        final DataReceiptRulePresenter editRulePresenter = editRulePresenterProvider.get();
        editRulePresenter.read(newRule, indexFields);

        final PopupSize popupSize = new PopupSize(800, 400, 300, 300, 2000, 2000, true);
        ShowPopupEvent.fire(DataReceiptPolicySettingsPresenter.this, editRulePresenter, PopupType.OK_CANCEL_DIALOG, popupSize, "Add New Rule", new PopupUiHandlers() {
            @Override
            public void onHideRequest(final boolean autoClose, final boolean ok) {
                if (ok) {
                    final DataReceiptRule rule = editRulePresenter.write();
                    rules.add(0, rule);
                    update();
                    listPresenter.getSelectionModel().setSelected(rule);
                    setDirty(true);
                }

                HidePopupEvent.fire(DataReceiptPolicySettingsPresenter.this, editRulePresenter);
            }

            @Override
            public void onHide(final boolean autoClose, final boolean ok) {
                // Do nothing.
            }
        });
    }

    private void edit(final DataReceiptRule existingRule) {
        final DataReceiptRulePresenter editRulePresenter = editRulePresenterProvider.get();
        editRulePresenter.read(existingRule, indexFields);

        final PopupSize popupSize = new PopupSize(800, 400, 300, 300, 2000, 2000, true);
        ShowPopupEvent.fire(DataReceiptPolicySettingsPresenter.this, editRulePresenter, PopupType.OK_CANCEL_DIALOG, popupSize, "Edit Rule", new PopupUiHandlers() {
            @Override
            public void onHideRequest(final boolean autoClose, final boolean ok) {
                if (ok) {
                    final DataReceiptRule rule = editRulePresenter.write();
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

                HidePopupEvent.fire(DataReceiptPolicySettingsPresenter.this, editRulePresenter);
            }

            @Override
            public void onHide(final boolean autoClose, final boolean ok) {
                // Do nothing.
            }
        });
    }

    @Override
    public void read(final DataReceiptPolicy policy) {
        if (policy != null) {
            if (policy.getFields() != null) {
                this.indexFields = policy.getFields().getIndexFields();
            }

            this.rules = policy.getRules();
            listPresenter.getSelectionModel().clear();
            setDirty(false);
            update();
        }
    }

    @Override
    public void write(final DataReceiptPolicy entity) {
    }

    private void update() {
        if (rules != null) {
            // Set rule numbers on all of the rules for display purposes.
            for (int i = 0; i < rules.size(); i++) {
                final DataReceiptRule rule = rules.get(i);
                final DataReceiptRule newRule = new DataReceiptRule(i + 1, rule.getCreationTime(), rule.getName(), rule.isEnabled(), rule.getExpression(), rule.getAction());
                rules.set(i, newRule);
            }
            listPresenter.setData(rules);
        }
        updateButtons();
    }

    private void updateButtons() {
        final boolean loadedPolicy = rules != null;
        final DataReceiptRule selection = listPresenter.getSelectionModel().getSelected();
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

    public interface DataReceiptPolicySettingsView extends View {
        void setTableView(View view);

        void setExpressionView(View view);
    }
}
