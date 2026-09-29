/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.floormap.client.presenter;

import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.floormap.shared.FloorMapEventStoreDoc;
import stroom.query.api.Column;
import stroom.query.client.presenter.AbstractQueryDataPresenter;
import stroom.query.client.presenter.DateTimeSettingsFactory;
import stroom.query.client.presenter.QueryDataView;
import stroom.query.client.presenter.QueryResultTablePresenter;
import stroom.query.client.presenter.ResultStoreModel;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.List;

/// The Data tab of a [FloorMapEventStoreDoc] - an editable query over the store's own rows.
///
/// The same tab a Plan B store has, and deliberately the same shape, because it answers the same
/// question: what is actually in here? It differs only in the default query, which adds the five
/// event properties the floor map reads out of the JSON value. A Plan B store shows `Value` as
/// one opaque blob, which is the right default when the store's contents are unknown; here they are
/// known, so the tab shows them.
///
/// No `switch` on state type, unlike `PlanBDataPresenter`: this document type fixes
/// `TEMPORAL_STATE`, so there is one shape and one default.
///
/// The query carries neither `readMode` nor `asAt`, so it takes the ordinary range
/// read rather than the map's point-in-time snapshot - the tab is for inspecting rows, not for
/// reconstructing a moment.
public class FloorMapEventStoreDataPresenter
        extends AbstractQueryDataPresenter<
                FloorMapEventStoreDataPresenter.FloorMapEventStoreDataView, FloorMapEventStoreDoc> {

    public interface FloorMapEventStoreDataView extends QueryDataView {

    }

    @Inject
    public FloorMapEventStoreDataPresenter(final EventBus eventBus,
                                           final FloorMapEventStoreDataView view,
                                           final QueryResultTablePresenter tablePresenter,
                                           final RestFactory restFactory,
                                           final DateTimeSettingsFactory dateTimeSettingsFactory,
                                           final ResultStoreModel resultStoreModel) {
        super(eventBus, view, tablePresenter, restFactory, dateTimeSettingsFactory, resultStoreModel);
    }

    @Override
    protected String getDefaultQuery(final DocRef docRef, final FloorMapEventStoreDoc doc) {
        return FloorMapEventStoreDataDefaults.query(docRef.getName());
    }

    @Override
    protected List<Column> getPreferredColumns(final FloorMapEventStoreDoc doc) {
        return FloorMapEventStoreDataDefaults.columns();
    }
}
