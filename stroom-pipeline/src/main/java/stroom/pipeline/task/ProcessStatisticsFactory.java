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

package stroom.pipeline.task;

import stroom.meta.api.AttributeMap;
import stroom.meta.shared.MetaFields;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.errorhandler.ErrorStatistics;
import stroom.pipeline.state.RecordCount;
import stroom.query.api.datasource.QueryField;
import stroom.util.shared.Severity;

import java.util.HashMap;
import java.util.Map;

public class ProcessStatisticsFactory {
    public static ProcessStatistics create(final RecordCount recordCount,
                                           final ErrorReceiverProxy errorReceiverProxy) {
        ErrorStatistics errorStatistics = null;
        if (errorReceiverProxy.getErrorReceiver() instanceof ErrorStatistics) {
            errorStatistics = (ErrorStatistics) errorReceiverProxy.getErrorReceiver();
        }

        final ProcessStatistics processStatistics = new ProcessStatistics();
        ProcessStatisticsFactory.addRecordCounts(processStatistics, recordCount);
        ProcessStatisticsFactory.addMarkerCounts(processStatistics, errorStatistics);

        return processStatistics;
    }

    private static void addRecordCounts(final ProcessStatistics stats,
                                        final RecordCount recordCount) {
        stats.map.put(MetaFields.REC_READ, recordCount.getRead());
        stats.map.put(MetaFields.REC_WRITE, recordCount.getWritten());
        stats.map.put(MetaFields.DURATION, recordCount.getDuration());
    }

    private static void addMarkerCounts(final ProcessStatistics stats,
                                        final ErrorStatistics errorStatistics) {
        stats.map.put(MetaFields.REC_INFO, getMarkerCount(errorStatistics, Severity.INFO));
        stats.map.put(MetaFields.REC_WARN, getMarkerCount(errorStatistics, Severity.WARNING));
        stats.map.put(MetaFields.REC_ERROR, getMarkerCount(errorStatistics, Severity.ERROR));
        stats.map.put(MetaFields.REC_FATAL, getMarkerCount(errorStatistics, Severity.FATAL_ERROR));
    }

    private static long getMarkerCount(final ErrorStatistics errorStatistics,
                                       final Severity... severity) {
        long count = 0;
        if (errorStatistics != null) {
            for (final Severity sev : severity) {
                count += errorStatistics.getRecords(sev);
            }
        }
        return count;
    }

    public static class ProcessStatistics {
        private final Map<QueryField, Long> map = new HashMap<>();

        public void write(final AttributeMap attributeMap) {
            map.forEach((k, v) -> attributeMap.put(k.getFldName(), String.valueOf(v)));
        }

        public ProcessStatistics add(final ProcessStatistics stats) {
            if (stats == null) {
                return this;
            }

            final ProcessStatistics result = new ProcessStatistics();
            result.map.putAll(map);
            stats.map.forEach((k, v) -> result.map.merge(k, v, (a, b) -> a + b));
            return result;
        }

        public ProcessStatistics subtract(final ProcessStatistics stats) {
            if (stats == null) {
                return this;
            }

            final ProcessStatistics result = new ProcessStatistics();
            result.map.putAll(map);
            stats.map.forEach((k, v) -> result.map.merge(k, v, (a, b) -> a - b));
            return result;
        }
    }
}
