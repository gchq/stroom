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

package stroom.statistics.impl.sql.entity;

import com.codahale.metrics.health.HealthCheck.Result;
import stroom.docref.DocRef;
import stroom.docstore.api.DocumentResourceHelper;
import stroom.statistics.impl.sql.shared.StatisticResource;
import stroom.statistics.impl.sql.shared.StatisticStoreDoc;
import stroom.util.HasHealthCheck;
import stroom.util.shared.RestResource;

import javax.inject.Inject;

class StatisticResourceImpl implements StatisticResource, RestResource, HasHealthCheck {
    private final StatisticStoreStore statisticStoreStore;
    private final DocumentResourceHelper documentResourceHelper;

    @Inject
    StatisticResourceImpl(final StatisticStoreStore statisticStoreStore,
                          final DocumentResourceHelper documentResourceHelper) {
        this.statisticStoreStore = statisticStoreStore;
        this.documentResourceHelper = documentResourceHelper;
    }

    @Override
    public StatisticStoreDoc read(final DocRef docRef) {
        return documentResourceHelper.read(statisticStoreStore, docRef);
    }

    @Override
    public StatisticStoreDoc update(final StatisticStoreDoc doc) {
        return documentResourceHelper.update(statisticStoreStore, doc);
    }

    @Override
    public Result getHealth() {
        return Result.healthy();
    }
}