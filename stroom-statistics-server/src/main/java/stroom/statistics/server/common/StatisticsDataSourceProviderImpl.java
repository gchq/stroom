/*
 * Copyright 2016 Crown Copyright
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

package stroom.statistics.server.common;

import stroom.query.shared.Condition;
import stroom.query.shared.DataSource;
import stroom.query.shared.IndexField;
import stroom.query.shared.IndexFieldType;
import stroom.query.shared.IndexFields;
import stroom.statistics.common.StatisticStoreEntityService;
import stroom.statistics.common.Statistics;
import stroom.statistics.common.StatisticsFactory;
import stroom.statistics.shared.StatisticField;
import stroom.statistics.shared.StatisticStoreEntity;
import stroom.statistics.shared.StatisticType;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.List;

@Component
public class StatisticsDataSourceProviderImpl implements StatisticsDataSourceProvider {
    private final StatisticStoreEntityService statisticStoreEntityService;
    private final StatisticsFactory statisticsFactory;

    @Inject
    StatisticsDataSourceProviderImpl(final StatisticStoreEntityService statisticStoreEntityService, final StatisticsFactory statisticsFactory) {
        this.statisticStoreEntityService = statisticStoreEntityService;
        this.statisticsFactory = statisticsFactory;
    }

    @Override
    public DataSource getDataSource(final String uuid) {
        final StatisticStoreEntity entity = statisticStoreEntityService.loadByUuid(uuid);
        if (entity == null) {
            return null;
        }

        final IndexFields indexFields = buildIndexFields(entity);
        final DataSourceImpl statisticsDataSource = new DataSourceImpl(entity.getType(), entity.getId(),
                entity.getName(), indexFields);
        return statisticsDataSource;
    }

    @Override
    public String getEntityType() {
        return StatisticStoreEntity.ENTITY_TYPE;
    }

    /**
     * Turn the {@link StatisticStoreEntity} into an {@link IndexFields} object
     * <p>
     * This builds the standard set of fields for a statistics store, which can
     * be filtered by the relevant statistics store instance
     */
    private IndexFields buildIndexFields(final StatisticStoreEntity entity) {
        IndexFields indexFields = new IndexFields();

        // TODO currently only BETWEEN is supported, but need to add support for
        // more conditions like >, >=, <, <=, =
        addField(StatisticStoreEntity.FIELD_NAME_DATE_TIME, IndexFieldType.DATE_FIELD, true,
                Arrays.asList(Condition.BETWEEN), indexFields);

        // one field per tag
        if (entity.getStatisticDataSourceDataObject() != null) {
            for (final StatisticField statisticField : entity.getStatisticFields()) {
                // TODO currently only EQUALS is supported, but need to add
                // support for more conditions like CONTAINS
                addField(statisticField.getFieldName(), IndexFieldType.FIELD, true,
                        Arrays.asList(Condition.EQUALS, Condition.IN), indexFields);
            }
        }

        addField(StatisticStoreEntity.FIELD_NAME_COUNT, IndexFieldType.NUMERIC_FIELD, false, null, indexFields);

        if (entity.getStatisticType().equals(StatisticType.VALUE)) {
            addField(StatisticStoreEntity.FIELD_NAME_VALUE, IndexFieldType.NUMERIC_FIELD, false, null, indexFields);
            addField(StatisticStoreEntity.FIELD_NAME_MIN_VALUE, IndexFieldType.NUMERIC_FIELD, false, null, indexFields);
            addField(StatisticStoreEntity.FIELD_NAME_MAX_VALUE, IndexFieldType.NUMERIC_FIELD, false, null, indexFields);
        }

        addField(StatisticStoreEntity.FIELD_NAME_PRECISION, IndexFieldType.NUMERIC_FIELD, false, null, indexFields);
        addField(StatisticStoreEntity.FIELD_NAME_PRECISION_MS, IndexFieldType.NUMERIC_FIELD, false, null, indexFields);

        // Filter fields.
        if (entity.getStatisticDataSourceDataObject() != null) {
            final Statistics statistics = statisticsFactory.instance(entity.getEngineName());
            if (statistics != null && statistics instanceof AbstractStatistics) {
                indexFields = ((AbstractStatistics) statistics).getSupportedFields(indexFields);
            }
        }

        return indexFields;
    }

    /**
     * @return A reference to the create index field so additional modifications
     * can be made
     */
    private void addField(final String name, final IndexFieldType type, final boolean isQueryable,
                          final List<Condition> supportedConditions, final IndexFields indexFields) {
        final IndexField field = new IndexField();
        field.setFieldName(name);
        field.setFieldType(type);
        // setIndexed should stop the field being queryable
        field.setIndexed(isQueryable);
        field.setSupportedConditions(supportedConditions);
        indexFields.add(field);
    }
}
