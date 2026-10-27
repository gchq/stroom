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

package stroom.gwt.workbench.client.app.gin;

import stroom.dashboard.client.table.cf.RulePresenter;
import stroom.dashboard.client.table.cf.RuleViewImpl;
import stroom.dashboard.client.table.cf.RulesPresenter;
import stroom.dashboard.client.table.cf.RulesViewImpl;
import stroom.query.client.presenter.QueryResultTablePresenter;
import stroom.query.client.presenter.QueryResultTablePresenter.QueryResultTableView;
import stroom.query.client.view.QueryResultTableViewImpl;
import stroom.widget.presenter.client.gin.AbstractPresenterModule;

/// The bindings of a query's results table (`QueryResultTablePresenter`) and the dialogs it opens
/// (column filters, formats, downloads and conditional formatting), for screens that show one
/// outside the Query editor, e.g. the Data tab of an index or a Plan B store. The query injector
/// has these from its `QueryScreenModule` and `TableScreenModule`, so doesn't include this one.
public class QueryResultTableScreenModule extends AbstractPresenterModule {

    /// Binds the presenters and views.
    @Override
    protected void configure() {
        bindPresenterWidget(QueryResultTablePresenter.class,
                QueryResultTableView.class,
                QueryResultTableViewImpl.class);
        bindPresenterWidget(RulesPresenter.class,
                RulesPresenter.RulesView.class,
                RulesViewImpl.class);
        bindPresenterWidget(RulePresenter.class,
                RulePresenter.RuleView.class,
                RuleViewImpl.class);
    }
}
