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

package stroom.streamstore.client.presenter;

import com.google.gwt.dom.client.Style.BorderStyle;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.View;
import stroom.alert.client.event.ConfirmEvent;
import stroom.content.client.event.RefreshContentTabEvent;
import stroom.content.client.presenter.ContentTabPresenter;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.entity.client.event.DirtyEvent;
import stroom.entity.client.event.DirtyEvent.DirtyHandler;
import stroom.entity.client.event.HasDirtyHandlers;
import stroom.query.client.ExpressionTreePresenter;
import stroom.query.shared.ExpressionOperator;
import stroom.query.shared.ExpressionOperator.Op;
import stroom.streamstore.shared.DataReceiptAction;
import stroom.streamstore.shared.DataReceiptPolicy;
import stroom.streamstore.shared.DataReceiptRule;
import stroom.streamstore.shared.FetchDataReceiptPolicyAction;
import stroom.streamstore.shared.SaveDataReceiptPolicyAction;
import stroom.widget.button.client.GlyphButtonView;
import stroom.widget.button.client.GlyphIcons;
import stroom.widget.button.client.SVGButtonView;
import stroom.widget.button.client.SVGIcons;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;
import stroom.widget.tab.client.presenter.Icon;

public class DataReceiptPolicyPresenter extends ContentTabPresenter<DataReceiptPolicyPresenter.DataReceiptPolicyView> implements HasDirtyHandlers {
    private final DataReceiptPolicyListPresenter listPresenter;
    private final ExpressionTreePresenter expressionPresenter;
    private final Provider<DataReceiptRulePresenter> editRulePresenterProvider;
    private final ClientDispatchAsync dispatcher;

    private DataReceiptPolicy policy;

    private SVGButtonView saveButton;
    private GlyphButtonView addButton;
    private GlyphButtonView editButton;
    private GlyphButtonView copyButton;
    private GlyphButtonView disableButton;
    private GlyphButtonView deleteButton;
    private GlyphButtonView moveUpButton;
    private GlyphButtonView moveDownButton;

    private boolean dirty;
    private String lastLabel;

    @Inject
    public DataReceiptPolicyPresenter(final EventBus eventBus,
                                      final DataReceiptPolicyView view,
                                      final DataReceiptPolicyListPresenter listPresenter,
                                      final ExpressionTreePresenter expressionPresenter,
                                      final Provider<DataReceiptRulePresenter> editRulePresenterProvider,
                                      final ClientDispatchAsync dispatcher) {
        super(eventBus, view);
        this.listPresenter = listPresenter;
        this.expressionPresenter = expressionPresenter;
        this.editRulePresenterProvider = editRulePresenterProvider;
        this.dispatcher = dispatcher;

        getView().setTableView(listPresenter.getView());
        getView().setExpressionView(expressionPresenter.getView());

        // Stop users from selecting expression items.
        expressionPresenter.setSelectionModel(null);

        saveButton = listPresenter.add(SVGIcons.SAVE);
        addButton = listPresenter.add(GlyphIcons.ADD);
        editButton = listPresenter.add(GlyphIcons.EDIT);
        copyButton = listPresenter.add(GlyphIcons.COPY);
        disableButton = listPresenter.add(GlyphIcons.DISABLE);
        deleteButton = listPresenter.add(GlyphIcons.DELETE);
        moveUpButton = listPresenter.add(GlyphIcons.UP);
        moveDownButton = listPresenter.add(GlyphIcons.DOWN);

        listPresenter.getView().asWidget().getElement().getStyle().setBorderStyle(BorderStyle.NONE);

        updateButtons();

        dispatcher.exec(new FetchDataReceiptPolicyAction()).onSuccess(result -> {
            policy = result;
            update();
        });
    }

