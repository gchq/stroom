/*
 * Copyright 2024 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.task.impl;

import stroom.datasource.api.v2.FindFieldCriteria;
import stroom.datasource.api.v2.QueryField;
import stroom.docref.DocRef;
import stroom.entity.shared.ExpressionCriteria;
import stroom.query.common.v2.FieldInfoResultPageBuilder;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValString;
import stroom.query.language.functions.ValuesConsumer;
import stroom.searchable.api.Searchable;
import stroom.util.NullSafe;
import stroom.util.shared.ResultPage;
import stroom.util.shared.string.CIKey;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class SearchableDual implements Searchable {

    private static final DocRef DOC_REF = new DocRef(
            "Searchable",
            "Dual",
            "Dual");

    private static final QueryField DUMMY_FIELD = QueryField.createText(
            "Dummy", true);

    private static final List<QueryField> FIELDS = Collections.singletonList(DUMMY_FIELD);

    private static final ValString DUMMY_VALUE = ValString.create("X");

    @Override
    public DocRef getDocRef() {
        return DOC_REF;
    }

    @Override
    public ResultPage<QueryField> getFieldInfo(final FindFieldCriteria criteria) {
        return FieldInfoResultPageBuilder.builder(criteria).addAll(FIELDS).build();
    }

    @Override
    public Optional<String> fetchDocumentation(final DocRef docRef) {
        return Optional.empty();
    }

    @Override
    public QueryField getTimeField() {
        return null;
    }

    @Override
    public void search(final ExpressionCriteria criteria,
                       final FieldIndex fieldIndex,
                       final ValuesConsumer consumer) {
        final List<CIKey> fields = fieldIndex.getFieldsAsCIKeys();
        final Val[] valArr = NullSafe.stream(fields)
                .map(fieldName -> DUMMY_VALUE)
                .toArray(Val[]::new);
        consumer.accept(Val.of(valArr));
    }
}
