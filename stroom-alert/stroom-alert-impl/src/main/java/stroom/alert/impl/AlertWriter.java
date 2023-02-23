package stroom.alert.impl;

import stroom.alert.api.AlertDefinition;
import stroom.alert.api.AlertManager;
import stroom.alert.impl.RecordConsumer.Data;
import stroom.alert.impl.RecordConsumer.Record;
import stroom.dashboard.expression.v1.Expression;
import stroom.dashboard.expression.v1.FieldIndex;
import stroom.dashboard.expression.v1.Generator;
import stroom.dashboard.expression.v1.Val;
import stroom.dashboard.expression.v1.ValuesConsumer;
import stroom.index.shared.IndexConstants;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.query.api.v2.DateTimeSettings;
import stroom.query.api.v2.Field;
import stroom.query.api.v2.TableSettings;
import stroom.query.common.v2.CompiledField;
import stroom.query.common.v2.CompiledFields;
import stroom.query.common.v2.format.FieldFormatter;
import stroom.query.common.v2.format.FormatterFactory;
import stroom.util.date.DateUtil;
import stroom.util.shared.Severity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;

public class AlertWriter implements ValuesConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(AlertWriter.class);

    private final ErrorReceiverProxy errorReceiverProxy;
    private final FieldFormatter fieldFormatter;
    private final String additionalFieldsPrefix;
    private final boolean outputIndexFields;

    private List<AlertDefinition> alertDefinitions;
    private Map<String, String> paramMapForAlerting;
    private FieldIndex fieldIndex;
    private RecordConsumer recordConsumer;

    @Inject
    public AlertWriter(final ErrorReceiverProxy errorReceiverProxy,
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

    public void setAlertDefinitions(final List<AlertDefinition> alertDefinitions) {
        this.alertDefinitions = alertDefinitions;
    }

    public void setParamMapForAlerting(final Map<String, String> paramMapForAlerting) {
        this.paramMapForAlerting = paramMapForAlerting;
    }

    public void setFieldIndex(final FieldIndex fieldIndex) {
        this.fieldIndex = fieldIndex;
    }

    public void setRecordConsumer(final RecordConsumer recordConsumer) {
        this.recordConsumer = recordConsumer;
    }

    @Override
    public void add(final Val[] values) {
        // Alert generation search extraction - create records when filters match
        if (values == null || values.length == 0) {
            log(Severity.WARNING, "No values to extract from ", null);
            return;
        }
        for (final AlertDefinition alertDefinition : alertDefinitions) {
            final CompiledFieldValue[] outputFields = extractAlert(alertDefinition, values);
            if (outputFields != null) {
                writeRecord(alertDefinition, outputFields);
            }
        }
    }

    private CompiledFieldValue[] extractAlert(final AlertDefinition rule, final Val[] vals) {
        final TableSettings tableSettings = rule.getTableSettings();
        final List<Field> fields = tableSettings.getFields();
        final CompiledField[] compiledFields = CompiledFields.create(fields, fieldIndex,
                paramMapForAlerting);

        final CompiledFieldValue[] output = new CompiledFieldValue[compiledFields.length];
        int index = 0;

        for (final CompiledField compiledField : compiledFields) {
            final Expression expression = compiledField.getExpression();

            if (expression != null) {
                if (expression.hasAggregate()) {
                    LOGGER.error("Rules error: Dashboard " +
                            rule.getAttributes().getOrDefault(AlertManager.DASHBOARD_NAME_KEY, "Unknown")
                            + " at " + rule.getAttributes().getOrDefault(AlertManager.RULES_FOLDER_KEY,
                            "Unknown location")
                            + " contains aggregate functions.  This is not supported.");
                    rule.setDisabled(true);
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

    private void writeRecord(final AlertDefinition alertDefinition,
                             final CompiledFieldValue[] fieldVals) {
        if (fieldVals == null || fieldVals.length == 0) {
            return;
        }

        if (fieldVals.length != alertDefinition.getTableSettings().getFields().size()) {
            log(Severity.ERROR, "Incorrect number of fields extracted for alert! " +
                    "Need " + alertDefinition.getTableSettings().getFields().size() +
                    " but got " + fieldVals.length, null);
            return;
        }

        final List<Data> rows = new ArrayList<>();
        rows.add(new Data(AlertManager.DETECT_TIME_DATA_ELEMENT_NAME_ATTR, DateUtil.createNormalDateTimeString()));
        for (String attrName : alertDefinition.getAttributes().keySet()) {
            rows.add(new Data(attrName, alertDefinition.getAttributes().get(attrName)));
        }

        // Output all the dashboard fields
        Set<String> skipFields = new HashSet<>();
        int index = 0;
        for (final Field field : alertDefinition.getTableSettings().getFields()) {
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
