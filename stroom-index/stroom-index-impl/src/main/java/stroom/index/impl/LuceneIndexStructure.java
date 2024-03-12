/*
 * Copyright 2017 Crown Copyright
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

package stroom.index.impl;

import stroom.index.shared.LuceneIndexDoc;
import stroom.index.shared.LuceneIndexField;
import stroom.index.shared.LuceneIndexFieldsMap;

import java.util.List;

public class LuceneIndexStructure {

    private final LuceneIndexDoc index;
    private final List<LuceneIndexField> indexFields;
    private final LuceneIndexFieldsMap indexFieldsMap;

    public LuceneIndexStructure(final LuceneIndexDoc index,
                                final List<LuceneIndexField> indexFields,
                                final LuceneIndexFieldsMap indexFieldsMap) {
        this.index = index;
        this.indexFields = indexFields;
        this.indexFieldsMap = indexFieldsMap;
    }

    public LuceneIndexDoc getIndex() {
        return index;
    }

    public List<LuceneIndexField> getIndexFields() {
        return indexFields;
    }

    public LuceneIndexFieldsMap getIndexFieldsMap() {
        return indexFieldsMap;
    }
}
