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

import static stroom.storedquery.impl.db.jooq.tables.Query.QUERY;

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
        return StoredQuery
                .builder()
                .id(record.get(QUERY.ID))
                .version(record.get(QUERY.VERSION))
                .createTimeMs(record.get(QUERY.CREATE_TIME_MS))
                .createUser(record.get(QUERY.CREATE_USER))
                .updateTimeMs(record.get(QUERY.UPDATE_TIME_MS))
                .updateUser(record.get(QUERY.UPDATE_USER))
                .dashboardUuid(record.get(QUERY.DASHBOARD_UUID))
                .componentId(record.get(QUERY.COMPONENT_ID))
                .name(record.get(QUERY.NAME))
                .query(queryJsonSerialiser.deserialise(record.get(QUERY.DATA)))
                .favourite(record.get(QUERY.FAVOURITE))
                .uuid(record.get(QUERY.UUID))
                .owner(userRefLookupProvider.get().getByUuid(record.get(QUERY.OWNER_UUID), FindUserContext.RUN_AS)
                        .orElse(null))
                .build();
    }
}
