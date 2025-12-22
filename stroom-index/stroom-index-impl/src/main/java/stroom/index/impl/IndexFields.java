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

package stroom.index.impl;

import stroom.index.shared.IndexConstants;
import stroom.index.shared.LuceneIndexField;

import java.util.ArrayList;
import java.util.List;

public class IndexFields {

    private IndexFields() {

    }

    public static List<LuceneIndexField> createStreamIndexFields() {
        final List<LuceneIndexField> indexFields = new ArrayList<>();
        // Always add standard id fields for now.
        indexFields.add(LuceneIndexField.createIdField(IndexConstants.STREAM_ID));
        indexFields.add(LuceneIndexField.createIdField(IndexConstants.EVENT_ID));
        return indexFields;
    }
}
