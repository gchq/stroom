package stroom.analytics.impl;

import stroom.analytics.impl.DetectionConsumer.Detection;
import stroom.analytics.impl.DetectionConsumer.LinkedEvent;
import stroom.analytics.impl.DetectionConsumer.Value;
import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.dashboard.expression.v1.FieldIndex;
import stroom.dashboard.expression.v1.Generator;
import stroom.dashboard.expression.v1.Val;
import stroom.dashboard.expression.v1.ValuesConsumer;
import stroom.dashboard.expression.v1.ref.StoredValues;
import stroom.docref.DocRef;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.query.api.v2.DateTimeSettings;
import stroom.query.common.v2.CompiledField;
import stroom.query.common.v2.CompiledFields;
import stroom.query.common.v2.format.FieldFormatter;
import stroom.query.common.v2.format.FormatterFactory;
import stroom.util.shared.Severity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Inject;
import javax.inject.Provider;

public class DetectionWriterProxy implements ValuesConsumer, ProcessLifecycleAware {

    private static final Logger LOGGER = LoggerFactory.getLogger(DetectionWriterProxy.class);

    private final Provider<ErrorReceiverProxy> errorReceiverProxyProvider;
    private final FieldFormatter fieldFormatter;
    private final Provider<DetectionsWriter> detectionsWriterProvider;
    private DocRef destinationFeed;
//    private final String additionalFieldsPrefix;
//    private final boolean outputIndexFields;

    private FieldIndex fieldIndex;
    private DetectionConsumer detectionConsumer;

    private AnalyticRuleDoc analyticRuleDoc;

    private CompiledFields compiledFields;

    @Inject
    public DetectionWriterProxy(final Provider<ErrorReceiverProxy> errorReceiverProxyProvider,
                                final AlertConfig alertConfig,
                                final Provider<DetectionsWriter> detectionsWriterProvider) {
        this.errorReceiverProxyProvider = errorReceiverProxyProvider;
        this.detectionsWriterProvider = detectionsWriterProvider;

        final DateTimeSettings dateTimeSettings = DateTimeSettings
                .builder()
                .localZoneId(alertConfig.getTimezone())
                .build();
        fieldFormatter = new FieldFormatter(new FormatterFactory(dateTimeSettings));
    }

    private DetectionConsumer getDetectionConsumer() {
        if (detectionConsumer == null) {
            final DetectionsWriter detectionsWriter = detectionsWriterProvider.get();
            detectionsWriter.setFeed(destinationFeed);
            detectionConsumer = detectionsWriter;
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
        final DetectionConsumer detectionConsumer = getDetectionConsumer();
        detectionConsumer.end();
    }

    public void setAnalyticRuleDoc(final AnalyticRuleDoc analyticRuleDoc) {
        this.analyticRuleDoc = analyticRuleDoc;
    }

    public void setCompiledFields(final CompiledFields compiledFields) {
        this.compiledFields = compiledFields;
    }

    public void setFieldIndex(final FieldIndex fieldIndex) {
        this.fieldIndex = fieldIndex;
    }

    public void setDestinationFeed(final DocRef destinationFeed) {
        this.destinationFeed = destinationFeed;
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
        final CompiledFieldValue[] outputFields = extractAlert(values);
        if (outputFields != null) {
            writeRecord(outputFields);
        }
    }

    private CompiledFieldValue[] extractAlert(final Val[] vals) {
        final CompiledField[] compiledFieldArray = compiledFields.getCompiledFields();
        final StoredValues storedValues = compiledFields.getValueReferenceIndex().createStoredValues();
        final CompiledFieldValue[] output = new CompiledFieldValue[compiledFieldArray.length];
        int index = 0;

        for (final CompiledField compiledField : compiledFieldArray) {
            final Generator generator = compiledField.getGenerator();

            if (generator != null) {
                if (compiledField.hasAggregate()) {
                    LOGGER.error("Rules error: Query " +
                            analyticRuleDoc.getUuid() +
                            " contains aggregate functions." +
                            " This is not supported for Event Type Rules.");
                    return null;
                } else {
                    generator.set(vals, storedValues);
                    final Val value = generator.eval(storedValues, null);
                    output[index] = new CompiledFieldValue(compiledField, value);

                    if (compiledField.getCompiledFilter() != null) {
                        // If we are filtering then we need to evaluate this field
                        // now so that we can filter the resultant value.

                        if (compiledField.getCompiledFilter() != null && value != null
                                && !compiledField.getCompiledFilter().match(value.toString())) {
                            // We want to exclude this item.
                            return null;
                        }
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

    private void writeRecord(final CompiledFieldValue[] fieldVals) {
//        final CompiledField[] compiledFieldArray = compiledFields.getCompiledFields();
        if (fieldVals == null || fieldVals.length == 0) {
            return;
        }

        final List<Value> values = new ArrayList<>();
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
        fieldIndex.forEach((idx, fieldName) -> {
//            if (skipFields.contains(fieldName)) {
            final CompiledFieldValue compiledFieldValue = fieldVals[idx];
            if (compiledFieldValue != null && compiledFieldValue.getVal() != null) {
                if (fieldIndex.getStreamIdFieldIndex() == idx) {
                    try {
                        streamId.set(compiledFieldValue.getVal().toLong());
                    } catch (final RuntimeException e) {
                        LOGGER.debug(e.getMessage(), e);
                    }
                } else if (fieldIndex.getEventIdFieldIndex() == idx) {
                    try {
                        eventId.set(compiledFieldValue.getVal().toLong());
                    } catch (final RuntimeException e) {
                        LOGGER.debug(e.getMessage(), e);
                    }
                } else {
                    final String fieldValStr =
                            fieldFormatter.format(compiledFieldValue.getCompiledField().getField(),
                                    compiledFieldValue.getVal());
                    values.add(new Value(fieldName, fieldValStr));
                }
            }
//            }
        });

        final Detection detection = new Detection(
                Instant.now(),
                analyticRuleDoc.getName(),
                analyticRuleDoc.getUuid(),
                analyticRuleDoc.getVersion(),
                null,
                null,
                analyticRuleDoc.getDescription(),
                null,
                UUID.randomUUID().toString(),
                0,
                false,
                values,
                List.of(new LinkedEvent(null, streamId.get(), eventId.get()))
        );

        final DetectionConsumer detectionConsumer = getDetectionConsumer();
        detectionConsumer.accept(detection);
    }

    private static class CompiledFieldValue {

        private final CompiledField compiledField;
        private final Val val;

        public CompiledFieldValue(final CompiledField compiledField, final Val val) {
            this.compiledField = compiledField;
            this.val = val;
        }

        public CompiledField getCompiledField() {
            return compiledField;
        }

        public Val getVal() {
            return val;
        }
    }
}
