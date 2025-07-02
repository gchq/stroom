package stroom.query.common.v2;

import stroom.query.language.functions.Val;

public class AnnotatedItem implements Item {

    private final Key key;
    private final Val[] values;
    private final Long annotationId;

    public AnnotatedItem(final Key key, final Val[] values, final Long annotationId) {
        this.key = key;
        this.values = values;
        this.annotationId = annotationId;
    }

    @Override
    public Key getKey() {
        return key;
    }

    @Override
    public Val getValue(final int index) {
        return values[index];
    }

    @Override
    public int size() {
        return values.length;
    }

    @Override
    public Val[] toArray() {
        return values;
    }

    public Long getAnnotationId() {
        return annotationId;
    }
}
