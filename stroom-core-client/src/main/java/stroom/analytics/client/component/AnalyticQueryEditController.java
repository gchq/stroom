/*
 * Copyright 2023 Crown Copyright
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

package stroom.analytics.client.component;

import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.docref.DocRef;
import stroom.entity.client.presenter.HasToolbar;
import stroom.query.client.component.QueryEditController;
import stroom.widget.component.client.Controller;

import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.List;

/// The Query tab of an analytic rule: its query ([QueryEditController]), time range and table preferences.
public class AnalyticQueryEditController
        extends Controller
        implements HasToolbar {

    private final QueryEditController queryEdit;

    /// Made by GIN, with its query.
    ///
    /// @param eventBus  The application's event bus.
    /// @param queryEdit The query.
    @Inject
    public AnalyticQueryEditController(final EventBus eventBus,
                                       final QueryEditController queryEdit) {
        super(eventBus);
        own(queryEdit);
        this.queryEdit = queryEdit;
        initWidget(queryEdit.asWidget());

        queryEdit.setOnChange(this::markChanged);
    }

    @Override
    public List<Widget> getToolbars() {
        return queryEdit.getToolbars();
    }

    /// Shows the document's query, time range and table preferences.
    ///
    /// @param docRef The document.
    /// @param entity The document.
    public void read(final DocRef docRef, final AnalyticRuleDoc entity) {
        queryEdit.setTimeRange(entity.getTimeRange());
        queryEdit.setQuery(docRef, entity.getQuery());
        queryEdit.read(entity.getQueryTablePreferences());
    }

    /// @param entity The document.
    /// @return A copy of it with the query, time range and table preferences shown.
    public AnalyticRuleDoc write(final AnalyticRuleDoc entity) {
        return entity
                .copy()
                .timeRange(queryEdit.getTimeRange())
                .query(queryEdit.getQuery())
                .queryTablePreferences(queryEdit.write())
                .build();
    }

    /// Runs the query again.
    public void start() {
        queryEdit.start();
    }

    /// Stops the query.
    public void stop() {
        queryEdit.stop();
    }
}
