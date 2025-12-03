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

package stroom.statistics.impl.sql.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@JsonPropertyOrder({"fields", "customRollUpMasks"})
@JsonInclude(Include.NON_NULL)
public class StatisticsDataSourceData {

    /**
     * Should be a SortedSet but GWT doesn't support that. Contents should be
     * sorted and not contain duplicates
     * <p>
     * XMLTransient to force JAXB to use the setter
     */
    @JsonProperty
    private List<StatisticField> fields;

    /**
     * Held in a set to prevent duplicates.
     * <p>
     * XMLTransient to force JAXB to use the setter
     */
    @JsonProperty
    private Set<CustomRollUpMask> customRollUpMasks;

    // cache the positions of the
    @JsonIgnore
    private Map<String, Integer> fieldPositionMap;

    public StatisticsDataSourceData() {
        this(new ArrayList<>(), new HashSet<>());
    }

    public StatisticsDataSourceData(final List<StatisticField> fields) {
        this(new ArrayList<>(fields), new HashSet<>());
    }

    @JsonCreator
    public StatisticsDataSourceData(@JsonProperty("fields") final List<StatisticField> fields,
                                    @JsonProperty("customRollUpMasks") final Set<CustomRollUpMask> customRollUpMasks) {
        this.fields = fields;
        this.customRollUpMasks = customRollUpMasks;
    }

    public List<StatisticField> getFields() {
        return fields;
    }

    public void setFields(final List<StatisticField> fields) {
        this.fields = fields;
    }

    public Set<CustomRollUpMask> getCustomRollUpMasks() {
        return customRollUpMasks;
    }

    public void setCustomRollUpMasks(final Set<CustomRollUpMask> customRollUpMasks) {
        this.customRollUpMasks = customRollUpMasks;
    }

    public void addStatisticField(final StatisticField statisticField) {
        if (fields == null) {
            fields = new ArrayList<>();
        }

        // prevent duplicates
        if (!fields.contains(statisticField)) {
            fields.add(statisticField);
            sortFields();
            fieldPositionMap = null;
        }
    }

    public void removeStatisticField(final StatisticField statisticField) {
        if (fields != null) {
            fields.remove(statisticField);
            fieldPositionMap = null;
        }
    }

    public void reOrderStatisticFields() {
        if (fields != null) {
            fieldPositionMap = null;
        }
    }

    public boolean containsStatisticField(final StatisticField statisticField) {
        if (fields != null) {
            return fields.contains(statisticField);
        }
        return false;
    }

    public void addCustomRollUpMask(final CustomRollUpMask customRollUpMask) {
        if (customRollUpMasks == null) {
            customRollUpMasks = new HashSet<>();
        }

        customRollUpMasks.add(customRollUpMask);
    }

    public void removeCustomRollUpMask(final CustomRollUpMask customRollUpMask) {
        if (customRollUpMasks != null) {
            customRollUpMasks.remove(customRollUpMask);
        }
    }

    public void clearCustomRollUpMask() {
        if (customRollUpMasks != null) {
            customRollUpMasks.clear();
        }
    }

    public boolean containsCustomRollUpMask(final CustomRollUpMask customRollUpMask) {
        if (customRollUpMasks != null) {
            return customRollUpMasks.contains(customRollUpMask);
        }
        return false;
    }

    public boolean isRollUpCombinationSupported(final Set<String> rolledUpFieldNames) {
        final Map<String, Integer> fieldPositionMap = getFieldPositionMap();

        if (rolledUpFieldNames == null || rolledUpFieldNames.isEmpty()) {
            return true;
        }

        if (rolledUpFieldNames.size() > fields.size()) {
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
            rolledUpFieldPositions.add(getFieldPositionInList(rolledUpField));
        }

        return customRollUpMasks.contains(new CustomRollUpMask(rolledUpFieldPositions));
    }

    public Integer getFieldPositionInList(final String fieldName) {
        final Map<String, Integer> fieldPositionMap = getFieldPositionMap();
        return fieldPositionMap.get(fieldName);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((fields == null)
                ? 0
                : fields.hashCode());
        return result;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final StatisticsDataSourceData other = (StatisticsDataSourceData) obj;
        if (fields == null) {
            return other.fields == null;
        } else {
            return fields.equals(other.fields);
        }
    }

    @Override
    public String toString() {
        return "StatisticFields [statisticFields=" + fields + "]";
    }

    private Map<String, Integer> getFieldPositionMap() {
        if (fieldPositionMap != null) {
            return fieldPositionMap;
        }
        return updateFieldPositionbMap();
    }

    private Map<String, Integer> updateFieldPositionbMap() {
        final Map<String, Integer> fieldPositionMap = createFieldPositionMap();
        this.fieldPositionMap = fieldPositionMap;
        return fieldPositionMap;
    }

    private void sortFields() {
        fields.sort(Comparator.naturalOrder());
    }

    private Map<String, Integer> createFieldPositionMap() {
        final Map<String, Integer> fieldPositionMap = new HashMap<>();
        int i = 0;
        for (final StatisticField field : fields) {
            fieldPositionMap.put(field.getFieldName(), i++);
        }
        return fieldPositionMap;
    }

    public StatisticsDataSourceData deepCopy() {
        final List<StatisticField> newFieldList = new ArrayList<>();

        for (final StatisticField statisticField : fields) {
            newFieldList.add(statisticField.deepCopy());
        }

        final Set<CustomRollUpMask> newMaskList = new HashSet<>();

        for (final CustomRollUpMask customRollUpMask : customRollUpMasks) {
            newMaskList.add(customRollUpMask.deepCopy());
        }

        return new StatisticsDataSourceData(newFieldList, newMaskList);
    }
}
