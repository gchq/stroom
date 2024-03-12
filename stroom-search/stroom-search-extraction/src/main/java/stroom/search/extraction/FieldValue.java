package stroom.search.extraction;

import stroom.index.shared.IndexField;
import stroom.query.language.functions.Val;

public record FieldValue(IndexField field, Val value) {

}
