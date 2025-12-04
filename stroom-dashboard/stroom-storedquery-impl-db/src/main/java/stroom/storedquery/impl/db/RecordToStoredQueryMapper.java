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

package stroom.storedquery.impl.db;

import stroom.dashboard.shared.StoredQuery;
import stroom.security.shared.FindUserContext;
import stroom.security.user.api.UserRefLookup;

import jakarta.inject.Provider;
import org.jooq.Record;

import java.util.function.Function;

import static stroom.storedquery.impl.db.jooq.Tables.QUERY;

class RecordToStoredQueryMapper implements Function<Record, StoredQuery> {

    private final QueryJsonSerialiser queryJsonSerialiser;
    private final Provider<UserRefLookup> userRefLookupProvider;

    public RecordToStoredQueryMapper(final QueryJsonSerialiser queryJsonSerialiser,
                                     final Provider<UserRefLookup> userRefLookupProvider) {
        this.queryJsonSerialiser = queryJsonSerialiser;
        this.userRefLookupProvider = userRefLookupProvider;
    }

    @Override
    public StoredQuery apply(final Record record) {
        final StoredQuery query = new StoredQuery();
        query.setId(record.get(QUERY.ID));
        query.setVersion(record.get(QUERY.VERSION));
        query.setCreateTimeMs(record.get(QUERY.CREATE_TIME_MS));
        query.setCreateUser(record.get(QUERY.CREATE_USER));
        query.setUpdateTimeMs(record.get(QUERY.UPDATE_TIME_MS));
        query.setUpdateUser(record.get(QUERY.UPDATE_USER));
        query.setDashboardUuid(record.get(QUERY.DASHBOARD_UUID));
        query.setComponentId(record.get(QUERY.COMPONENT_ID));
        query.setName(record.get(QUERY.NAME));
        query.setQuery(queryJsonSerialiser.deserialise(record.get(QUERY.DATA)));
        query.setFavourite(record.get(QUERY.FAVOURITE));
        query.setUuid(record.get(QUERY.UUID));
        query.setOwner(userRefLookupProvider.get()
                .getByUuid(record.get(QUERY.OWNER_UUID), FindUserContext.RUN_AS)
                .orElse(null));
        return query;
    }
}
