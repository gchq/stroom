package stroom.search.extraction;

import stroom.query.api.datasource.IndexField;
import stroom.query.language.functions.Val;

public record FieldValue(IndexField field, Val value) {

}
