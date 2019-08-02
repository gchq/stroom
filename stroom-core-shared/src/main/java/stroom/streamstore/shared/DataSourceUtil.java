package stroom.streamstore.shared;

import stroom.datasource.api.v2.DataSourceField;
import stroom.datasource.api.v2.DataSourceField.DataSourceFieldType;
import stroom.query.api.v2.ExpressionTerm.Condition;

public class DataSourceUtil {
    public static DataSourceField createDateField(final String name) {
        return new DataSourceField.Builder()
                .name(name)
                .type(DataSourceField.DataSourceFieldType.DATE_FIELD)
                .addConditions(Condition.EQUALS)
                .addConditions(Condition.BETWEEN)
                .addConditions(Condition.GREATER_THAN)
                .addConditions(Condition.GREATER_THAN_OR_EQUAL_TO)
                .addConditions(Condition.LESS_THAN)
                .addConditions(Condition.LESS_THAN_OR_EQUAL_TO)
                .build();
    }

    public static DataSourceField createStringField(final String name) {
        return new DataSourceField.Builder()
                .name(name)
                .type(DataSourceField.DataSourceFieldType.FIELD)
                .addConditions(Condition.EQUALS)
                .addConditions(Condition.IN)
                .addConditions(Condition.IN_DICTIONARY)
                .build();
    }

    public static DataSourceField createIdField(final String name) {
        return new DataSourceField.Builder()
                .name(name)
                .type(DataSourceField.DataSourceFieldType.ID)
                .addConditions(Condition.EQUALS)
                .addConditions(Condition.IN)
                .build();
    }

    public static DataSourceField createDocRefField(final String name, final String docRefType) {
        return new DataSourceField.Builder()
                .name(name)
                .type(DataSourceFieldType.DOC_REF)
                .addConditions(Condition.IS_DOC_REF)
                .addConditions(Condition.EQUALS)
                .addConditions(Condition.IN)
                .addConditions(Condition.IN_DICTIONARY)
                .addConditions(Condition.IN_FOLDER)
                .docRefType(docRefType)
                .build();
    }

    public static DataSourceField createNumField(final String name) {
        return new DataSourceField.Builder()
                .name(name)
                .type(DataSourceFieldType.NUMERIC_FIELD)
                .addConditions(Condition.EQUALS)
                .addConditions(Condition.BETWEEN)
                .addConditions(Condition.GREATER_THAN)
                .addConditions(Condition.GREATER_THAN_OR_EQUAL_TO)
                .addConditions(Condition.LESS_THAN)
                .addConditions(Condition.LESS_THAN_OR_EQUAL_TO)
                .build();
    }
}
