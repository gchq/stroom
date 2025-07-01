package stroom.query.common.v2;

import stroom.query.api.Row;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ref.ErrorConsumer;

import java.util.List;
import java.util.function.BiFunction;

public interface AnnotationsPostProcessor {

    List<Row> convert(Val[] values, ErrorConsumer errorConsumer, BiFunction<Long, Val[], Row> mapping);
}
