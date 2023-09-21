package stroom.analytics.impl;

import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.expression.api.DateTimeSettings;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.query.common.v2.CompiledField;
import stroom.query.common.v2.CompiledFields;
import stroom.query.common.v2.format.FieldFormatter;
import stroom.query.common.v2.format.FormatterFactory;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Generator;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValuesConsumer;
import stroom.query.language.functions.ref.StoredValues;
import stroom.util.date.DateUtil;
import stroom.util.shared.Severity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Inject;
import javax.inject.Provider;

public class DetectionConsumerProxy implements ValuesConsumer, ProcessLifecycleAware {

    private static final Logger LOGGER = LoggerFactory.getLogger(DetectionConsumerProxy.class);

    private final Provider<ErrorReceiverProxy> errorReceiverProxyProvider;
    private final FieldFormatter fieldFormatter;
    private Provider<DetectionConsumer> detectionsConsumerProvider;

    private FieldIndex fieldIndex;
    private DetectionConsumer detectionConsumer;

    private AnalyticRuleDoc analyticRuleDoc;

    private CompiledFields compiledFields;

    @Inject
    public DetectionConsumerProxy(final Provider<ErrorReceiverProxy> errorReceiverProxyProvider,
                                  final AnalyticsConfig analyticsConfig) {
        this.errorReceiverProxyProvider = errorReceiverProxyProvider;
        final DateTimeSettings dateTimeSettings = DateTimeSettings
                .builder()
                .localZoneId(analyticsConfig.getTimezone())
                .build();
        fieldFormatter = new FieldFormatter(new FormatterFactory(dateTimeSettings));
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

    public void setCompiledFields(final CompiledFields compiledFields) {
        this.compiledFields = compiledFields;
    }

    public void setFieldIndex(final FieldIndex fieldIndex) {
        this.fieldIndex = fieldIndex;
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
        final CompiledFieldValue[] outputValues = extractValues(values);
        if (outputValues != null) {
            writeRecord(outputValues);
        }
    }

    private CompiledFieldValue[] extractValues(final Val[] vals) {
        final CompiledField[] compiledFieldArray = compiledFields.getCompiledFields();
        final StoredValues storedValues = compiledFields.getValueReferenceIndex().createStoredValues();
        final CompiledFieldValue[] output = new CompiledFieldValue[compiledFieldArray.length];
        int index = 0;

        for (final CompiledField compiledField : compiledFieldArray) {
            final Generator generator = compiledField.getGenerator();

            if (generator != null) {
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
        for (int pos = 0; pos < fieldIndex.size(); pos++) {
            final String fieldName = fieldIndex.getField(pos);
            if (fieldName != null) {
//            if (skipFields.contains(fieldName)) {
                final CompiledFieldValue compiledFieldValue = fieldVals[pos];
                if (compiledFieldValue != null && compiledFieldValue.getVal() != null) {
                    if (fieldIndex.getStreamIdFieldIndex() == pos) {
                        streamId.set(getSafeLong(compiledFieldValue.getVal()));
                    } else if (fieldIndex.getEventIdFieldIndex() == pos) {
                        eventId.set(getSafeLong(compiledFieldValue.getVal()));
                    } else {
                        final String fieldValStr =
                                fieldFormatter.format(compiledFieldValue.getCompiledField().getField(),
                                        compiledFieldValue.getVal());
                        values.add(new DetectionValue(fieldName, fieldValStr));
                    }
                }
            }
//            }
        }

        final Detection detection = new Detection(
                DateUtil.createNormalDateTimeString(),
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
                List.of(new DetectionLinkedEvent(null, streamId.get(), eventId.get()))
        );

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
