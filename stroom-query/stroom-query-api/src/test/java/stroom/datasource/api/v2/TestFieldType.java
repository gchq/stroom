package stroom.datasource.api.v2;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestFieldType {

    @Test
    void testFromTypeId() {
        for (final FieldType fieldType : FieldType.values()) {
            final int typeId = fieldType.getTypeId();

            final FieldType fieldType2 = FieldType.fromTypeId(typeId);

            assertThat(fieldType2)
                    .isEqualTo(fieldType);
        }
    }

    @Test
    void testFromName() {
        for (final FieldType fieldType : FieldType.values()) {
            final String name = fieldType.getTypeName();
            final String displayValue = fieldType.getDisplayValue();

            assertThat(displayValue)
                    .isEqualTo(name);

            final FieldType fieldType2 = FieldType.fromDisplayValue(displayValue);

            assertThat(fieldType2)
                    .isEqualTo(fieldType);

            final String displayValueUpperCase = displayValue.toUpperCase();
            assertThat(displayValueUpperCase)
                    .isNotEqualTo(displayValue);

            final FieldType fieldType3 = FieldType.fromDisplayValue(displayValueUpperCase);

            assertThat(fieldType3)
                    .isEqualTo(fieldType);
        }
    }
}
