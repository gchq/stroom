package stroom.test.common.util.test.data;

import java.util.List;

public class Rec {

    final List<Field> fieldDefinitions;
    final List<String> values;

    public Rec(List<Field> fieldDefinitions, List<String> values) {
        this.fieldDefinitions = fieldDefinitions;
        this.values = values;
    }

    public List<Field> getFieldDefinitions() {
        return fieldDefinitions;
    }

    public List<String> getValues() {
        return values;
    }
}
