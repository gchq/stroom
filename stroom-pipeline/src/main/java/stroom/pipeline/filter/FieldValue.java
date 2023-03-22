package stroom.pipeline.filter;

import stroom.dashboard.expression.v1.Val;
import stroom.index.shared.IndexField;

public record FieldValue(IndexField field, Val value) {

}
