package stroom.datasource.api.v2;

import stroom.docref.HasDisplayValue;

public interface Field extends HasDisplayValue, Comparable<Field> {

    String getName();

    FieldType getType();
}
