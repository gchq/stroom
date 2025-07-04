package stroom.query.common.v2;

import stroom.query.language.functions.Val;

import java.util.List;

public interface AnnotationColumnValueProvider {

    List<Val> getValues(Item item);
}
