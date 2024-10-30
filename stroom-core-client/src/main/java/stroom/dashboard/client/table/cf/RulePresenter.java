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

import stroom.query.api.v2.ConditionalFormattingRule;
import stroom.query.api.v2.ConditionalFormattingStyle;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.client.presenter.FieldSelectionListModel;
import stroom.util.shared.RandomId;

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

public class RulePresenter extends MyPresenterWidget<RulePresenter.RuleView> implements Focus {

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

    void read(final ConditionalFormattingRule rule,
              final FieldSelectionListModel fieldSelectionListModel) {
        this.originalRule = rule;
        editExpressionPresenter.init(null, null, fieldSelectionListModel);
        this.originalRule = rule;
        if (rule.getExpression() == null) {
            editExpressionPresenter.read(ExpressionOperator.builder().build());
        } else {
            editExpressionPresenter.read(rule.getExpression());
        }
        getView().setHide(rule.isHide());
        getView().setCustomStyle(rule.isCustomStyle());
        getView().setStyle(rule.getStyle());
        getView().setBackgroundColor(rule.getBackgroundColor());
        getView().setTextColor(rule.getTextColor());
        getView().setEnabled(rule.isEnabled());
    }

    ConditionalFormattingRule write() {
        final String id;
        if (originalRule != null && originalRule.getId() != null) {
            id = originalRule.getId();
        } else {
            id = RandomId.createId(5);
        }

        final ExpressionOperator expression = editExpressionPresenter.write();
        return ConditionalFormattingRule
                .builder()
                .id(id)
                .expression(expression)
                .hide(getView().isHide())
                .customStyle(getView().isCustomStyle())
                .style(getView().getStyle())
                .backgroundColor(getView().getBackgroundColor())
                .textColor(getView().getTextColor())
                .enabled(getView().isEnabled())
                .build();
    }

    @Override
    public void focus() {
        editExpressionPresenter.focus();
    }


    // --------------------------------------------------------------------------------


    public interface RuleView extends View {

        void setExpressionView(View view);

        boolean isHide();

        void setHide(boolean hide);

        void setCustomStyle(boolean customStyle);

        boolean isCustomStyle();

        void setStyle(ConditionalFormattingStyle styleName);

        ConditionalFormattingStyle getStyle();

        String getBackgroundColor();

        void setBackgroundColor(String backgroundColor);

        String getTextColor();

        void setTextColor(String textColor);

        boolean isEnabled();

        void setEnabled(boolean enabled);
    }
}
