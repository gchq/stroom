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

import stroom.data.client.presenter.EditExpressionPresenter;
import stroom.query.api.ExpressionOperator;
import stroom.query.client.presenter.FieldSelectionListModel;
import stroom.receive.rules.client.presenter.RulePresenter.RuleView;
import stroom.receive.rules.shared.ReceiveAction;
import stroom.receive.rules.shared.ReceiveDataRule;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

public class RulePresenter extends MyPresenterWidget<RuleView> {

    private final EditExpressionPresenter editExpressionPresenter;
    private ReceiveDataRule originalRule;

    @Inject
    public RulePresenter(final EventBus eventBus,
                         final RuleView view,
                         final EditExpressionPresenter editExpressionPresenter) {
        super(eventBus, view);
        this.editExpressionPresenter = editExpressionPresenter;
        view.setExpressionView(editExpressionPresenter.getView());
    }

    void read(final ReceiveDataRule rule,
              final FieldSelectionListModel fieldSelectionListModel) {
        editExpressionPresenter.init(null, null, fieldSelectionListModel);
        this.originalRule = rule;
        getView().setName(rule.getName());
        if (rule.getExpression() == null) {
            editExpressionPresenter.read(ExpressionOperator.builder().build());
        } else {
            editExpressionPresenter.read(rule.getExpression());
        }
        getView().setAction(rule.getAction());
    }

    ReceiveDataRule write() {
        final ExpressionOperator expression = editExpressionPresenter.write();
        return new ReceiveDataRule(originalRule.getRuleNumber(),
                originalRule.getCreationTime(),
                getView().getName(),
                originalRule.isEnabled(),
                expression,
                getView().getAction());
    }


    // --------------------------------------------------------------------------------


    public interface RuleView extends View {

        void setExpressionView(View view);

        String getName();

        void setName(String name);

        ReceiveAction getAction();

        void setAction(ReceiveAction action);
    }
}
