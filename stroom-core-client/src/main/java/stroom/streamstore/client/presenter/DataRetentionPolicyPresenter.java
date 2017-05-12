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
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;
import stroom.alert.client.event.ConfirmEvent;
import stroom.content.client.presenter.ContentTabPresenter;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.query.shared.ExpressionOperator;
import stroom.query.shared.ExpressionOperator.Op;
import stroom.query.shared.IndexFieldsMap;
import stroom.streamstore.shared.DataRetentionPolicy;
import stroom.streamstore.shared.DataRetentionRule;
import stroom.streamstore.shared.FetchDataRetentionPolicyAction;
import stroom.streamstore.shared.FetchFieldsAction;
import stroom.streamstore.shared.SaveDataRetentionPolicyAction;
import stroom.streamstore.shared.TimeUnit;
import stroom.widget.button.client.GlyphButtonView;
import stroom.widget.button.client.GlyphIcons;
import stroom.widget.tab.client.presenter.Icon;

public class DataRetentionPolicyPresenter extends ContentTabPresenter<DataRetentionPolicyPresenter.DataRetentionPolicyView> {
    private final DataRetentionPolicyListPresenter listPresenter;
    private final EditExpressionPresenter editExpressionPresenter;
    private final ClientDispatchAsync dispatcher;

    private DataRetentionPolicy policy;
    private IndexFieldsMap indexFieldsMap;

    private GlyphButtonView saveButton;
    private GlyphButtonView addButton;
    private GlyphButtonView deleteButton;
    private GlyphButtonView moveUpButton;
    private GlyphButtonView moveDownButton;

    @Inject
    public DataRetentionPolicyPresenter(final EventBus eventBus,
                                        final DataRetentionPolicyView view,
                                        final DataRetentionPolicyListPresenter listPresenter,
                                        final EditExpressionPresenter editExpressionPresenter,
                                        final ClientDispatchAsync dispatcher) {
        super(eventBus, view);
        this.listPresenter = listPresenter;
        this.editExpressionPresenter = editExpressionPresenter;
        this.dispatcher = dispatcher;

        getView().setTableView(listPresenter.getView());
        getView().setExpressionView(editExpressionPresenter.getView());

        saveButton = listPresenter.add(GlyphIcons.SAVE);
        addButton = listPresenter.add(GlyphIcons.ADD);
        deleteButton = listPresenter.add(GlyphIcons.DELETE);
        moveUpButton = listPresenter.add(GlyphIcons.UP);
        moveDownButton = listPresenter.add(GlyphIcons.DOWN);

        listPresenter.getView().asWidget().getElement().getStyle().setBorderStyle(BorderStyle.NONE);

        enableButtons();

        dispatcher.exec(new FetchDataRetentionPolicyAction()).onSuccess(result -> {
            policy = result;
            update();
        });

        dispatcher.exec(new FetchFieldsAction()).onSuccess(result -> {
            indexFieldsMap = new IndexFieldsMap(result);
            update();
        });
    }

    @Override
    protected void onBind() {
        registerHandler(saveButton.addClickHandler(event -> {
            dispatcher.exec(new SaveDataRetentionPolicyAction(policy)).onSuccess(result -> {
                policy = result;
                update();
            });
        }));
        registerHandler(addButton.addClickHandler(event -> {
            if (policy != null) {
                final DataRetentionRule newPolicy = new DataRetentionRule(new ExpressionOperator(Op.AND), 1, TimeUnit.YEARS, true);
                policy.getRules().add(newPolicy);
                update();
                listPresenter.getSelectionModel().setSelected(newPolicy);
            }
        }));
        registerHandler(deleteButton.addClickHandler(event -> {
            if (policy != null) {
                ConfirmEvent.fire(this, "Are you sure you want to delete this item?", ok -> {
                    if (ok) {
                        final DataRetentionRule rule = listPresenter.getSelectionModel().getSelected();
                        policy.getRules().remove(rule);
                        update();
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
                    }
                }
            }
        }));
        registerHandler(moveDownButton.addClickHandler(event -> {
            if (policy != null) {
                final DataRetentionRule rule = listPresenter.getSelectionModel().getSelected();
                if (rule != null) {
                    int index = policy.getRules().indexOf(rule);
                    if (index < policy.getRules().size() - 2) {
                        policy.getRules().remove(rule);
                        policy.getRules().add(index + 1, rule);
                        update();
                    }
                }
            }
        }));
        registerHandler(listPresenter.getSelectionModel().addSelectionHandler(event -> {
            final DataRetentionRule rule = listPresenter.getSelectionModel().getSelected();
            if (rule != null) {
                editExpressionPresenter.read(rule.getExpression(), indexFieldsMap);
            } else {
                editExpressionPresenter.read(null, indexFieldsMap);
            }
        }));

        super.onBind();
    }

    private void update() {
        if (policy != null) {
            listPresenter.setData(policy.getRules());
        }
        enableButtons();
    }

    private void enableButtons() {
        saveButton.setEnabled(policy != null);
        addButton.setEnabled(policy != null);
        deleteButton.setEnabled(policy != null && listPresenter.getSelectionModel().getSelected() != null);
        moveUpButton.setEnabled(policy != null && listPresenter.getSelectionModel().getSelected() != null);
        moveDownButton.setEnabled(policy != null && listPresenter.getSelectionModel().getSelected() != null);
    }

    @Override
    public Icon getIcon() {
        return GlyphIcons.HISTORY;
    }

    @Override
    public String getLabel() {
        return "Data Retention";
    }

    public interface DataRetentionPolicyView extends View {
        void setTableView(View view);

        void setExpressionView(View view);
    }
}
