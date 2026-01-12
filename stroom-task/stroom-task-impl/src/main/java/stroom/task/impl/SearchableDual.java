/*
 * Copyright 2016-2025 Crown Copyright
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

import stroom.docref.DocRef;
import stroom.entity.shared.ExpressionCriteria;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.datasource.FindFieldCriteria;
import stroom.query.api.datasource.QueryField;
import stroom.query.common.v2.FieldInfoResultPageFactory;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValString;
import stroom.query.language.functions.ValuesConsumer;
import stroom.query.language.functions.ref.ErrorConsumer;
import stroom.searchable.api.Searchable;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;

import java.util.Collections;
import java.util.List;

public class SearchableDual implements Searchable {

    private static final DocRef DOC_REF = new DocRef(
            "Dual",
            "Dual",
            "Dual");

    private static final QueryField DUMMY_FIELD = QueryField.createText(
            "Dummy", true);

    private static final List<QueryField> FIELDS = Collections.singletonList(DUMMY_FIELD);

    private final FieldInfoResultPageFactory fieldInfoResultPageFactory;

    @Inject
    public SearchableDual(final FieldInfoResultPageFactory fieldInfoResultPageFactory) {
        this.fieldInfoResultPageFactory = fieldInfoResultPageFactory;
    }

    @Override
    public String getDataSourceType() {
        return DOC_REF.getType();
    }

    @Override
    public List<DocRef> getDataSourceDocRefs() {
        return Collections.singletonList(DOC_REF);
    }

    @Override
    public ResultPage<QueryField> getFieldInfo(final FindFieldCriteria criteria) {
        if (!DOC_REF.equals(criteria.getDataSourceRef())) {
            return ResultPage.empty();
        }
        return fieldInfoResultPageFactory.create(criteria, FIELDS);
    }

    @Override
    public int getFieldCount(final DocRef docRef) {
        return FIELDS.size();
    }

    @Override
    public void search(final ExpressionCriteria criteria,
                       final FieldIndex fieldIndex,
                       final DateTimeSettings dateTimeSettings,
                       final ValuesConsumer valuesConsumer,
                       final ErrorConsumer errorConsumer) {
        final String[] fields = fieldIndex.getFields();
        final Val[] valArr = new Val[fields.length];
        for (int i = 0; i < fields.length; i++) {
            valArr[i] = ValString.create("X");
        }
        valuesConsumer.accept(Val.of(valArr));
    }
}
