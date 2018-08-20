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

package stroom.index;

import stroom.index.shared.IndexDoc;
import stroom.index.shared.IndexField;
import stroom.index.shared.IndexFieldsMap;

import java.util.List;

public class IndexStructure {
    private final IndexDoc index;
    private final List<IndexField> indexFields;
    private final IndexFieldsMap indexFieldsMap;

    public IndexStructure(final IndexDoc index, final List<IndexField> indexFields, final IndexFieldsMap indexFieldsMap) {
        this.index = index;
        this.indexFields = indexFields;
        this.indexFieldsMap = indexFieldsMap;
    }

    public IndexDoc getIndex() {
        return index;
    }

    public List<IndexField> getIndexFields() {
        return indexFields;
    }

    public IndexFieldsMap getIndexFieldsMap() {
        return indexFieldsMap;
    }
}
