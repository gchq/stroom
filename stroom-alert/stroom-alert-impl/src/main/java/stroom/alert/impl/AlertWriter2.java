package stroom.alert.impl;

import stroom.alert.api.AlertManager;
import stroom.alert.impl.RecordConsumer.Data;
import stroom.alert.impl.RecordConsumer.Record;
import stroom.alert.rule.shared.AlertRuleDoc;
import stroom.dashboard.expression.v1.Expression;
import stroom.dashboard.expression.v1.FieldIndex;
import stroom.dashboard.expression.v1.Generator;
import stroom.dashboard.expression.v1.Val;
import stroom.dashboard.expression.v1.Values;
import stroom.dashboard.expression.v1.ValuesConsumer;
import stroom.index.shared.IndexConstants;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.query.api.v2.DateTimeSettings;
import stroom.query.api.v2.Field;
import stroom.query.common.v2.CompiledField;
import stroom.query.common.v2.format.FieldFormatter;
import stroom.query.common.v2.format.FormatterFactory;
import stroom.util.date.DateUtil;
import stroom.util.shared.Severity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;

public class AlertWriter2 implements ValuesConsumer, ProcessLifecycleAware {

    private static final Logger LOGGER = LoggerFactory.getLogger(AlertWriter2.class);

    private final ErrorReceiverProxy errorReceiverProxy;
    private final FieldFormatter fieldFormatter;
    private final String additionalFieldsPrefix;
    private final boolean outputIndexFields;

    private FieldIndex fieldIndex;
    private RecordConsumer recordConsumer;

    private AlertRuleDoc alertRuleDoc;

    private CompiledField[] compiledFields;

    @Inject
    public AlertWriter2(final ErrorReceiverProxy errorReceiverProxy,
                        final AlertConfig alertConfig) {
        this.errorReceiverProxy = errorReceiverProxy;
        additionalFieldsPrefix = alertConfig.getAdditionalFieldsPrefix() != null
                ?
                alertConfig.getAdditionalFieldsPrefix()
                : "";
        outputIndexFields = alertConfig.isReportAllExtractedFieldsEnabled();

        final DateTimeSettings dateTimeSettings = DateTimeSettings
                .builder()
                .localZoneId(alertConfig.getTimezone())
                .build();
        fieldFormatter = new FieldFormatter(new FormatterFactory(dateTimeSettings));
    }

    @Override
    public void start() {
        if (recordConsumer instanceof ProcessLifecycleAware) {
            ((ProcessLifecycleAware) recordConsumer).start();
        }
    }

    @Override
    public void end() {
        if (recordConsumer instanceof ProcessLifecycleAware) {
            ((ProcessLifecycleAware) recordConsumer).end();
        }
    }

    public void setAlertRuleDoc(final AlertRuleDoc alertRuleDoc) {
        this.alertRuleDoc = alertRuleDoc;
    }

    public void setCompiledFields(final CompiledField[] compiledFields) {
        this.compiledFields = compiledFields;
    }

    public void setFieldIndex(final FieldIndex fieldIndex) {
        this.fieldIndex = fieldIndex;
    }

    public void setRecordConsumer(final RecordConsumer recordConsumer) {
        this.recordConsumer = recordConsumer;
    }

    @Override
    public void add(final Values values) {
        // Alert generation search extraction - create records when filters match
        if (values == null || values.size() == 0) {
            log(Severity.WARNING, "No values to extract from ", null);
            return;
        }
        final CompiledFieldValue[] outputFields = extractAlert(values);
        if (outputFields != null) {
            writeRecord(outputFields);
        }
    }

    private CompiledFieldValue[] extractAlert(final Values vals) {
        final CompiledFieldValue[] output = new CompiledFieldValue[compiledFields.length];
        int index = 0;

        for (final CompiledField compiledField : compiledFields) {
            final Expression expression = compiledField.getExpression();

            if (expression != null) {
                if (expression.hasAggregate()) {
                    LOGGER.error("Rules error: Query contains aggregate functions.  This is not supported.");
                    return null;
                } else {
                    final Generator generator = expression.createGenerator();

                    generator.set(vals);
                    Val value = generator.eval(null);
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
        errorReceiverProxy.log(severity, null,
                "AlertExtractionReceiver", message, e);
    }

    private void writeRecord(final CompiledFieldValue[] fieldVals) {
        if (fieldVals == null || fieldVals.length == 0) {
            return;
        }

        final List<Data> rows = new ArrayList<>();
        rows.add(new Data(AlertManager.DETECT_TIME_DATA_ELEMENT_NAME_ATTR, DateUtil.createNormalDateTimeString()));
        rows.add(new Data("alertRuleName", alertRuleDoc.getName()));
        rows.add(new Data("alertRuleUuid", alertRuleDoc.getUuid()));

        // Output all the dashboard fields
        Set<String> skipFields = new HashSet<>();
        int index = 0;
        for (final CompiledField compiledField : compiledFields) {
            final Field field = compiledField.getField();
            if (field.isVisible()) {
                final String fieldName = field.getDisplayValue();
                final CompiledFieldValue compiledFieldValue = fieldVals[index];
                final Val fieldVal = compiledFieldValue.getVal();

                // Remember this field so not to output again
                skipFields.add(fieldName);

                if (fieldVal != null) {
                    final String fieldValStr =
                            fieldFormatter.format(compiledFieldValue.getCompiledField().getField(), fieldVal);
                    rows.add(new Data(fieldName, fieldValStr));
                }
            }
            index++;
        }

        // Output standard index fields
        fieldIndex.forEach((idx, fieldName) -> {
            if (skipFields.contains(fieldName)) {
                String val = "Unknown";
                final CompiledFieldValue compiledFieldValue = fieldVals[idx];
                if (compiledFieldValue != null && compiledFieldValue.getVal() != null) {
                    val = compiledFieldValue.getVal().toString();
                }

                if (IndexConstants.STREAM_ID.equals(fieldName)) {
                    rows.add(new Data(AlertManager.STREAM_ID_DATA_ELEMENT_NAME_ATTR, val));
                } else if (IndexConstants.EVENT_ID.equals(fieldName)) {
                    rows.add(new Data(AlertManager.EVENT_ID_DATA_ELEMENT_NAME_ATTR, val));
                } else if (outputIndexFields && compiledFieldValue != null && compiledFieldValue.getVal() != null) {
                    final String fieldValStr =
                            fieldFormatter.format(compiledFieldValue.getCompiledField().getField(),
                                    compiledFieldValue.getVal());
                    rows.add(new Data(additionalFieldsPrefix + fieldName, fieldValStr));
                }
            }
        });

        recordConsumer.accept(new Record(rows));
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
