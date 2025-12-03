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

package stroom.search.extraction;

import stroom.docref.DocRef;
import stroom.index.shared.LuceneIndexField;
import stroom.query.api.datasource.IndexField;
import stroom.query.common.v2.IndexFieldCache;
import stroom.query.language.functions.FieldIndex;

import jakarta.inject.Inject;

public class FieldValueExtractorFactory {

    private final IndexFieldCache indexFieldCache;

    @Inject
    public FieldValueExtractorFactory(final IndexFieldCache indexFieldCache) {
        this.indexFieldCache = indexFieldCache;
    }

    public FieldValueExtractor create(final DocRef dataSource, final FieldIndex fieldIndex) {
        final IndexField[] indexFields = new IndexField[fieldIndex.size()];

        // Populate the index field map with the expected fields.
        for (final String fieldName : fieldIndex.getFields()) {
            final int pos = fieldIndex.getPos(fieldName);
            IndexField indexField = null;

            if (dataSource != null &&
                    indexFieldCache != null) {
                indexField = indexFieldCache.get(dataSource, fieldName);
            }

            if (indexField == null) {
                indexField = LuceneIndexField
                        .builder()
                        .fldName(fieldName)
                        .indexed(false)
                        .build();
            }

            indexFields[pos] = indexField;
        }

        return new FieldValueExtractor(fieldIndex, indexFields);
    }
}
