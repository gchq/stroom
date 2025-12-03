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

package stroom.data.client.presenter;

import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.datasource.QueryField;
import stroom.query.client.presenter.SimpleFieldSelectionListModel;
import stroom.util.shared.NullSafe;

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.List;

public class ExpressionPresenter
        extends MyPresenterWidget<ExpressionPresenter.ExpressionView>
        implements Focus {

    private final EditExpressionPresenter editExpressionPresenter;
    private final RestFactory restFactory;

    @Inject
    public ExpressionPresenter(final EventBus eventBus,
                               final ExpressionView view,
                               final EditExpressionPresenter editExpressionPresenter,
                               final RestFactory restFactory) {
        super(eventBus, view);
        this.editExpressionPresenter = editExpressionPresenter;
        this.restFactory = restFactory;
        view.setExpressionView(editExpressionPresenter.getView());
    }

    @Override
    public void focus() {
        editExpressionPresenter.focus();
    }

    public void read(final ExpressionOperator expression,
                     final DocRef dataSource,
                     final List<QueryField> fields) {

        final SimpleFieldSelectionListModel fieldSelectionBoxModel = new SimpleFieldSelectionListModel();
        fieldSelectionBoxModel.addItems(fields);
        editExpressionPresenter.init(restFactory, dataSource, fieldSelectionBoxModel);

        editExpressionPresenter.read(NullSafe.requireNonNullElseGet(
                expression,
                () -> ExpressionOperator.builder().build()));
    }

    public ExpressionOperator write() {
        return editExpressionPresenter.write();
    }


    // --------------------------------------------------------------------------------


    public interface ExpressionView extends View {

        void setExpressionView(View view);
    }
}
