package stroom.query.api.datasource;

import stroom.docref.HasDisplayValue;

public interface Field extends HasDisplayValue, Comparable<Field> {

    String getFldName();

    FieldType getFldType();
}
