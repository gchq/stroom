package stroom.search.extraction;

import stroom.index.shared.IndexField;
import stroom.query.common.v2.StringFieldValue;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValBoolean;
import stroom.query.language.functions.ValDate;
import stroom.query.language.functions.ValDouble;
import stroom.query.language.functions.ValFloat;
import stroom.query.language.functions.ValInteger;
import stroom.query.language.functions.ValLong;
import stroom.query.language.functions.ValString;
import stroom.util.date.DateUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class FieldValueExtractor {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FieldValueExtractor.class);

    private final FieldIndex fieldIndex;
    private final IndexField[] indexFields;

    FieldValueExtractor(final FieldIndex fieldIndex,
                        final IndexStructure indexStructure) {
        this.fieldIndex = fieldIndex;
        indexFields = new IndexField[fieldIndex.size()];

        // Populate the index field map with the expected fields.
        for (final String fieldName : fieldIndex.getFields()) {
            final int pos = fieldIndex.getPos(fieldName);
            IndexField indexField = null;

            if (indexStructure != null &&
                    indexStructure.getIndexFieldsMap() != null) {
                indexField = indexStructure.getIndexFieldsMap().get(fieldName);
            }

            if (indexField == null) {
                indexField = IndexField
                        .builder()
                        .fieldName(fieldName)
                        .indexed(false)
                        .build();
            }

            indexFields[pos] = indexField;
        }
    }

    public FieldValue convert(final int pos, final String stringValue) {
        final IndexField indexField = indexFields[pos];
        final Val val = convertValue(indexField, stringValue);
        return new FieldValue(indexField, val);
    }

    public List<FieldValue> convert(final List<StringFieldValue> stringValues) {
        final List<FieldValue> fieldValues = new ArrayList<>(indexFields.length);
        for (final StringFieldValue stringFieldValue : stringValues) {
            final Integer pos = fieldIndex.getPos(stringFieldValue.fieldName());
            if (pos != null) {
                final IndexField indexField = indexFields[pos];
                final Val val = convertValue(indexField, stringFieldValue.fieldValue());
                fieldValues.add(new FieldValue(indexField, val));
            }
        }
        return fieldValues;
    }

    private Val convertValue(final IndexField indexField, final String value) {
        try {
            switch (indexField.getType()) {
                case LONG, ID -> {
                    final long val = Long.parseLong(value);
                    return ValLong.create(val);
                }
                case BOOLEAN -> {
                    final boolean val = Boolean.parseBoolean(value);
                    return ValBoolean.create(val);
                }
                case INTEGER -> {
                    final int val = Integer.parseInt(value);
                    return ValInteger.create(val);
                }
                case FLOAT -> {
                    final float val = Float.parseFloat(value);
                    return ValFloat.create(val);
                }
                case DOUBLE -> {
                    final double val = Double.parseDouble(value);
                    return ValDouble.create(val);
                }
                case DATE -> {
                    final long val = DateUtil.parseNormalDateTimeString(value);
                    return ValDate.create(val);
                }
                case TEXT -> {
                    return ValString.create(value);
                }
            }
        } catch (final RuntimeException e) {
            LOGGER.debug(e::getMessage, e);
        }
        return null;
    }
}
