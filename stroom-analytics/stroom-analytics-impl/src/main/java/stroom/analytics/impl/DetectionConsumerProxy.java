/*
 * Copyright 2024 Crown Copyright
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

package stroom.analytics.impl;

import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.analytics.shared.ExecutionSchedule;
import stroom.expression.api.DateTimeSettings;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.query.api.v2.Column;
import stroom.query.common.v2.CompiledColumn;
import stroom.query.common.v2.CompiledColumns;
import stroom.query.common.v2.format.ColumnFormatter;
import stroom.query.common.v2.format.FormatterFactory;
import stroom.query.language.functions.Generator;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValuesConsumer;
import stroom.query.language.functions.ref.StoredValues;
import stroom.search.extraction.ProcessLifecycleAware;
import stroom.util.NullSafe;
import stroom.util.date.DateUtil;
import stroom.util.shared.Severity;
import stroom.util.shared.query.FieldNames;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class DetectionConsumerProxy implements ValuesConsumer, ProcessLifecycleAware {

    private static final Logger LOGGER = LoggerFactory.getLogger(DetectionConsumerProxy.class);

    private final Provider<ErrorReceiverProxy> errorReceiverProxyProvider;
    private final ColumnFormatter fieldFormatter;
    private Provider<DetectionConsumer> detectionsConsumerProvider;

    private DetectionConsumer detectionConsumer;

    private AnalyticRuleDoc analyticRuleDoc;

    private CompiledColumns compiledColumns;

    private ExecutionSchedule executionSchedule;
    private Instant executionTime;
    private Instant effectiveExecutionTime;

    @Inject
    public DetectionConsumerProxy(final Provider<ErrorReceiverProxy> errorReceiverProxyProvider,
                                  final AnalyticsConfig analyticsConfig) {
        this.errorReceiverProxyProvider = errorReceiverProxyProvider;
        final DateTimeSettings dateTimeSettings = DateTimeSettings
                .builder()
                .localZoneId(analyticsConfig.getTimezone())
                .build();
        fieldFormatter = new ColumnFormatter(new FormatterFactory(dateTimeSettings));
    }

    public void setDetectionsConsumerProvider(final Provider<DetectionConsumer> detectionsConsumerProvider) {
        this.detectionsConsumerProvider = detectionsConsumerProvider;
    }

    public DetectionConsumer getDetectionConsumer() {
        if (detectionConsumer == null) {
            if (detectionsConsumerProvider == null) {
                LOGGER.error("Detection consumer is null");
                throw new NullPointerException();
            }
            detectionConsumer = detectionsConsumerProvider.get();
        }
        return detectionConsumer;
    }

    @Override
    public void start() {
        final DetectionConsumer detectionConsumer = getDetectionConsumer();
        detectionConsumer.start();
    }

    @Override
    public void end() {
        if (detectionConsumer != null) {
            detectionConsumer.end();
        }
    }

    public void setAnalyticRuleDoc(final AnalyticRuleDoc analyticRuleDoc) {
        this.analyticRuleDoc = analyticRuleDoc;
    }

    public void setExecutionSchedule(final ExecutionSchedule executionSchedule) {
        this.executionSchedule = executionSchedule;
    }

    public void setExecutionTime(final Instant executionTime) {
        this.executionTime = executionTime;
    }

    public void setEffectiveExecutionTime(final Instant effectiveExecutionTime) {
        this.effectiveExecutionTime = effectiveExecutionTime;
    }

    public void setCompiledColumns(final CompiledColumns compiledColumns) {
        this.compiledColumns = compiledColumns;
    }

    @Override
    public void accept(final Val[] values) {
        // Analytics generation search extraction - create records when filters match
        if (values == null || values.length == 0) {
            log(Severity.WARNING, "Rules error: Query " +
                    analyticRuleDoc.getUuid() +
                    ". No values to extract from ", null);
            return;
        }
        final CompiledColumnValue[] outputValues = extractValues(values);
        if (outputValues != null) {
            writeRecord(outputValues);
        }
    }

    private CompiledColumnValue[] extractValues(final Val[] vals) {
        final CompiledColumn[] compiledColumnArray = compiledColumns.getCompiledColumns();
        final StoredValues storedValues = compiledColumns.getValueReferenceIndex().createStoredValues();
        final CompiledColumnValue[] output = new CompiledColumnValue[compiledColumnArray.length];
        int index = 0;

        for (final CompiledColumn compiledColumn : compiledColumnArray) {
            final Generator generator = compiledColumn.getGenerator();

            if (generator != null) {
                generator.set(vals, storedValues);
                final Val value = generator.eval(storedValues, null);
                output[index] = new CompiledColumnValue(compiledColumn, value);

                if (compiledColumn.getCompiledFilter() != null) {
                    // If we are filtering then we need to evaluate this field
                    // now so that we can filter the resultant value.

                    if (compiledColumn.getCompiledFilter() != null && value != null
                            && !compiledColumn.getCompiledFilter().match(value.toString())) {
                        // We want to exclude this item.
                        return null;
                    }
                }
            }

            index++;
        }

        return output;
    }

    private void log(final Severity severity, final String message, final Exception e) {
        LOGGER.error(message, e);
        errorReceiverProxyProvider.get().log(severity, null,
                "AlertExtractionReceiver", message, e);
    }

    private void writeRecord(final CompiledColumnValue[] columnValues) {
//        final CompiledField[] compiledFieldArray = compiledFields.getCompiledFields();
        if (columnValues == null || columnValues.length == 0) {
            return;
        }

        final List<DetectionValue> values = new ArrayList<>();
//        final List<LinkedEvent> linkedEvents = new ArrayList<>();
//
//        // Output all the dashboard fields
//        Set<String> skipFields = new HashSet<>();
//        int index = 0;
//        for (final CompiledField compiledField : compiledFieldArray) {
//            final Field field = compiledField.getField();
//            if (field.isVisible()) {
//                final String fieldName = field.getDisplayValue();
//                final CompiledFieldValue compiledFieldValue = fieldVals[index];
//                final Val fieldVal = compiledFieldValue.getVal();
//
//                // Remember this field so not to output again
//                skipFields.add(fieldName);
//
//                if (fieldVal != null) {
//                    final String fieldValStr =
//                            fieldFormatter.format(compiledFieldValue.getCompiledField().getField(), fieldVal);
//                    values.add(new Value(fieldName, fieldValStr));
//                }
//            }
//            index++;
//        }

        // Output standard index fields
        final AtomicReference<Long> streamId = new AtomicReference<>();
        final AtomicReference<Long> eventId = new AtomicReference<>();

        // fieldIndex may differ in size to columnValues if the query uses column but does not
        // select them, e.g.
        //   eval compound=field1+field2
        //   select compound
        for (final CompiledColumnValue columnValue : columnValues) {
            NullSafe.consume(columnValue, CompiledColumnValue::getVal, val -> {
                final CompiledColumn compiledColumn = columnValue.getCompiledColumn();
                final Column column = compiledColumn.getColumn();
                final String columnName = column.getName();
                if (FieldNames.isStreamIdFieldName(columnName)) {
                    streamId.set(getSafeLong(val));
                } else if (FieldNames.isEventIdFieldName(columnName)) {
                    eventId.set(getSafeLong(val));
                } else {
                    final String fieldValStr = fieldFormatter.format(column, val);
                    values.add(new DetectionValue(columnName, fieldValStr));
                }
            });
        }

        final List<DetectionLinkedEvent> linkedEvents =
                List.of(new DetectionLinkedEvent(null, streamId.get(), eventId.get()));
        final Detection detection = Detection
                .builder()
                .withDetectTime(DateUtil.createNormalDateTimeString())
                .withDetectorName(analyticRuleDoc.getName())
                .withDetectorUuid(analyticRuleDoc.getUuid())
                .withDetectorVersion(analyticRuleDoc.getVersion())
                .withDetailedDescription(analyticRuleDoc.getDescription())
                .withRandomDetectionUniqueId()
                .withDetectionRevision(0)
                .withExecutionSchedule(NullSafe
                        .get(executionSchedule, ExecutionSchedule::getName))
                .withExecutionTime(executionTime)
                .withEffectiveExecutionTime(effectiveExecutionTime)
                .notDefunct()
                .withValues(values)
                .withLinkedEvents(linkedEvents)
                .build();

        final DetectionConsumer detectionConsumer = getDetectionConsumer();
        detectionConsumer.accept(detection);
    }

    public static Long getSafeLong(final String value) {
        try {
            return Long.parseLong(value);
        } catch (final RuntimeException e) {
            LOGGER.debug(e.getMessage(), e);
        }
        return null;
    }

    public static Long getSafeLong(final Val value) {
        try {
            return value.toLong();
        } catch (final RuntimeException e) {
            LOGGER.debug(e.getMessage(), e);
        }
        return null;
    }

    private static class CompiledColumnValue {

        private final CompiledColumn compiledColumn;
        private final Val val;

        public CompiledColumnValue(final CompiledColumn compiledColumn, final Val val) {
            this.compiledColumn = compiledColumn;
            this.val = val;
        }

        public CompiledColumn getCompiledColumn() {
            return compiledColumn;
        }

        public Val getVal() {
            return val;
        }
    }
}
