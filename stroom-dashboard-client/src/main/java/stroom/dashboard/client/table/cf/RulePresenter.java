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

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import stroom.dashboard.shared.ConditionalFormattingRule;
import stroom.datasource.api.v2.DataSourceField;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;

import java.util.List;

public class RulePresenter extends MyPresenterWidget<RulePresenter.RuleView> {
    private final EditExpressionPresenter editExpressionPresenter;
    private ConditionalFormattingRule originalRule;

    @Inject
    public RulePresenter(final EventBus eventBus,
                         final RuleView view,
                         final EditExpressionPresenter editExpressionPresenter) {
        super(eventBus, view);
        this.editExpressionPresenter = editExpressionPresenter;
        view.setExpressionView(editExpressionPresenter.getView());
    }

    void read(final ConditionalFormattingRule rule, final List<DataSourceField> fields) {
        this.originalRule = rule;
        editExpressionPresenter.init(null, null, fields);
        this.originalRule = rule;
        if (rule.getExpression() == null) {
            editExpressionPresenter.read(new ExpressionOperator.Builder(Op.AND).build());
        } else {
            editExpressionPresenter.read(rule.getExpression());
        }
        getView().setHide(rule.isHide());
        getView().setBackgroundColor(rule.getBackgroundColor());
        getView().setTextColor(rule.getTextColor());
        getView().setEnabled(rule.isEnabled());
    }

    ConditionalFormattingRule write() {
        String id = null;
        if (originalRule != null) {
            id = originalRule.getId();
        }

        final ExpressionOperator expression = editExpressionPresenter.write();
        return new ConditionalFormattingRule.Builder()
                .id(id)
                .expression(expression)
                .hide(getView().isHide())
                .backgroundColor(getView().getBackgroundColor())
                .textColor(getView().getTextColor())
                .enabled(getView().isEnabled())
                .build();
    }

    public interface RuleView extends View {
        void setExpressionView(View view);

        boolean isHide();

        void setHide(boolean hide);

        String getBackgroundColor();

        void setBackgroundColor(String backgroundColor);

        String getTextColor();

        void setTextColor(String textColor);

        boolean isEnabled();

        void setEnabled(boolean enabled);
    }
}
