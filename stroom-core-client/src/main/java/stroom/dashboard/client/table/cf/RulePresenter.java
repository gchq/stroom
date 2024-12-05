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
import stroom.query.api.v2.ConditionalFormattingType;
import stroom.query.api.v2.CustomConditionalFormattingStyle;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.client.presenter.FieldSelectionListModel;
import stroom.util.shared.RandomId;

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

public class RulePresenter
        extends MyPresenterWidget<RulePresenter.RuleView>
        implements RuleUiHandlers, Focus {

    private final EditExpressionPresenter editExpressionPresenter;
    private final Provider<CustomRowStylePresenter> customRowStylePresenterProvider;
    private CustomConditionalFormattingStyle customConditionalFormattingStyle;
    private ConditionalFormattingRule originalRule;

    @Inject
    public RulePresenter(final EventBus eventBus,
                         final RuleView view,
                         final EditExpressionPresenter editExpressionPresenter,
                         final Provider<CustomRowStylePresenter> customRowStylePresenterProvider) {
        super(eventBus, view);
        this.editExpressionPresenter = editExpressionPresenter;
        this.customRowStylePresenterProvider = customRowStylePresenterProvider;
        view.setExpressionView(editExpressionPresenter.getView());
        view.setUiHandlers(this);
    }

    @Override
    public void onEditCustomStyle() {
        final CustomRowStylePresenter customRowStylePresenter = customRowStylePresenterProvider.get();
        customRowStylePresenter.read(customConditionalFormattingStyle);
        customRowStylePresenter.show(e -> {
            if (e.isOk()) {
                customConditionalFormattingStyle = customRowStylePresenter.write();
            }
            e.hide();
        });
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
        getView().setFormattingType(rule.getFormattingType() == null
                ? ConditionalFormattingType.CUSTOM
                : rule.getFormattingType());
        getView().setFormattingStyle(rule.getFormattingStyle());
        getView().setEnabled(rule.isEnabled());
        customConditionalFormattingStyle = rule.getCustomStyle();
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
                .formattingType(getView().getFormattingType())
                .formattingStyle(getView().getFormattingStyle())
                .customStyle(customConditionalFormattingStyle)
                .enabled(getView().isEnabled())
                .build();
    }

    @Override
    public void focus() {
        editExpressionPresenter.focus();
    }

    public interface RuleView extends View, HasUiHandlers<RuleUiHandlers> {

        void setExpressionView(View view);

        boolean isHide();

        void setHide(boolean hide);

        void setFormattingType(ConditionalFormattingType type);

        ConditionalFormattingType getFormattingType();

        void setFormattingStyle(ConditionalFormattingStyle styleName);

        ConditionalFormattingStyle getFormattingStyle();

        boolean isEnabled();

        void setEnabled(boolean enabled);
    }
}
