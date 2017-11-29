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

package stroom.process.client.presenter;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import stroom.datasource.api.v2.DataSourceField;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.ruleset.client.presenter.EditExpressionPresenter;
import stroom.streamstore.shared.QueryData;

import java.util.List;

public class FilterPresenter extends MyPresenterWidget<FilterPresenter.FilterView> {
    private final EditExpressionPresenter editExpressionPresenter;

    @Inject
    public FilterPresenter(final EventBus eventBus,
                           final FilterView view,
                           final EditExpressionPresenter editExpressionPresenter) {
        super(eventBus, view);
        this.editExpressionPresenter = editExpressionPresenter;
        view.setExpressionView(editExpressionPresenter.getView());
    }

    void read(final QueryData queryData, final List<DataSourceField> fields) {
        editExpressionPresenter.init(null, null, fields);
        editExpressionPresenter.read(getExpressionFromQueryData(queryData));
    }

    private ExpressionOperator getExpressionFromQueryData(final QueryData queryData) {
        if (queryData != null && queryData.getExpression() != null) {
            return queryData.getExpression();
        }

        return new ExpressionOperator.Builder(Op.AND).build();
    }

    private QueryData getQueryDataFromExpression(final ExpressionOperator expressionOperator) {
        final QueryData queryData = new QueryData();
        queryData.setExpression(expressionOperator);
        queryData.setDataSource(QueryData.STREAM_STORE_DOC_REF);
        return queryData;
    }

    QueryData write() {
        final ExpressionOperator expression = editExpressionPresenter.write();
        return getQueryDataFromExpression(expression);
    }

    public interface FilterView extends View {
        void setExpressionView(View view);
    }
}
