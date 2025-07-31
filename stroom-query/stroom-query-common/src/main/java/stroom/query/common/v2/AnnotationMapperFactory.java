package stroom.query.common.v2;

import stroom.query.language.functions.ref.ValueReferenceIndex;

import java.util.stream.Stream;

public interface AnnotationMapperFactory {

    AnnotationMapperFactory NO_OP = valueReferenceIndex -> Stream::of;

    StoredValueMapper createMapper(ValueReferenceIndex valueReferenceIndex);
}
