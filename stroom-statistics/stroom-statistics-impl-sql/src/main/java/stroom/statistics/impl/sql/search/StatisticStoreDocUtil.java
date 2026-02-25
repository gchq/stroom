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

package stroom.statistics.impl.sql.search;

import stroom.statistics.impl.sql.shared.CustomRollUpMask;
import stroom.statistics.impl.sql.shared.StatisticField;
import stroom.statistics.impl.sql.shared.StatisticRollUpType;
import stroom.statistics.impl.sql.shared.StatisticStoreDoc;
import stroom.statistics.impl.sql.shared.StatisticsDataSourceData;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class StatisticStoreDocUtil {

    public static boolean isRollUpCombinationSupported(final StatisticStoreDoc dataSource,
                                                       final Set<String> rolledUpFieldNames) {
        if (rolledUpFieldNames == null || rolledUpFieldNames.isEmpty()) {
            return true;
        }

        if (dataSource.getRollUpType().equals(StatisticRollUpType.NONE)) {
            return false;
        }

        if (dataSource.getRollUpType().equals(StatisticRollUpType.ALL)) {
            return true;
        }

        // rolledUpFieldNames not empty if we get here
        if (dataSource.getConfig() == null) {
            throw new RuntimeException("isRollUpCombinationSupported called with non-empty list but data source " +
                                       "has no statistic fields or custom roll up masks");
        }

        return isRollUpCombinationSupported2(dataSource, rolledUpFieldNames);
    }

    public static boolean isRollUpCombinationSupported2(final StatisticStoreDoc dataSource,
                                                        final Set<String> rolledUpFieldNames) {
        final Map<String, Integer> fieldPositionMap = createFieldPositionMap(dataSource);

        if (rolledUpFieldNames == null || rolledUpFieldNames.isEmpty()) {
            return true;
        }

        if (rolledUpFieldNames.size() > dataSource.getConfig().getFields().size()) {
            throw new RuntimeException(
                    "isRollUpCombinationSupported called with more rolled up fields (" + rolledUpFieldNames
                    + ") than there are statistic fields (" + fieldPositionMap.keySet() + ")");
        }

        if (!fieldPositionMap.keySet().containsAll(rolledUpFieldNames)) {
            throw new RuntimeException(
                    "isRollUpCombinationSupported called rolled up fields (" + rolledUpFieldNames
                    + ") that don't exist in the statistic fields list (" + fieldPositionMap.keySet() + ")");
        }

        final List<Integer> rolledUpFieldPositions = new ArrayList<>();
        for (final String rolledUpField : rolledUpFieldNames) {
            rolledUpFieldPositions.add(fieldPositionMap.get(rolledUpField));
        }

        return dataSource.getConfig().getCustomRollUpMasks().contains(new CustomRollUpMask(rolledUpFieldPositions));
    }

    public static Map<String, Integer> createFieldPositionMap(final StatisticStoreDoc dataSource) {
        final Map<String, Integer> fieldPositionMap = new HashMap<>();
        int i = 0;
        if (dataSource != null && dataSource.getConfig() != null) {
            for (final StatisticField field : dataSource.getConfig().getFields()) {
                fieldPositionMap.put(field.getFieldName(), i++);
            }
        }
        return fieldPositionMap;
    }

    public static Map<String, Integer> createFieldPositionMap(final StatisticsDataSourceData config) {
        final Map<String, Integer> fieldPositionMap = new HashMap<>();
        int i = 0;
        if (config != null) {
            for (final StatisticField field : config.getFields()) {
                fieldPositionMap.put(field.getFieldName(), i++);
            }
        }
        return fieldPositionMap;
    }

    public static List<String> getFieldNames(final StatisticStoreDoc dataSource) {
        return NullSafe.getOrElse(
                dataSource,
                StatisticStoreDoc::getConfig,
                StatisticsDataSourceData::getFields,
                fields -> fields.stream().map(StatisticField::getFieldName).toList(),
                Collections.emptyList());
    }

    public static StatisticsDataSourceData addCustomRollUpMask(final StatisticsDataSourceData statisticsDataSourceData,
                                                               final CustomRollUpMask customRollUpMask) {
        final Set<CustomRollUpMask> set = new HashSet<>(statisticsDataSourceData.getCustomRollUpMasks());
        set.add(customRollUpMask);
        return statisticsDataSourceData.copy().customRollUpMasks(set).build();
    }

    public static StatisticStoreDoc addField(final StatisticStoreDoc doc, final StatisticField field) {
        final List<StatisticField> fields = new ArrayList<>(doc.getConfig().getFields());
        fields.add(field);
        return doc.copy().config(doc.getConfig().copy().fields(fields).build()).build();
    }

    public static StatisticStoreDoc removeField(final StatisticStoreDoc doc, final StatisticField field) {
        final List<StatisticField> fields = new ArrayList<>(doc.getConfig().getFields());
        fields.remove(field);
        return doc.copy().config(doc.getConfig().copy().fields(fields).build()).build();
    }
}
