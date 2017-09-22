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

package stroom.ruleset.client.presenter;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import stroom.datasource.api.v2.DataSourceField;
import stroom.query.api.v2.ExpressionBuilder;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.ruleset.client.presenter.RulePresenter.RuleView;
import stroom.ruleset.shared.DataReceiptAction;
import stroom.ruleset.shared.Rule;

import java.util.List;

public class RulePresenter extends MyPresenterWidget<RuleView> {
    private final EditExpressionPresenter editExpressionPresenter;
    private Rule originalRule;

    @Inject
    public RulePresenter(final EventBus eventBus,
                         final RuleView view,
                         final EditExpressionPresenter editExpressionPresenter) {
        super(eventBus, view);
        this.editExpressionPresenter = editExpressionPresenter;
        view.setExpressionView(editExpressionPresenter.getView());
    }

    void read(final Rule rule, final List<DataSourceField> fields) {
        editExpressionPresenter.init(null, null, fields);
        this.originalRule = rule;
        getView().setName(rule.getName());
        if (rule.getExpression() == null) {
            editExpressionPresenter.read(new ExpressionBuilder(Op.AND).build());
        } else {
            editExpressionPresenter.read(rule.getExpression());
        }
        getView().setAction(rule.getAction());
    }

    Rule write() {
        final ExpressionOperator expression = editExpressionPresenter.write();
        return new Rule(originalRule.getRuleNumber(), originalRule.getCreationTime(), getView().getName(), originalRule.isEnabled(), expression, getView().getAction());
    }

    public interface RuleView extends View {
        void setExpressionView(View view);

        String getName();

        void setName(String name);

        DataReceiptAction getAction();

        void setAction(DataReceiptAction action);
    }
}
