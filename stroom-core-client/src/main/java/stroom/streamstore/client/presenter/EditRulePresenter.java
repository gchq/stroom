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
import stroom.streamstore.client.presenter.EditRulePresenter.EditRuleView;
import stroom.streamstore.shared.DataRetentionRule;
import stroom.streamstore.shared.TimeUnit;

public class EditRulePresenter extends MyPresenterWidget<EditRuleView> {
    private final EditExpressionPresenter editExpressionPresenter;
    private boolean enabled;

    @Inject
    public EditRulePresenter(final EventBus eventBus,
                             final EditRuleView view, final EditExpressionPresenter editExpressionPresenter) {
        super(eventBus, view);
        this.editExpressionPresenter = editExpressionPresenter;
        view.setExpressionView(editExpressionPresenter.getView());
    }

    void read(final DataRetentionRule rule) {
        this.enabled = rule.isEnabled();

        if (rule.getExpression() == null) {
            editExpressionPresenter.read(new ExpressionOperator(Op.AND));
        } else {
            editExpressionPresenter.read(rule.getExpression());
        }
        getView().setForever(rule.isForever());
        getView().setAge(rule.getAge());
        getView().setTimeUnit(rule.getTimeUnit());
    }

    DataRetentionRule write() {
        final ExpressionOperator expression = editExpressionPresenter.write();
        return new DataRetentionRule(enabled, expression, getView().getAge(), getView().getTimeUnit(), getView().isForever());
    }

    public interface EditRuleView extends View {
        void setExpressionView(View view);

        void setForever(boolean forever);

        boolean isForever();

        void setAge(int age);

        int getAge();

        void setTimeUnit(TimeUnit timeUnit);

        TimeUnit getTimeUnit();
    }
}
