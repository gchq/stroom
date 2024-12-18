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

package stroom.meta.impl;

import stroom.datasource.api.v2.DataSourceProvider;
import stroom.datasource.api.v2.FindFieldCriteria;
import stroom.datasource.api.v2.QueryField;
import stroom.docref.DocRef;
import stroom.meta.shared.MetaFields;
import stroom.query.common.v2.FieldInfoResultPageBuilder;
import stroom.util.NullSafe;
import stroom.util.shared.ResultPage;

import java.util.List;
import java.util.Optional;

/**
 * This class provides a data source for the `StreamStore` source type as opposed to the generic `Searchable`.
 */
public class MetaDataSourceProvider implements DataSourceProvider {

    @Override
    public List<DocRef> list() {
        return List.of(MetaFields.STREAM_STORE_DOC_REF);
    }

    @Override
    public String getType() {
        return MetaFields.STREAM_STORE_DOC_REF.getType();
    }

    @Override
    public ResultPage<QueryField> getFieldInfo(final FindFieldCriteria criteria) {
        if (!MetaFields.STREAM_STORE_DOC_REF.equals(criteria.getDataSourceRef())) {
            return ResultPage.empty();
        }
        return FieldInfoResultPageBuilder.builder(criteria).addAll(MetaFields.getFields()).build();
    }

    @Override
    public int getFieldCount(final DocRef docRef) {
        return NullSafe.size(MetaFields.getFields());
    }

    @Override
    public Optional<String> fetchDocumentation(final DocRef docRef) {
        return Optional.empty();
    }

    @Override
    public DocRef fetchDefaultExtractionPipeline(final DocRef docRef) {
        return null;
    }
}
