package stroom.pipeline.filter;

import stroom.index.shared.IndexField;
import stroom.query.language.functions.Val;

public record FieldValue(IndexField field, Val value) {

}
