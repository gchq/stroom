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

package stroom.data.client.component;

import stroom.docref.DocRef;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.datasource.QueryField;
import stroom.query.client.SimpleFieldSelectionListModel;
import stroom.query.client.component.ExpressionEditorController;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.PopupSize;
import stroom.widget.popup.client.PopupType;
import stroom.widget.popup.client.event.HidePopupRequestEvent;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;

/// A filter dialog ([FilterExpression]): an expression of a data source's fields, edited in a
/// dialog made for one use.
public class FilterExpressionController extends Controller {

    private final ExpressionEditorController expressionEditor;

    /// Made by GIN, with its content and the expression editor it shows.
    ///
    /// @param eventBus         The application's event bus.
    /// @param view             The content.
    /// @param expressionEditor The expression editor.
    @Inject
    public FilterExpressionController(final EventBus eventBus,
                                      final FilterExpression view,
                                      final ExpressionEditorController expressionEditor) {
        super(eventBus);
        initWidget(view);
        this.expressionEditor = own(expressionEditor).attach(view.getExpressionEditor());
    }

    /// Edits an expression in a dialog, owned by `opener` until it is closed. Cancel closes it;
    /// on OK the expression is given to `onOk`, which closes the dialog with `e.hide()` or keeps
    /// it open with `e.reset()`.
    ///
    /// @param opener     What shows it.
    /// @param caption    The dialog's caption, e.g. "Filter Streams".
    /// @param popupSize  The dialog's size.
    /// @param expression The expression to start from, or null for an empty one.
    /// @param dataSource The data source whose fields the expression uses.
    /// @param fields     The fields the expression can use.
    /// @param onOk       Given the expression entered and the dialog's hide request.
    public void show(final HasControllerNode opener,
                     final String caption,
                     final PopupSize popupSize,
                     final ExpressionOperator expression,
                     final DocRef dataSource,
                     final List<QueryField> fields,
                     final BiConsumer<ExpressionOperator, HidePopupRequestEvent> onOk) {
        final SimpleFieldSelectionListModel fieldSelectionBoxModel = new SimpleFieldSelectionListModel();
        fieldSelectionBoxModel.addItems(fields);
        expressionEditor.init(dataSource, fieldSelectionBoxModel);
        expressionEditor.read(Objects.requireNonNullElseGet(expression,
                () -> ExpressionOperator.builder().build()));

        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(popupSize)
                .caption(caption)
                .onShow(e -> expressionEditor.focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        onOk.accept(expressionEditor.write(), e);
                    } else {
                        e.hide();
                    }
                })
                .fire();
    }
}
