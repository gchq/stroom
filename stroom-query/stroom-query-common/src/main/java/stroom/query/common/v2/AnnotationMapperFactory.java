package stroom.query.common.v2;

import stroom.query.language.functions.FieldIndex;

import java.util.stream.Stream;

public interface AnnotationMapperFactory {

    AnnotationMapperFactory NO_OP = fieldIndex -> Stream::of;

    StoredValueMapper createMapper(FieldIndex fieldIndex);
}
