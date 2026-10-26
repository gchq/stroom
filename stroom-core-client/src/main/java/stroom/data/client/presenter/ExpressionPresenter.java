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

package stroom.data.client.presenter;

import stroom.docref.DocRef;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.datasource.QueryField;
import stroom.query.client.component.ExpressionEditorController;
import stroom.query.client.presenter.SimpleFieldSelectionListModel;
import stroom.widget.presenter.client.MyPresenterWidget;
import stroom.widget.presenter.client.View;

import com.google.gwt.user.client.ui.Focus;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.List;
import java.util.Objects;

public class ExpressionPresenter
        extends MyPresenterWidget<ExpressionPresenter.ExpressionView>
        implements Focus {

    private final ExpressionEditorController expressionEditor;

    @Inject
    public ExpressionPresenter(final EventBus eventBus,
                               final ExpressionView view,
                               final ExpressionEditorController expressionEditor) {
        super(eventBus, view);
        own(expressionEditor);
        this.expressionEditor = expressionEditor;
        view.setExpressionView(expressionEditor);
    }

    @Override
    public void focus() {
        expressionEditor.focus();
    }

    public void read(final ExpressionOperator expression,
                     final DocRef dataSource,
                     final List<QueryField> fields) {

        final SimpleFieldSelectionListModel fieldSelectionBoxModel = new SimpleFieldSelectionListModel();
        fieldSelectionBoxModel.addItems(fields);
        expressionEditor.init(dataSource, fieldSelectionBoxModel);

        expressionEditor.read(Objects.requireNonNullElseGet(expression,
                () -> ExpressionOperator.builder().build()));
    }

    public ExpressionOperator write() {
        return expressionEditor.write();
    }


    // --------------------------------------------------------------------------------


    public interface ExpressionView extends View {

        void setExpressionView(IsWidget view);
    }
}
