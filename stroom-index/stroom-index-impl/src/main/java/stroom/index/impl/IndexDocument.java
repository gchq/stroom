package stroom.index.impl;

import stroom.search.extraction.FieldValue;

import java.util.ArrayList;
import java.util.List;

public class IndexDocument {

    private final List<FieldValue> values = new ArrayList<>();

    public void add(final stroom.search.extraction.FieldValue value) {
        values.add(value);
    }

    public List<FieldValue> getValues() {
        return values;
    }
}
