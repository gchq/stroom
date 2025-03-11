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

package stroom.legacy.model_6_1;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlTransient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@XmlRootElement(name = "data")
@Deprecated
public class StatisticsDataSourceData implements SharedObject {

    private static final long serialVersionUID = -9071682094300037627L;

    /**
     * Should be a SortedSet but GWT doesn't support that. Contents should be
     * sorted and not contain duplicates
     * <p>
     * XMLTransient to force JAXB to use the setter
     */

    @XmlTransient
    private List<StatisticField> statisticFields;

    /**
     * Held in a set to prevent duplicates.
     * <p>
     * XMLTransient to force JAXB to use the setter
     */
    @XmlTransient
    private Set<CustomRollUpMask> customRollUpMasks;

    // cache the positions of the
    @XmlTransient
    private Map<String, Integer> fieldPositionMap = new HashMap<>();

    public StatisticsDataSourceData() {
        this(new ArrayList<>(), new HashSet<>());
    }

    public StatisticsDataSourceData(final List<StatisticField> statisticFields) {
        this(new ArrayList<>(statisticFields), new HashSet<>());
    }

    public StatisticsDataSourceData(final List<StatisticField> statisticFields,
                                    final Set<CustomRollUpMask> customRollUpMasks) {
        this.statisticFields = statisticFields;
        this.customRollUpMasks = customRollUpMasks;

        // sort the list of fields as this will help us later when generating
        // StatisticEvents
        sortFieldListAndCachePositions();
    }

    @XmlElement(name = "field")
    public List<StatisticField> getStatisticFields() {
        return statisticFields;
    }

    public void setStatisticFields(final List<StatisticField> statisticFields) {
        this.statisticFields = statisticFields;
        sortFieldListAndCachePositions();
    }

    @XmlElement(name = "customRollUpMask")
    public Set<CustomRollUpMask> getCustomRollUpMasks() {
        return customRollUpMasks;
    }

    public void setCustomRollUpMasks(final Set<CustomRollUpMask> customRollUpMasks) {
        this.customRollUpMasks = customRollUpMasks;
    }

    public void addStatisticField(final StatisticField statisticField) {
        if (statisticFields == null) {
            statisticFields = new ArrayList<>();
        }
        // prevent duplicates
        if (!statisticFields.contains(statisticField)) {
            statisticFields.add(statisticField);
            sortFieldListAndCachePositions();
        }
    }

    public void removeStatisticField(final StatisticField statisticField) {
        if (statisticFields != null) {
            statisticFields.remove(statisticField);
            sortFieldListAndCachePositions();
        }
    }

    public void reOrderStatisticFields() {
        if (statisticFields != null) {
            sortFieldListAndCachePositions();
        }
    }

    public boolean containsStatisticField(final StatisticField statisticField) {
        if (statisticFields != null) {
            return statisticFields.contains(statisticField);
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
        if (rolledUpFieldNames == null || rolledUpFieldNames.isEmpty()) {
            return true;
        }

        if (rolledUpFieldNames.size() > statisticFields.size()) {
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
        return fieldPositionMap.get(fieldName);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((statisticFields == null)
                ? 0
                : statisticFields.hashCode());
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
        if (statisticFields == null) {
            return other.statisticFields == null;
        } else return statisticFields.equals(other.statisticFields);
    }

    @Override
    public String toString() {
        return "StatisticFields [statisticFields=" + statisticFields + "]";
    }

    private void sortFieldListAndCachePositions() {
        // de-dup the list
        Set<StatisticField> tempSet = new HashSet<>(statisticFields);
        statisticFields.clear();
        statisticFields.addAll(tempSet);
        tempSet = null;

        Collections.sort(statisticFields);

        fieldPositionMap.clear();
        int i = 0;
        for (final StatisticField field : statisticFields) {
            fieldPositionMap.put(field.getFieldName(), i++);
        }
    }

    public StatisticsDataSourceData deepCopy() {
        final List<StatisticField> newFieldList = new ArrayList<>();

        for (final StatisticField statisticField : statisticFields) {
            newFieldList.add(statisticField.deepCopy());
        }

        final Set<CustomRollUpMask> newMaskList = new HashSet<>();

        for (final CustomRollUpMask customRollUpMask : customRollUpMasks) {
            newMaskList.add(customRollUpMask.deepCopy());
        }

        return new StatisticsDataSourceData(newFieldList, newMaskList);
    }

    /**
     * Added to ensure map is not made final which would break GWT
     * serialisation.
     */
    public void setFieldPositionMap(final Map<String, Integer> fieldPositionMap) {
        this.fieldPositionMap = fieldPositionMap;
    }
}
