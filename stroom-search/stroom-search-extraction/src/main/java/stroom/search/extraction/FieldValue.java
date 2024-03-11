package stroom.search.extraction;

import stroom.index.shared.LuceneIndexField;
import stroom.query.language.functions.Val;

public record FieldValue(LuceneIndexField field, Val value) {

}
