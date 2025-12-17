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

package stroom.analytics.impl;

import stroom.analytics.shared.AbstractAnalyticRuleDoc;
import stroom.analytics.shared.ExecutionSchedule;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.query.api.Column;
import stroom.query.api.DateTimeSettings;
import stroom.query.common.v2.CompiledColumn;
import stroom.query.common.v2.CompiledColumns;
import stroom.query.common.v2.format.ColumnFormatter;
import stroom.query.common.v2.format.FormatterFactory;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Generator;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValuesConsumer;
import stroom.query.language.functions.ref.StoredValues;
import stroom.search.extraction.ProcessLifecycleAware;
import stroom.util.date.DateUtil;
import stroom.util.shared.ElementId;
import stroom.util.shared.NullSafe;
import stroom.util.shared.Severity;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

public class DetectionConsumerProxy implements ValuesConsumer, ProcessLifecycleAware {

    private static final Logger LOGGER = LoggerFactory.getLogger(DetectionConsumerProxy.class);

    private final Provider<ErrorReceiverProxy> errorReceiverProxyProvider;
    private final ColumnFormatter fieldFormatter;
    private Provider<DetectionConsumer> detectionsConsumerProvider;

    private DetectionConsumer detectionConsumer;

    private AbstractAnalyticRuleDoc analyticRuleDoc;

    private CompiledColumns compiledColumns;

    private ExecutionSchedule executionSchedule;
    private Instant executionTime;
    private Instant effectiveExecutionTime;
    private Predicate<Val[]> valFilter;

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

    public void setAnalyticRuleDoc(final AbstractAnalyticRuleDoc analyticRuleDoc) {
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

    public void setValFilter(final Predicate<Val[]> valFilter) {
        this.valFilter = valFilter;
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

        if (valFilter.test(values)) {
            final ColumnValue[] outputValues = extractValues(values);
            writeRecord(outputValues);
        }
    }

    private ColumnValue[] extractValues(final Val[] vals) {
        final CompiledColumn[] compiledColumnArray = compiledColumns.getCompiledColumns();
        final StoredValues storedValues = compiledColumns.getValueReferenceIndex().createStoredValues();
        final ColumnValue[] output = new ColumnValue[compiledColumnArray.length];
        int index = 0;

        for (final CompiledColumn compiledColumn : compiledColumnArray) {
            final Generator generator = compiledColumn.getGenerator();

            if (generator != null) {
                generator.set(vals, storedValues);
                final Val value = generator.eval(storedValues, null);
                output[index] = new ColumnValue(compiledColumn.getColumn(), value);
            }

            index++;
        }

        return output;
    }

    private void log(final Severity severity, final String message, final Exception e) {
        LOGGER.error(message, e);
        errorReceiverProxyProvider.get().log(severity, null,
                new ElementId(getClass().getSimpleName()), message, e);
    }

    private void writeRecord(final ColumnValue[] columnValues) {
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
        for (final ColumnValue columnValue : columnValues) {
            NullSafe.consume(columnValue, ColumnValue::val, val -> {
                final Column column = columnValue.column();
                final String columnName = column.getName();
                if (FieldIndex.isStreamIdFieldName(columnName)) {
                    streamId.set(getSafeLong(val));
                } else if (FieldIndex.isEventIdFieldName(columnName)) {
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

    public static Long getSafeLong(final Val value) {
        try {
            return value.toLong();
        } catch (final RuntimeException e) {
            LOGGER.debug(e.getMessage(), e);
        }
        return null;
    }

    private record ColumnValue(Column column, Val val) {

    }
}
