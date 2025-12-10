
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

package stroom.core.db;

import stroom.event.logging.rs.api.AutoLogged;
import stroom.node.shared.DBTableStatus;
import stroom.node.shared.DbStatusResource;
import stroom.node.shared.FindDBTableCriteria;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

@AutoLogged
class DbStatusResourceImpl implements DbStatusResource {

    private final Provider<DBTableService> dbTableServiceProvider;

    @Inject
    DbStatusResourceImpl(final Provider<DBTableService> dbTableServiceProvider) {
        this.dbTableServiceProvider = dbTableServiceProvider;
    }

    @Override
    public ResultPage<DBTableStatus> getSystemTableStatus() {
        return dbTableServiceProvider.get().getSystemTableStatus();
    }

    @Override
    public ResultPage<DBTableStatus> findSystemTableStatus(final FindDBTableCriteria criteria) {
        return dbTableServiceProvider.get().findSystemTableStatus(criteria);
    }
}
