/*
 * Copyright 2020 Crown Copyright
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

package stroom.dashboard.client.component;

import stroom.query.api.ConditionalFormattingRule;
import stroom.query.api.ConditionalFormattingType;
import stroom.query.api.CustomConditionalFormattingStyle;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.TextAttributes;
import stroom.query.client.component.ExpressionEditorController;
import stroom.query.client.model.FieldSelectionListModel;
import stroom.util.shared.RandomId;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.PopupSize;
import stroom.widget.popup.client.PopupType;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;

/// Adds or edits a table's conditional formatting rule in a dialog
/// ([ConditionalFormattingRuleEditor]), made for one use.
public class ConditionalFormattingRuleEditorController extends Controller {

    private final ConditionalFormattingRuleEditor view;
    private final ExpressionEditorController expressionEditor;
    private final Provider<CustomRowStyleEditorController> customRowStyleProvider;
    private CustomConditionalFormattingStyle customConditionalFormattingStyle;
    private ConditionalFormattingRule originalRule;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus               The application's event bus.
    /// @param view                   The content.
    /// @param expressionEditor       Edits the expression choosing the rows.
    /// @param customRowStyleProvider Makes the dialog that sets the user's own style.
    @Inject
    public ConditionalFormattingRuleEditorController(
            final EventBus eventBus,
            final ConditionalFormattingRuleEditor view,
            final ExpressionEditorController expressionEditor,
            final Provider<CustomRowStyleEditorController> customRowStyleProvider) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        this.expressionEditor = own(expressionEditor).attach(view.getExpressionEditor());
        this.customRowStyleProvider = customRowStyleProvider;
        // The rule's form frames the expression's editor itself
        expressionEditor.setFramed(false);
        view.setOnEditCustomStyle(this::onEditCustomStyle);
    }

    /// Shows a rule as a dialog, owned by `opener` until it is closed.
    ///
    /// @param opener   What shows it.
    /// @param caption  The dialog's caption, e.g. `Add New Rule`.
    /// @param rule     The rule to edit, or a new one.
    /// @param fields   The fields the expression may use.
    /// @param onOk     Given the rule as edited when OK is pressed.
    /// @param onHidden Called once the dialog is closed, by OK or Cancel.
    public void edit(final HasControllerNode opener,
                     final String caption,
                     final ConditionalFormattingRule rule,
                     final FieldSelectionListModel fields,
                     final Consumer<ConditionalFormattingRule> onOk,
                     final Runnable onHidden) {
        read(rule, fields);
        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.resizable(1000, 700))
                .caption(caption)
                .onShow(e -> expressionEditor.focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        onOk.accept(write());
                    }
                    e.hide();
                    onHidden.run();
                })
                .fire();
    }

    private void onEditCustomStyle() {
        customRowStyleProvider.get().edit(this,
                customConditionalFormattingStyle,
                writeTextAttributes(),
                style -> {
                    customConditionalFormattingStyle = style;
                    view.setCustomConditionalFormattingStyle(customConditionalFormattingStyle);
                });
    }

    private void read(final ConditionalFormattingRule rule,
                      final FieldSelectionListModel fieldSelectionListModel) {
        this.originalRule = rule;
        expressionEditor.init(null, fieldSelectionListModel);
        if (rule.getExpression() == null) {
            expressionEditor.read(ExpressionOperator.builder().build());
        } else {
            expressionEditor.read(rule.getExpression());
        }
        view.setHide(rule.isHide());
        view.setFormattingType(rule.getFormattingType() == null
                ? ConditionalFormattingType.CUSTOM
                : rule.getFormattingType());
        view.setFormattingStyle(rule.getFormattingStyle());
        view.setEnabled(rule.isEnabled());
        customConditionalFormattingStyle = rule.getCustomStyle();
        view.setCustomConditionalFormattingStyle(customConditionalFormattingStyle);

        final TextAttributes textAttributes = rule.getTextAttributes();
        if (textAttributes != null) {
            view.setTextBold(textAttributes.isBold());
            view.setTextItalic(textAttributes.isItalic());
        }
    }

    private ConditionalFormattingRule write() {
        final String id;
        if (originalRule != null && originalRule.getId() != null) {
            id = originalRule.getId();
        } else {
            id = RandomId.createId(5);
        }

        final TextAttributes textAttributes = writeTextAttributes();
        final ExpressionOperator expression = expressionEditor.write();
        return ConditionalFormattingRule
                .builder()
                .id(id)
                .expression(expression)
                .hide(view.isHide())
                .formattingType(view.getFormattingType())
                .formattingStyle(view.getFormattingStyle())
                .customStyle(customConditionalFormattingStyle)
                .textAttributes(textAttributes)
                .enabled(view.isEnabled())
                .build();
    }

    private TextAttributes writeTextAttributes() {
        TextAttributes textAttributes = null;
        if (view.isTextBold() || view.isTextItalic()) {
            textAttributes = TextAttributes
                    .builder()
                    .bold(view.isTextBold())
                    .italic(view.isTextItalic())
                    .build();
        }
        return textAttributes;
    }
}
