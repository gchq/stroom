package stroom.index.shared;

import stroom.query.api.datasource.FieldType;

import java.util.ArrayList;
import java.util.List;

public class LuceneFieldTypes {

    public static final List<FieldType> FIELD_TYPES = new ArrayList<>();

    static {
        FIELD_TYPES.add(FieldType.ID);
        FIELD_TYPES.add(FieldType.BOOLEAN);
        FIELD_TYPES.add(FieldType.INTEGER);
        FIELD_TYPES.add(FieldType.LONG);
        FIELD_TYPES.add(FieldType.FLOAT);
        FIELD_TYPES.add(FieldType.DOUBLE);
        FIELD_TYPES.add(FieldType.DATE);
        FIELD_TYPES.add(FieldType.TEXT);
    }
}
