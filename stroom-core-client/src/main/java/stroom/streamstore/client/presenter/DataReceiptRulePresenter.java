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

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import stroom.query.shared.ExpressionOperator;
import stroom.query.shared.ExpressionOperator.Op;
import stroom.streamstore.client.presenter.DataReceiptRulePresenter.DataReceiptRuleView;
import stroom.streamstore.shared.DataReceiptAction;
import stroom.streamstore.shared.DataReceiptRule;

public class DataReceiptRulePresenter extends MyPresenterWidget<DataReceiptRuleView> {
    private final EditExpressionPresenter editExpressionPresenter;
    private long creationTime;
    private boolean enabled;

    @Inject
    public DataReceiptRulePresenter(final EventBus eventBus,
                                    final DataReceiptRuleView view, final EditExpressionPresenter editExpressionPresenter) {
        super(eventBus, view);
        this.editExpressionPresenter = editExpressionPresenter;
        view.setExpressionView(editExpressionPresenter.getView());
    }

    void read(final DataReceiptRule rule) {
        this.creationTime = rule.getCreationTime();
        this.enabled = rule.isEnabled();

        if (rule.getExpression() == null) {
            editExpressionPresenter.read(new ExpressionOperator(Op.AND));
        } else {
            editExpressionPresenter.read(rule.getExpression());
        }
        getView().setAction(rule.getAction());
    }

    DataReceiptRule write() {
        final ExpressionOperator expression = editExpressionPresenter.write();
        return new DataReceiptRule(creationTime, getView().getName(), enabled, expression, getView().getAction());
    }

    public interface DataReceiptRuleView extends View {
        void setExpressionView(View view);

        String getName();

        void setName(String name);

        DataReceiptAction getAction();

        void setAction(DataReceiptAction action);
    }
}
