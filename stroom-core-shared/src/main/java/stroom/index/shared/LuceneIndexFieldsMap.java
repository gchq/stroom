/*
 * Copyright 2016 Crown Copyright
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

package stroom.index.shared;

import java.util.HashMap;
import java.util.List;

public class LuceneIndexFieldsMap extends HashMap<String, LuceneIndexField> {

    public LuceneIndexFieldsMap() {
    }

    public LuceneIndexFieldsMap(final List<LuceneIndexField> indexFields) {
        if (indexFields != null) {
            for (final LuceneIndexField indexField : indexFields) {
                put(indexField);
            }
        }
    }

    public LuceneIndexField put(final LuceneIndexField indexField) {
        return put(indexField.getName(), indexField);
    }

    public LuceneIndexField putIfAbsent(final LuceneIndexField indexField) {
        return putIfAbsent(indexField.getName(), indexField);
    }
}
