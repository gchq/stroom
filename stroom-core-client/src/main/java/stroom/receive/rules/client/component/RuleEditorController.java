/*
 * Copyright 2026 Crown Copyright
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

package stroom.receive.rules.client.component;

import stroom.alert.client.event.ConfirmEvent;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionUtil;
import stroom.query.api.datasource.ConditionSet;
import stroom.query.client.FieldSelectionListModel;
import stroom.query.client.component.ExpressionEditorController;
import stroom.receive.rules.shared.ReceiveAction;
import stroom.receive.rules.shared.ReceiveDataRule;
import stroom.ui.config.client.UiConfigCache;
import stroom.util.shared.NullSafe;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.PopupSize;
import stroom.widget.popup.client.PopupType;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.SafeHtmlUtil;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/// Adds or edits a receipt rule in a dialog ([RuleEditor]). OK warns if the expression uses a
/// field Stroom-Proxy obfuscates with a condition that can't be obfuscated, and keeps the rule only
/// if the user goes on.
public class RuleEditorController extends Controller {

    private final RuleEditor view;
    private final ExpressionEditorController expressionEditor;
    private final UiConfigCache uiConfigCache;
    private ReceiveDataRule originalRule;

    @Inject
    public RuleEditorController(final EventBus eventBus,
                                final RuleEditor view,
                                final ExpressionEditorController expressionEditor,
                                final UiConfigCache uiConfigCache) {
        super(eventBus);
        this.view = view;
        this.expressionEditor = own(expressionEditor).attach(view.getExpressionEditor());
        this.uiConfigCache = uiConfigCache;
        initWidget(view);
        view.setActions(List.of(ReceiveAction.RECEIVE, ReceiveAction.REJECT, ReceiveAction.DROP));
    }

    /// Shows the editor as a dialog, owned by `opener` until it is closed.
    ///
    /// @param opener What shows it.
    /// @param rule   The rule to edit, or a new one.
    /// @param fields The fields the expression may use.
    /// @param onOk   What to do with the rule (a copy of `rule`, as edited) when OK is pressed and,
    ///               if the expression can't be obfuscated, the user goes on.
    public void edit(final HasControllerNode opener,
                     final ReceiveDataRule rule,
                     final FieldSelectionListModel fields,
                     final Consumer<ReceiveDataRule> onOk) {
        read(rule, fields);
        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.resizable(1_000, 600))
                .caption("Edit Rule")
                .onShow(e -> expressionEditor.focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        final ReceiveDataRule edited = write();
                        uiConfigCache.get(extendedUiConfig -> confirmObfuscation(
                                edited,
                                extendedUiConfig.getObfuscatedFields(),
                                confirmed -> {
                                    if (confirmed) {
                                        onOk.accept(edited);
                                        e.hide();
                                    } else {
                                        e.reset();
                                    }
                                }));
                    } else {
                        e.hide();
                    }
                })
                .fire();
    }

    // The rule as edited, a copy of the one shown
    private ReceiveDataRule write() {
        return originalRule.copy()
                .withName(view.getName())
                .withExpression(expressionEditor.write())
                .withAction(view.getAction())
                .build();
    }

    private void read(final ReceiveDataRule rule, final FieldSelectionListModel fields) {
        expressionEditor.init(null, fields);
        this.originalRule = rule;
        view.setName(rule.getName());
        if (rule.getExpression() == null) {
            expressionEditor.read(ExpressionOperator.builder().build());
        } else {
            expressionEditor.read(rule.getExpression());
        }
        view.setAction(rule.getAction());
    }

    // Goes on at once unless the rule has terms on obfuscated fields with conditions that can't be
    // obfuscated, when the user is asked
    private void confirmObfuscation(final ReceiveDataRule rule,
                                    final Set<String> obfuscatedFields,
                                    final Consumer<Boolean> onResult) {
        final List<ExpressionTerm> unobfuscatable = getUnobfuscatableTerms(rule.getExpression(), obfuscatedFields);
        if (unobfuscatable.isEmpty()) {
            onResult.accept(true);
        } else {
            final HtmlBuilder htmlBuilder = HtmlBuilder.builder()
                    .para("The following terms have obfuscated fields and conditions " +
                          "that do not support obfuscation:");
            for (final ExpressionTerm term : unobfuscatable) {
                htmlBuilder.para(termBuilder -> termBuilder.code(codeBuilder -> codeBuilder
                                .append(term.getField()))
                        .append(SafeHtmlUtil.ENSP)
                        .append(term.getCondition().getDisplayValue())
                        .append(SafeHtmlUtil.ENSP)
                        .code(codeBuilder -> codeBuilder.append(term.getValue())));
            }
            ConfirmEvent.fireWarn(
                    this,
                    SafeHtmlUtil.toParagraphs(
                            "This rule contains conditions that are not compatible with obfuscated fields. " +
                            "Values in the effected terms will not be obfuscated on Stroom-Proxy.\n" +
                            "Do you wish to continue?"),
                    htmlBuilder.toSafeHtml(),
                    onResult::accept);
        }
    }

    private static List<ExpressionTerm> getUnobfuscatableTerms(final ExpressionOperator expression,
                                                               final Set<String> obfuscatedFields) {
        final List<ExpressionTerm> terms = new ArrayList<>();
        if (expression != null
            && NullSafe.stream(ExpressionUtil.fields(expression)).anyMatch(obfuscatedFields::contains)) {
            ExpressionUtil.walkExpressionTree(expression, expressionItem -> {
                if (expressionItem instanceof final ExpressionTerm term
                    && obfuscatedFields.contains(term.getField())
                    && term.getCondition() != null
                    && !ConditionSet.OBFUSCATABLE_CONDITIONS.supportsCondition(term.getCondition())) {
                    terms.add(term);
                }
                return true;
            });
        }
        return terms;
    }

}
