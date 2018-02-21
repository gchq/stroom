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

package stroom.statistics.sql.search;

import stroom.statistics.shared.StatisticType;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.stream.Stream;

public class StatisticDataSet implements Iterable<StatisticDataPoint> {
    private final String statisticName;
    private final StatisticType statisticType;
    private final Set<StatisticDataPoint> statisticDataPoints;

    public StatisticDataSet(final String statisticName, final StatisticType statisticType) {
        this.statisticName = statisticName;
        this.statisticType = statisticType;
        this.statisticDataPoints = new HashSet<>();
    }

    public StatisticDataSet(final String statisticName, final StatisticType statisticType, final long precisionMs) {
        this.statisticName = statisticName;
        this.statisticType = statisticType;
        this.statisticDataPoints = new HashSet<>();
    }

    public StatisticDataSet(final String statisticName, final StatisticType statisticType, final long precisionMs,
                            final Set<StatisticDataPoint> statisticDataPoints) {
        for (StatisticDataPoint dataPoint : statisticDataPoints) {
            if (!statisticType.equals(dataPoint.getStatisticType())) {
                throw new RuntimeException(
                        "Attempting to create a StatisticDataSet with StatisticDataPoints of an incompatible StatisticType");
            }
        }

        this.statisticName = statisticName;
        this.statisticType = statisticType;
        this.statisticDataPoints = statisticDataPoints;
    }

    public void addDataPoint(StatisticDataPoint dataPoint) {
        if (!statisticType.equals(dataPoint.getStatisticType())) {
            throw new RuntimeException("Attempting to add a StatisticDataPoint of an incompatible StatisticType");
        }

        this.statisticDataPoints.add(dataPoint);
    }

    public String getStatisticName() {
        return statisticName;
    }

    public StatisticType getStatisticType() {
        return statisticType;
    }

    // public long getPrecisionMs() {
    // return precisionMs;
    // }

    public Set<StatisticDataPoint> getStatisticDataPoints() {
        return statisticDataPoints;
    }

    public int size() {
        return statisticDataPoints.size();
    }

    public boolean isEmpty() {
        return statisticDataPoints.isEmpty();
    }

    @Override
    public Iterator<StatisticDataPoint> iterator() {
        return statisticDataPoints.iterator();
    }

    public Stream<StatisticDataPoint> stream() {
        return statisticDataPoints.stream();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;

        result = prime * result + ((statisticDataPoints == null) ? 0 : statisticDataPoints.hashCode());
        result = prime * result + ((statisticName == null) ? 0 : statisticName.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        StatisticDataSet other = (StatisticDataSet) obj;
        if (statisticDataPoints == null) {
            if (other.statisticDataPoints != null)
                return false;
        } else if (!statisticDataPoints.equals(other.statisticDataPoints))
            return false;
        if (statisticName == null) {
            if (other.statisticName != null)
                return false;
        } else if (!statisticName.equals(other.statisticName))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "StatisticDataSet [statisticName=" + statisticName + ", statisticType=" + statisticType
                + ", statisticDataPoints size=" + statisticDataPoints.size() + "]";
    }

}
