package stroom.search.extraction;

import stroom.datasource.api.v2.IndexField;
import stroom.query.language.functions.Val;

public record FieldValue(IndexField field, Val value) {

}
