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

package stroom.dashboard.client.table.cf;

import stroom.query.api.ConditionalFormattingRule;
import stroom.query.api.ConditionalFormattingStyle;
import stroom.query.api.ConditionalFormattingType;
import stroom.query.api.CustomConditionalFormattingStyle;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.TextAttributes;
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
        final TextAttributes textAttributes = writeTextAttributes();
        customRowStylePresenter.read(customConditionalFormattingStyle, textAttributes);
        customRowStylePresenter.show(e -> {
            if (e.isOk()) {
                customConditionalFormattingStyle = customRowStylePresenter.write();
                getView().setCustomConditionalFormattingStyle(customConditionalFormattingStyle);
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
        getView().setCustomConditionalFormattingStyle(customConditionalFormattingStyle);

        final TextAttributes textAttributes = rule.getTextAttributes();
        if (textAttributes != null) {
            getView().setTextBold(textAttributes.isBold());
            getView().setTextItalic(textAttributes.isItalic());
        }
    }

    ConditionalFormattingRule write() {
        final String id;
        if (originalRule != null && originalRule.getId() != null) {
            id = originalRule.getId();
        } else {
            id = RandomId.createId(5);
        }

        final TextAttributes textAttributes = writeTextAttributes();
        final ExpressionOperator expression = editExpressionPresenter.write();
        return ConditionalFormattingRule
                .builder()
                .id(id)
                .expression(expression)
                .hide(getView().isHide())
                .formattingType(getView().getFormattingType())
                .formattingStyle(getView().getFormattingStyle())
                .customStyle(customConditionalFormattingStyle)
                .textAttributes(textAttributes)
                .enabled(getView().isEnabled())
                .build();
    }

    private TextAttributes writeTextAttributes() {
        TextAttributes textAttributes = null;
        if (getView().isTextBold() || getView().isTextItalic()) {
            textAttributes = TextAttributes
                    .builder()
                    .bold(getView().isTextBold())
                    .italic(getView().isTextItalic())
                    .build();
        }
        return textAttributes;
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

        boolean isTextBold();

        void setTextBold(boolean bold);

        boolean isTextItalic();

        void setTextItalic(boolean italic);

        boolean isEnabled();

        void setEnabled(boolean enabled);

        void setCustomConditionalFormattingStyle(CustomConditionalFormattingStyle customConditionalFormattingStyle);
    }
}
