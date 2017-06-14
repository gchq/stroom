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
import stroom.streamstore.shared.DataRetentionPolicy;
import stroom.streamstore.shared.DataRetentionRule;
import stroom.streamstore.shared.FetchDataRetentionPolicyAction;
import stroom.streamstore.shared.SaveDataRetentionPolicyAction;
import stroom.streamstore.shared.TimeUnit;
import stroom.widget.button.client.ButtonView;
import stroom.widget.button.client.SvgIcons;
import stroom.widget.button.client.SvgIcons;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;
import stroom.widget.tab.client.presenter.Icon;

public class DataRetentionPolicyPresenter extends ContentTabPresenter<DataRetentionPolicyPresenter.DataRetentionPolicyView> implements HasDirtyHandlers {
    private final DataRetentionPolicyListPresenter listPresenter;
    private final ExpressionTreePresenter expressionPresenter;
    private final Provider<DataRetentionRulePresenter> editRulePresenterProvider;
    private final ClientDispatchAsync dispatcher;

    private DataRetentionPolicy policy;

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

        saveButton = listPresenter.add(SvgIcons.SAVE);
        addButton = listPresenter.add(SvgIcons.ADD);
        editButton = listPresenter.add(SvgIcons.EDIT);
        copyButton = listPresenter.add(SvgIcons.COPY);
        disableButton = listPresenter.add(SvgIcons.DISABLE);
        deleteButton = listPresenter.add(SvgIcons.DELETE);
        moveUpButton = listPresenter.add(SvgIcons.UP);
        moveDownButton = listPresenter.add(SvgIcons.DOWN);

        listPresenter.getView().asWidget().getElement().getStyle().setBorderStyle(BorderStyle.NONE);

        updateButtons();

        dispatcher.exec(new FetchDataRetentionPolicyAction()).onSuccess(result -> {
            policy = result;
            update();
        });
    }

    @Override
    protected void onBind() {
        registerHandler(saveButton.addClickHandler(event -> {
            dispatcher.exec(new SaveDataRetentionPolicyAction(policy)).onSuccess(result -> {
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
                final DataRetentionRule selected = listPresenter.getSelectionModel().getSelected();
                if (selected != null) {
                    edit(selected);
                }
            }
        }));
        registerHandler(copyButton.addClickHandler(event -> {
            if (policy != null) {
                final DataRetentionRule selected = listPresenter.getSelectionModel().getSelected();
                if (selected != null) {
                    ExpressionOperator expression = selected.getExpression();
                    if (expression != null) {
                        expression = expression.copy();
                    }

                    final DataRetentionRule newRule = new DataRetentionRule(System.currentTimeMillis(), selected.getName(), selected.isEnabled(), expression, selected.getAge(), selected.getTimeUnit(), selected.isForever());

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
                final DataRetentionRule selected = listPresenter.getSelectionModel().getSelected();
                if (selected != null) {
                    final DataRetentionRule newRule = new DataRetentionRule(selected.getCreationTime(), selected.getName(), !selected.isEnabled(), selected.getExpression(), selected.getAge(), selected.getTimeUnit(), selected.isForever());

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
                        final DataRetentionRule rule = listPresenter.getSelectionModel().getSelected();
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
                final DataRetentionRule rule = listPresenter.getSelectionModel().getSelected();
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
                final DataRetentionRule rule = listPresenter.getSelectionModel().getSelected();
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
            final DataRetentionRule rule = listPresenter.getSelectionModel().getSelected();
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
        final DataRetentionRule newRule = new DataRetentionRule(System.currentTimeMillis(), "", true, new ExpressionOperator(Op.AND), 1, TimeUnit.YEARS, true);
        final DataRetentionRulePresenter editRulePresenter = editRulePresenterProvider.get();
        editRulePresenter.read(newRule);

        final PopupSize popupSize = new PopupSize(800, 400, 300, 300, 2000, 2000, true);
        ShowPopupEvent.fire(DataRetentionPolicyPresenter.this, editRulePresenter, PopupType.OK_CANCEL_DIALOG, popupSize, "Add New Rule", new PopupUiHandlers() {
            @Override
            public void onHideRequest(final boolean autoClose, final boolean ok) {
                if (ok) {
                    final DataRetentionRule rule = editRulePresenter.write();
                    policy.getRules().add(0, rule);
                    update();
                    listPresenter.getSelectionModel().setSelected(rule);
                    setDirty(true);
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

        final PopupSize popupSize = new PopupSize(800, 400, 300, 300, 2000, 2000, true);
        ShowPopupEvent.fire(DataRetentionPolicyPresenter.this, editRulePresenter, PopupType.OK_CANCEL_DIALOG, popupSize, "Edit Rule", new PopupUiHandlers() {
            @Override
            public void onHideRequest(final boolean autoClose, final boolean ok) {
                if (ok) {
                    final DataRetentionRule rule = editRulePresenter.write();
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

                HidePopupEvent.fire(DataRetentionPolicyPresenter.this, editRulePresenter);
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
        final DataRetentionRule selection = listPresenter.getSelectionModel().getSelected();
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
        return SvgIcons.HISTORY;
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
