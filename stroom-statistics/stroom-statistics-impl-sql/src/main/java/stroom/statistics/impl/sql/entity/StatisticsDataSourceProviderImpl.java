/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.statistics.impl.sql.entity;

import stroom.datasource.api.v2.AbstractField;
import stroom.datasource.api.v2.DataSource;
import stroom.datasource.api.v2.DateField;
import stroom.datasource.api.v2.NumberField;
import stroom.datasource.api.v2.TextField;
import stroom.docref.DocRef;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.statistics.impl.sql.Statistics;
import stroom.statistics.impl.sql.shared.StatisticField;
import stroom.statistics.impl.sql.shared.StatisticStoreDoc;
import stroom.statistics.impl.sql.shared.StatisticType;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

class StatisticsDataSourceProviderImpl implements StatisticsDataSourceProvider {
    private final StatisticStoreCache statisticStoreCache;
    private final Statistics statistics;

    @Inject
    StatisticsDataSourceProviderImpl(final StatisticStoreCache statisticStoreCache,
                                     final Statistics statistics) {
        this.statisticStoreCache = statisticStoreCache;
        this.statistics = statistics;
    }

    @Override
    public DataSource getDataSource(final DocRef docRef) {
        final StatisticStoreDoc entity = statisticStoreCache.getStatisticsDataSource(docRef);
        if (entity == null) {
            return null;
        }

        final List<AbstractField> fields = buildFields(entity);

        return new DataSource(fields);
    }

    /**
     * Turn the {@link StatisticStoreDoc} into an {@link List<AbstractField>} object
     * <p>
     * This builds the standard set of fields for a statistics store, which can
     * be filtered by the relevant statistics store instance
     */
    private List<AbstractField> buildFields(final StatisticStoreDoc entity) {
        List<AbstractField> fields = new ArrayList<>();

        // TODO currently only BETWEEN is supported, but need to add support for
        // more conditions like >, >=, <, <=, =
        fields.add(new DateField(StatisticStoreDoc.FIELD_NAME_DATE_TIME, true, Collections.singletonList(Condition.BETWEEN)));

        // one field per tag
        if (entity.getConfig() != null) {
            final List<Condition> supportedConditions = Arrays.asList(Condition.EQUALS, Condition.IN);

            for (final StatisticField statisticField : entity.getStatisticFields()) {
                // TODO currently only EQUALS is supported, but need to add
                // support for more conditions like CONTAINS
                fields.add(new TextField(statisticField.getFieldName(), true, supportedConditions));
            }
        }

        fields.add(new NumberField(StatisticStoreDoc.FIELD_NAME_COUNT, false, Collections.emptyList()));

        if (entity.getStatisticType().equals(StatisticType.VALUE)) {
            fields.add(new NumberField(StatisticStoreDoc.FIELD_NAME_VALUE, false, Collections.emptyList()));
        }

        fields.add(new NumberField(StatisticStoreDoc.FIELD_NAME_PRECISION_MS, false, Collections.emptyList()));

        // Filter fields.
        if (entity.getConfig() != null) {
            fields = statistics.getSupportedFields(fields);
        }

        return fields;
    }
}
