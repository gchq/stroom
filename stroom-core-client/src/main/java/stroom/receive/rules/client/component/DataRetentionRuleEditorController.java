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
 */

package stroom.receive.rules.client.component;

import stroom.data.retention.shared.DataRetentionRule;
import stroom.meta.shared.MetaFields;
import stroom.query.api.ExpressionOperator;
import stroom.query.client.component.ExpressionEditorController;
import stroom.query.client.model.DynamicFieldSelectionListModel;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.PopupSize;
import stroom.widget.popup.client.PopupType;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;

/// Adds or edits a data retention rule in a dialog ([DataRetentionRuleEditor]), made for one use.
public class DataRetentionRuleEditorController extends Controller {

    private final DataRetentionRuleEditor view;
    private final ExpressionEditorController expressionEditor;
    private DataRetentionRule originalRule;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus               The application's event bus.
    /// @param view                   The content.
    /// @param expressionEditor       Edits the expression choosing the rule's data.
    /// @param fieldSelectionBoxModel The stream meta fields the expression may use.
    @Inject
    public DataRetentionRuleEditorController(final EventBus eventBus,
                                             final DataRetentionRuleEditor view,
                                             final ExpressionEditorController expressionEditor,
                                             final DynamicFieldSelectionListModel fieldSelectionBoxModel) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        this.expressionEditor = own(expressionEditor).attach(view.getExpressionEditor());
        // Shows its requests' progress as this controller's context does, once it is shown
        fieldSelectionBoxModel.setTaskMonitorFactory(() -> taskMonitor().createTaskMonitor());

        fieldSelectionBoxModel.setDataSourceRefConsumer(consumer -> consumer.accept(MetaFields.STREAM_STORE_DOC_REF));
        expressionEditor.init(MetaFields.STREAM_STORE_DOC_REF, fieldSelectionBoxModel);
    }

    /// Shows a rule as a dialog, owned by `opener` until it is closed.
    ///
    /// @param opener  What shows it.
    /// @param caption The dialog's caption, e.g. `Add New Rule`.
    /// @param rule    The rule to edit, or a new one.
    /// @param onOk    Given the rule as edited when OK is pressed.
    public void edit(final HasControllerNode opener,
                     final String caption,
                     final DataRetentionRule rule,
                     final Consumer<DataRetentionRule> onOk) {
        read(rule);
        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.resizable(800, 600))
                .caption(caption)
                .onShow(e -> view.focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        onOk.accept(write());
                    }
                    e.hide();
                })
                .fire();
    }

    private void read(final DataRetentionRule rule) {
        this.originalRule = rule;
        view.setName(rule.getName());
        if (rule.getExpression() == null) {
            expressionEditor.read(ExpressionOperator.builder().build());
        } else {
            expressionEditor.read(rule.getExpression());
        }
        view.setForever(rule.isForever());
        view.setAge(rule.getAge());
        view.setTimeUnit(rule.getTimeUnit());
    }

    private DataRetentionRule write() {
        final ExpressionOperator expression = expressionEditor.write();
        return new DataRetentionRule(
                originalRule.getRuleNumber(),
                originalRule.getCreationTime(),
                view.getName(),
                originalRule.isEnabled(),
                expression,
                view.getAge(),
                view.getTimeUnit(),
                view.isForever());
    }

}
