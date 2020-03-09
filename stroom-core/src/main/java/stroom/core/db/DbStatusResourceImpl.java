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

package stroom.core.db;

import com.codahale.metrics.health.HealthCheck.Result;
import stroom.node.shared.DBTableStatus;
import stroom.node.shared.DbStatusResource;
import stroom.node.shared.FindDBTableCriteria;
import stroom.util.HasHealthCheck;
import stroom.util.shared.ResultPage;

import javax.inject.Inject;

// TODO : @66 add event logging
class DbStatusResourceImpl implements DbStatusResource, HasHealthCheck {
    private final DBTableService dbTableService;

    @Inject
    DbStatusResourceImpl(final DBTableService dbTableService) {
        this.dbTableService = dbTableService;
    }

    @Override
    public ResultPage<DBTableStatus> getSystemTableStatus() {
        return dbTableService.getSystemTableStatus();
    }

    @Override
    public ResultPage<DBTableStatus> findSystemTableStatus(final FindDBTableCriteria criteria) {
        return dbTableService.findSystemTableStatus(criteria);
    }

    @Override
    public Result getHealth() {
        return Result.healthy();
    }
}