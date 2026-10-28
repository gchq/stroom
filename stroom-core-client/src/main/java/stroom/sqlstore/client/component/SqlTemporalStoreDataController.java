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

package stroom.sqlstore.client.component;

import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.query.api.Column;
import stroom.query.client.DateTimeSettingsFactory;
import stroom.query.client.ResultStoreModel;
import stroom.query.client.component.AbstractQueryDataController;
import stroom.query.client.component.QueryDataPane;
import stroom.query.client.component.QueryResultTableController;
import stroom.sqlstore.shared.SqlTemporalStoreDoc;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Arrays;
import java.util.List;

/// A SQL temporal store's Data tab: a StroomQL query of its keys and values.
public class SqlTemporalStoreDataController
        extends AbstractQueryDataController<SqlTemporalStoreDoc> {


    private final Column timeCol = Column.builder()
            .id("EffectiveTime")
            .name("Effective Time")
            .expression("EffectiveTime")
            .build();
    private final Column keyCol = Column.builder()
            .id("Key")
            .name("Key")
            .expression("Key")
            .build();
    private final Column valueCol = Column.builder()
            .id("Value")
            .name("Value")
            .expression("substring(Value, 1, 100)")
            .build();

    /// Made by GIN, with its content and its parts.
    @Inject
    public SqlTemporalStoreDataController(final EventBus eventBus,
                                          final QueryDataPane view,
                                          final QueryResultTableController tableController,
                                          final RestFactory restFactory,
                                          final DateTimeSettingsFactory dateTimeSettingsFactory,
                                          final ResultStoreModel resultStoreModel) {
        super(eventBus, view, tableController, restFactory, dateTimeSettingsFactory, resultStoreModel);
    }

    @Override
    protected String getDefaultQuery(final DocRef docRef, final SqlTemporalStoreDoc doc) {
        return "from \"" + docRef.getName() + "\" limit 100 select EffectiveTime as \"Effective Time\", Key, Value";
    }

    @Override
    protected List<Column> getPreferredColumns(final SqlTemporalStoreDoc doc) {
        return Arrays.asList(timeCol, keyCol, valueCol);
    }
}