    @Override
    protected void onBind() {
        registerHandler(saveButton.addClickHandler(event -> {
            dispatcher.exec(new SaveDataReceiptPolicyAction(policy)).onSuccess(result -> {
                policy = result;
                listPresenter.getSelectionModel().clear();
                update();
                setDirty(false);
            });
        }));
        registerHandler(addButton.addClickHandler(event -> {
            if (policy != null) {
                add();
            }
        }));
        registerHandler(editButton.addClickHandler(event -> {
            if (policy != null) {
                final DataReceiptRule selected = listPresenter.getSelectionModel().getSelected();
                if (selected != null) {
                    edit(selected);
                }
            }
        }));
        registerHandler(copyButton.addClickHandler(event -> {
            if (policy != null) {
                final DataReceiptRule selected = listPresenter.getSelectionModel().getSelected();
                if (selected != null) {
                    ExpressionOperator expression = selected.getExpression();
                    if (expression != null) {
                        expression = expression.copy();
                    }

                    final DataReceiptRule newRule = new DataReceiptRule(System.currentTimeMillis(), selected.getName(), selected.isEnabled(), expression, selected.getAction());

                    final int index = policy.getRules().indexOf(selected);

                    if (index < policy.getRules().size() - 1) {
                        policy.getRules().add(index + 1, newRule);
                    } else {
                        policy.getRules().add(newRule);
                    }

                    update();
                    listPresenter.getSelectionModel().setSelected(newRule);
                }
            }
        }));
        registerHandler(disableButton.addClickHandler(event -> {
            if (policy != null) {
                final DataReceiptRule selected = listPresenter.getSelectionModel().getSelected();
                if (selected != null) {
                    final DataReceiptRule newRule = new DataReceiptRule(selected.getCreationTime(), selected.getName(), !selected.isEnabled(), selected.getExpression(), selected.getAction());

                    final int index = policy.getRules().indexOf(selected);
                    policy.getRules().remove(index);
                    policy.getRules().add(index, newRule);
                    listPresenter.getSelectionModel().setSelected(newRule);
                    update();
                    setDirty(true);
                }
            }
        }));
        registerHandler(deleteButton.addClickHandler(event -> {
            if (policy != null) {
                ConfirmEvent.fire(this, "Are you sure you want to delete this item?", ok -> {
                    if (ok) {
                        final DataReceiptRule rule = listPresenter.getSelectionModel().getSelected();
                        policy.getRules().remove(rule);
                        listPresenter.getSelectionModel().clear();
                        update();
                        setDirty(true);
                    }
                });
            }
        }));
        registerHandler(moveUpButton.addClickHandler(event -> {
            if (policy != null) {
                final DataReceiptRule rule = listPresenter.getSelectionModel().getSelected();
                if (rule != null) {
                    int index = policy.getRules().indexOf(rule);
                    if (index > 0) {
                        policy.getRules().remove(rule);
                        policy.getRules().add(index - 1, rule);
                        update();
                        setDirty(true);
                    }
                }
            }
        }));
        registerHandler(moveDownButton.addClickHandler(event -> {
            if (policy != null) {
                final DataReceiptRule rule = listPresenter.getSelectionModel().getSelected();
                if (rule != null) {
                    int index = policy.getRules().indexOf(rule);
                    if (index < policy.getRules().size() - 1) {
                        policy.getRules().remove(rule);
                        policy.getRules().add(index + 1, rule);
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
        final DataReceiptRule newRule = new DataReceiptRule(System.currentTimeMillis(), "", true, new ExpressionOperator(Op.AND), DataReceiptAction.RECEIVE);
        final DataReceiptRulePresenter editRulePresenter = editRulePresenterProvider.get();
        editRulePresenter.read(newRule);

        final PopupSize popupSize = new PopupSize(800, 400, 300, 300, 2000, 2000, true);
        ShowPopupEvent.fire(DataReceiptPolicyPresenter.this, editRulePresenter, PopupType.OK_CANCEL_DIALOG, popupSize, "Add New Rule", new PopupUiHandlers() {
            @Override
            public void onHideRequest(final boolean autoClose, final boolean ok) {
                if (ok) {
                    final DataReceiptRule rule = editRulePresenter.write();
                    policy.getRules().add(0, rule);
                    update();
                    listPresenter.getSelectionModel().setSelected(rule);
                    setDirty(true);
                }

                HidePopupEvent.fire(DataReceiptPolicyPresenter.this, editRulePresenter);
            }

            @Override
            public void onHide(final boolean autoClose, final boolean ok) {
                // Do nothing.
            }
        });
    }

    private void edit(final DataReceiptRule existingRule) {
        final DataReceiptRulePresenter editRulePresenter = editRulePresenterProvider.get();
        editRulePresenter.read(existingRule);

        final PopupSize popupSize = new PopupSize(800, 400, 300, 300, 2000, 2000, true);
        ShowPopupEvent.fire(DataReceiptPolicyPresenter.this, editRulePresenter, PopupType.OK_CANCEL_DIALOG, popupSize, "Edit Rule", new PopupUiHandlers() {
            @Override
            public void onHideRequest(final boolean autoClose, final boolean ok) {
                if (ok) {
                    final DataReceiptRule rule = editRulePresenter.write();
                    final int index = policy.getRules().indexOf(existingRule);
                    policy.getRules().remove(index);
                    policy.getRules().add(index, rule);

                    update();
                    listPresenter.getSelectionModel().setSelected(rule);

                    // Only mark the policies as dirty if the rule was actually changed.
                    if (!existingRule.equals(rule)) {
                        setDirty(true);
                    }
                }

                HidePopupEvent.fire(DataReceiptPolicyPresenter.this, editRulePresenter);
            }

            @Override
            public void onHide(final boolean autoClose, final boolean ok) {
                // Do nothing.
            }
        });
    }

    private void update() {
        if (policy != null) {
            // Set rule numbers on all of the rules for display purposes.
            for (int i = 0; i < policy.getRules().size(); i++) {
                policy.getRules().get(i).setRuleNumber(i + 1);
            }
            listPresenter.setData(policy.getRules());
        }
        updateButtons();
    }

    private void updateButtons() {
        final boolean loadedPolicy = policy != null;
        final DataReceiptRule selection = listPresenter.getSelectionModel().getSelected();
        final boolean selected = loadedPolicy && selection != null;
        int index = -1;
        if (selected) {
            index = policy.getRules().indexOf(selection);
        }

        if (selection != null && selection.isEnabled()) {
            disableButton.setTitle("Disable");
        } else {
            disableButton.setTitle("Enable");
        }

        saveButton.setEnabled(loadedPolicy && dirty);
        addButton.setEnabled(loadedPolicy);
        editButton.setEnabled(selected);
        copyButton.setEnabled(selected);
        disableButton.setEnabled(selected);
        deleteButton.setEnabled(selected);
        moveUpButton.setEnabled(selected && index > 0);
        moveDownButton.setEnabled(selected && index >= 0 && index < policy.getRules().size() - 1);
    }

    @Override
    public Icon getIcon() {
        return GlyphIcons.HISTORY;
    }

    @Override
    public String getLabel() {
        if (isDirty()) {
            return "* " + "Data Receipt";
        }

        return "Data Receipt";
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

    public interface DataReceiptPolicyView extends View {
        void setTableView(View view);

        void setExpressionView(View view);
    }
}
